// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;



public class WebsocketClusteringTests {

// todo: non-clustered tyrus test

	org.eclipse.jetty.client.HttpClient wsHttpClient;
	WebSocketContainer clientWebsocketContainer;

	TyrusServer node1;
	TyrusServer node2;
	int port1;
	int port2;
	String path1;
	String path2;

	StandaloneWebsocketContainerServletContext appDeployment;



	@Before
	public void setup() throws DeploymentException {
		final var cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JavaxWebSocketClientContainerProvider.getContainer(wsHttpClient);

		final var clusterCtx1 = new InMemoryClusterContext(1);
		node1 = new TyrusServer(-1, clusterCtx1);
		port1 = node1.getPort();
		path1 = "ws://localhost:" + port1 + TyrusServer.PATH + BroadcastEndpoint.PATH;
		final var clusterCtx2 = new InMemoryClusterContext(2);
		node2 = new TyrusServer(-1, clusterCtx2);
		port2 = node2.getPort();
		path2 = "ws://localhost:" + port2 + TyrusServer.PATH + BroadcastEndpoint.PATH;

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
		node2.stop();

		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
	}



	@Test
	public void testBroadcast() throws DeploymentException, IOException {
		final var broadcastMessage = "broadcast";
		final var messages1 = new ArrayList<String>(2);
		final var messages2 = new ArrayList<String>(2);
		final var testMessageSent = new CountDownLatch(1);
		final var messagesReceived = new CountDownLatch(4);
		final var testThread = Thread.currentThread();
		final CloseReason[] closeReasonHolder = {null, null};
		final var clientEndpoint1 = new ClientEndpoint(
			(message) -> {
				messages1.add(message);
				messagesReceived.countDown();
			},
			(connection, error) -> {},
			(connection, closeReason) -> {
				closeReasonHolder[0] = closeReason;
				if (closeReason.getCloseCode().getCode() != CloseCodes.NORMAL_CLOSURE.getCode()) {
					try {
						var ignored = testMessageSent.await(500L, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ignored) {}
					testThread.interrupt();
				}
			}
		);
		final var clientEndpoint2 = new ClientEndpoint(
			(message) -> {
				messages2.add(message);
				messagesReceived.countDown();
			},
			(connection, error) -> {},
			(connection, closeReason) -> {
				closeReasonHolder[1] = closeReason;
				if (closeReason.getCloseCode().getCode() != CloseCodes.NORMAL_CLOSURE.getCode()) {
					try {
						var ignored = testMessageSent.await(500L, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ignored) {}
					testThread.interrupt();
				}
			}
		);
		final var connection1 =
				clientWebsocketContainer.connectToServer(clientEndpoint1, null, URI.create(path1));
		final var connection2 =
				clientWebsocketContainer.connectToServer(clientEndpoint2, null, URI.create(path2));
		connection1.getAsyncRemote().sendText(broadcastMessage);
		testMessageSent.countDown();
		try {
			if ( !messagesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
			connection1.close();
			connection2.close();
			if ( !clientEndpoint1.awaitClosure(2L, TimeUnit.SECONDS)) fail("timeout");
			if ( !clientEndpoint2.awaitClosure(2L, TimeUnit.SECONDS)) fail("timeout");
		} catch (InterruptedException e) {  // interrupted by clientEndpoint.closeHandler above
			fail("abnormal close code: 1: " + closeReasonHolder[0].getCloseCode()
					+ ", 2: " + closeReasonHolder[1].getCloseCode());
		}

		assertEquals("client1 should receive 2 messages", 2, messages1.size());
		assertEquals("the first message should be the welcome message",
				BroadcastEndpoint.WELCOME_MESSAGE, messages1.get(0));
		assertEquals("the second message should be the echo",
				broadcastMessage, messages1.get(1));

		assertEquals("client2 should receive 2 messages", 2, messages2.size());
		assertEquals("the first message should be the welcome message",
				BroadcastEndpoint.WELCOME_MESSAGE, messages2.get(0));
		assertEquals("the second message should be the broadcast",
				broadcastMessage, messages2.get(1));
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
