// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.*;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.junit.*;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServletModule;
import pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus.server.*;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;
import pl.morgwai.base.utils.concurrent.Awaitable;

import static org.junit.Assert.*;



public class TyrusClusteringTests {



	static final String URL_PREFIX = "ws://localhost:";

	org.eclipse.jetty.client.HttpClient wsHttpClient;
	WebSocketContainer clientWebsocketContainer;

	ServerNode node1;
	ServerNode node2;

	StandaloneWebsocketContainerServletContext appDeployment;



	@Before
	public void setup() {
		final var cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JavaxWebSocketClientContainerProvider.getContainer(wsHttpClient);

		appDeployment  = new StandaloneWebsocketContainerServletContext(ServerNode.PATH);
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



	public void testBroadcast(String... urls)
			throws DeploymentException, IOException, InterruptedException {
		final var broadcastMessage = "broadcast";
		final var welcomesReceived = new CountDownLatch(urls.length);
		final var testMessageSent = new CountDownLatch(1);
		final var messagesReceived = new CountDownLatch(urls.length);
		@SuppressWarnings("unchecked")
		final List<String>[] messages = (List<String>[]) new List<?>[urls.length];
		final ClientEndpoint[] clientEndpoints = new ClientEndpoint[urls.length];
		final Session[] connections =  new Session[urls.length];

		// create clientEndpoints and open connections
		for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
			messages[clientNumber] = new ArrayList<>(2);
			final var localClientNumber = clientNumber;
			clientEndpoints[clientNumber] = new ClientEndpoint(
				(message) -> {
					messages[localClientNumber].add(message);
					if (message.equals(BroadcastEndpoint.WELCOME_MESSAGE)) {
						welcomesReceived.countDown();
					} else {
						messagesReceived.countDown();
					}
				},
				(connection, error) -> {},
				(connection, closeReason) -> {}
			);
			connections[clientNumber] = clientWebsocketContainer.connectToServer(
				clientEndpoints[clientNumber],
				null,
				URI.create(urls[clientNumber])
			);
		}

		if ( !welcomesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
		connections[0].getAsyncRemote().sendText(broadcastMessage);
		testMessageSent.countDown();
		if ( !messagesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
		for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
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

		for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
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
	public void testTwoNodeCluster() throws DeploymentException, IOException, InterruptedException {
		final var clusterCtx1 = new InMemoryClusterContext(1);
		final var clusterCtx2 = new InMemoryClusterContext(2);
		node1 = new ServerNode(-1, clusterCtx1);
		node2 = new ServerNode(-1, clusterCtx2);
		final var port1 = node1.getPort();
		final var port2 = node2.getPort();
		final var url1 = URL_PREFIX + port1 + ServerNode.PATH + BroadcastEndpoint.PATH;
		final var url2 = URL_PREFIX + port2 + ServerNode.PATH + BroadcastEndpoint.PATH;
		testBroadcast(url1, url2, url1);
	}



	@Test
	public void testSingleServer() throws DeploymentException, IOException, InterruptedException {
		node1 = new ServerNode(-1, null);
		final var port = node1.getPort();
		final var url = URL_PREFIX + port + ServerNode.PATH + BroadcastEndpoint.PATH;
		testBroadcast(url, url);
	}



	static Level LOG_LEVEL = Level.WARNING;
	static final Logger log = Logger.getLogger(TyrusClusteringTests.class.getPackageName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					TyrusClusteringTests.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
