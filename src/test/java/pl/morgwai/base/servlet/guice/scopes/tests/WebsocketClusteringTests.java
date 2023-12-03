// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.junit.*;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServletModule;
import pl.morgwai.base.servlet.guice.scopes.tests.tyrusserver.*;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;
import pl.morgwai.base.utils.concurrent.Awaitable;

import static org.junit.Assert.*;



public class WebsocketClusteringTests {



	static final String URL_PREFIX = "ws://localhost:";
	static final int NORMAL_CLOSURE = CloseCodes.NORMAL_CLOSURE.getCode();

	org.eclipse.jetty.client.HttpClient wsHttpClient;
	WebSocketContainer clientWebsocketContainer;

	TyrusServer node1;
	TyrusServer node2;
	int port1;
	int port2;
	String path1;
	String path2;
	final InMemoryClusterContext clusterCtx1 = new InMemoryClusterContext(1);
	final InMemoryClusterContext clusterCtx2 = new InMemoryClusterContext(2);

	StandaloneWebsocketContainerServletContext appDeployment;



	@Before
	public void setup() {
		final var cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JavaxWebSocketClientContainerProvider.getContainer(wsHttpClient);

		appDeployment  = new StandaloneWebsocketContainerServletContext(TyrusServer.PATH);
		final var servletModule = new ServletModule(appDeployment);
		final var injector = Guice.createInjector(servletModule);
		appDeployment.setAttribute(Injector.class.getName(), injector);
		GuiceServerEndpointConfigurator.registerDeployment(appDeployment);
	}



	@After
	public void shutdown() throws Exception {
		final var jettyWsContainer = ((JavaxWebSocketContainer) clientWebsocketContainer);
		jettyWsContainer.stop();
		jettyWsContainer.destroy();
		wsHttpClient.stop();
		wsHttpClient.destroy();

		node1.stop();
		if (node2 != null) node2.stop();

		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
	}



	public void testBroadcast(String... paths)
			throws DeploymentException, IOException {
		final var broadcastMessage = "broadcast";
		final var testMessageSent = new CountDownLatch(1);
		final var messagesReceived = new CountDownLatch(2 * paths.length);
		final var testThread = Thread.currentThread();
		@SuppressWarnings("unchecked")
		final List<String>[] messages = (List<String>[]) new List<?>[paths.length];
		final CloseReason[] closeReasons = new CloseReason[paths.length];
		final ClientEndpoint[] clientEndpoints = new ClientEndpoint[paths.length];
		final Session[] connections =  new Session[paths.length];

		// create clientEndpoints and open connections
		for (int clientNumber = 0; clientNumber < paths.length; clientNumber++) {
			messages[clientNumber] = new ArrayList<>(2);
			final var localClientNumber = clientNumber;
			clientEndpoints[clientNumber] = new ClientEndpoint(
				(message) -> {
					messages[localClientNumber].add(message);
					messagesReceived.countDown();
				},
				(connection, error) -> {},
				(connection, closeReason) -> {
					closeReasons[localClientNumber] = closeReason;
					if (closeReason.getCloseCode().getCode() != NORMAL_CLOSURE) {
						try {
							var ignored = testMessageSent.await(500L, TimeUnit.MILLISECONDS);
						} catch (InterruptedException ignored) {}
						testThread.interrupt();
					}
				}
			);
			connections[clientNumber] = clientWebsocketContainer.connectToServer(
				clientEndpoints[clientNumber],
				null,
				URI.create(paths[clientNumber])
			);
		}

		// send the broadcast
		connections[0].getAsyncRemote().sendText(broadcastMessage);
		testMessageSent.countDown();

		// make sure 2*connections.length of messages was received and close connections
		try {
			if ( !messagesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
			for (int clientNumber = 0; clientNumber < paths.length; clientNumber++) {
				connections[clientNumber].close();
			}
			assertTrue(
				"timeout",
				Awaitable.awaitMultiple(
					2L, TimeUnit.SECONDS,
					ClientEndpoint::toAwaitableOfClosure,
					Arrays.asList(clientEndpoints)
				).isEmpty()
			);
		} catch (InterruptedException e) {  // interrupted by clientEndpoint.closeHandler above
			final var messageBuilder = new StringBuilder("abnormal close codes:");
			IntStream.range(0, closeReasons.length)
				.filter((i) -> closeReasons[i] != null)
				.filter((i) -> closeReasons[i].getCloseCode().getCode() != NORMAL_CLOSURE)
				.forEach((i) -> messageBuilder
					.append(' ')
					.append(i + 1)
					.append(" -> ")
					.append(closeReasons[i].getCloseCode())
					.append('(')
					.append(closeReasons[i].getCloseCode().getCode())
					.append(");")
				);
			fail(messageBuilder.toString());
		}

		for (int clientNumber = 0; clientNumber < paths.length; clientNumber++) {
			assertEquals("client " + (clientNumber + 1) + " should receive 2 messages",
					2, messages[clientNumber].size());
			assertEquals(
				"the 1st message of client " + (clientNumber + 1) + " should be the welcome",
				BroadcastEndpoint.WELCOME_MESSAGE,
				messages[clientNumber].get(0)
			);
			assertEquals(
				"the 2nd message of client " + (clientNumber + 1) + " should be the broadcast",
				broadcastMessage,
				messages[clientNumber].get(1)
			);
		}
	}



	@Test
	public void testTwoNodeCluster() throws DeploymentException, IOException {
		node1 = new TyrusServer(-1, clusterCtx1);
		port1 = node1.getPort();
		path1 = URL_PREFIX + port1 + TyrusServer.PATH + BroadcastEndpoint.PATH;
		node2 = new TyrusServer(-1, clusterCtx2);
		port2 = node2.getPort();
		path2 = URL_PREFIX + port2 + TyrusServer.PATH + BroadcastEndpoint.PATH;
		testBroadcast(path1, path2, path1);
	}



	@Test
	public void testSingleServer() throws DeploymentException, IOException {
		node1 = new TyrusServer(-1, null);
		port1 = node1.getPort();
		path1 = URL_PREFIX + port1 + TyrusServer.PATH + BroadcastEndpoint.PATH;
		testBroadcast(path1, path1);
	}



	static Level LOG_LEVEL = Level.WARNING;
	static final Logger log = Logger.getLogger(WebsocketClusteringTests.class.getPackageName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					WebsocketClusteringTests.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
