// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.*;

import jakarta.websocket.*;
import jakarta.websocket.CloseReason.CloseCodes;

import org.eclipse.jetty.websocket.jakarta.client.JakartaWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.jakarta.common.JakartaWebSocketContainer;
import org.junit.*;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;
import pl.morgwai.base.utils.concurrent.Awaitable;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;

import static org.junit.Assert.*;
import static pl.morgwai.base.jul.JulConfigurator.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.EchoEndpoint.MESSAGE_PROPERTY;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.EchoEndpoint.WELCOME_MESSAGE;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server.WEBSOCKET_PATH;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Service.*;



public abstract class WebsocketIntegrationTests {



	protected Server server;
	protected String appWebsocketUrl;

	protected CookieManager cookieManager;
	protected org.eclipse.jetty.client.HttpClient wsHttpClient;
	protected WebSocketContainer clientWebsocketContainer;



	protected abstract Server createServer() throws Exception;
	protected abstract boolean isHttpSessionAvailable();



	@Before
	public void setupServer() throws Exception {
		server = createServer();
		appWebsocketUrl = server.getAppWebsocketUrl();

		cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JakartaWebSocketClientContainerProvider.getContainer(wsHttpClient);
	}



	@After
	public void stopServer() throws Exception {
		final var jettyWsContainer = ((JakartaWebSocketContainer) clientWebsocketContainer);
		jettyWsContainer.stop();
		jettyWsContainer.destroy();
		wsHttpClient.stop();
		wsHttpClient.destroy();

		server.shutdown();
	}



	@BeforeClass
	public static void setupProperties() {
		System.setProperty(Server.PING_INTERVAL_MILLIS_PROPERTY, "50");
	}



	/**
	 * Connects to a server endpoint at {@code url} and depending on {@code sendTestMessage} sends 1
	 * test message. Returns a list containing 2 messages received from the server: the initial
	 * welcome message and either the reply to the test message that was sent or an
	 * {@link RttReportingEndpoint RTT report}.
	 * <p>
	 * Each message is split into lines. Each message is expected to have the format defined in
	 * {@link EchoEndpoint}. Both messages are expected to be sent from the same HTTP session scope
	 * and the same websocket connection scope.</p>
	 */
	protected List<Properties> testSingleSessionWithServerEndpoint(URI url, boolean sendTestMessage)
			throws Exception {
		final var testMessage = "test message for " + url;
		final var replies = new ArrayList<Properties>(4);
		final var testMessageSent = new CountDownLatch(1);
		final var repliesReceived = new CountDownLatch(2);
		final var testThread = Thread.currentThread();
		final CloseReason[] closeReasonHolder = {null};
		final var clientEndpoint = new ClientEndpoint(
			(reply) -> {
				if (replies.size() >= 2 && !sendTestMessage) return;  // extra pong
				final var parsedReply = new Properties(5);
				try {
					parsedReply.load(new StringReader(reply));
					replies.add(parsedReply);
				} catch (IOException e) {
					throw new RuntimeException(e);
				} finally {
					repliesReceived.countDown();
				}
			},
			(connection, error) -> {},
			(connection, closeReason) -> {
				closeReasonHolder[0] = closeReason;
				if (closeReason.getCloseCode().getCode() != CloseCodes.NORMAL_CLOSURE.getCode()) {
					// server endpoint error: interrupt testThread awaiting for replies (they will
					// probably never arrive), but not before testMessage is sent as
					// getAsyncRemote.sendText(...) may clear interruption status.
					try {
						var ignored = testMessageSent.await(500L, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ignored) {}
					testThread.interrupt();
				}
			}
		);
		final var connection = clientWebsocketContainer.connectToServer(clientEndpoint, null, url);
		if (sendTestMessage) connection.getAsyncRemote().sendText(testMessage);
		testMessageSent.countDown();
		try {
			assertTrue("relies should be received",
					repliesReceived.await(2L, SECONDS));
			connection.close();
			assertTrue ("client endpoint should be closed",
					clientEndpoint.awaitClosure(2L, SECONDS));
		} catch (InterruptedException e) {  // interrupted by clientEndpoint.closeHandler above
			fail("abnormal close code: " + closeReasonHolder[0].getCloseCode());
		}
		assertEquals("onOpen reply should be a welcome",
				WELCOME_MESSAGE, replies.get(0).getProperty(MESSAGE_PROPERTY));
		if (sendTestMessage) {
			assertEquals("2nd reply should be an echo",
					testMessage, replies.get(1).getProperty(MESSAGE_PROPERTY));
		}
		if (isHttpSessionAvailable()) {
			assertEquals(
				"HttpSession scoped object hash should remain the same",
				replies.get(0).getProperty(HTTP_SESSION),
				replies.get(1).getProperty(HTTP_SESSION)
			);
		}
		assertEquals(
			"connection scoped object hash should remain the same",
			replies.get(0).getProperty(WEBSOCKET_CONNECTION),
			replies.get(1).getProperty(WEBSOCKET_CONNECTION)
		);
		assertNotEquals(
			"event scoped object hash should change",
			replies.get(0).getProperty(CONTAINER_CALL),
			replies.get(1).getProperty(CONTAINER_CALL)
		);
		return replies;
	}



	/**
	 * Returns a list containing 4 messages received from the server via 2 calls to
	 * {@link #testSingleSessionWithServerEndpoint(URI, boolean)} made via separate websocket
	 * connections. All 4 messages are expected to be sent from the same HTTP session scope.
	 */
	protected List<Properties> test2SessionsWithServerEndpoint(String url, boolean sendTestMessage)
			throws Exception {
		final var uri = URI.create(url);
		final var replies = testSingleSessionWithServerEndpoint(uri, sendTestMessage);
		replies.addAll(testSingleSessionWithServerEndpoint(uri, sendTestMessage));
		assertNotEquals(
			"connection scoped object hash should change",
			replies.get(0).getProperty(WEBSOCKET_CONNECTION),
			replies.get(2).getProperty(WEBSOCKET_CONNECTION)
		);
		if (isHttpSessionAvailable()) {
			assertEquals(
				"session scoped object hash should remain the same",
				replies.get(0).getProperty(HTTP_SESSION),
				replies.get(2).getProperty(HTTP_SESSION)
			);
		}
		return replies;
	}



	@Test
	public void testProgrammaticEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(appWebsocketUrl + ProgrammaticEndpoint.PATH, true);
	}

	@Test
	public void testAnnotatedEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(appWebsocketUrl + AnnotatedEndpoint.PATH, true);
	}

	@Test
	public void testRttReportingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(appWebsocketUrl + RttReportingEndpoint.PATH, false);
	}

	/** Not all servers support it. */
	@Test
	public void testAnnotatedExtendingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(appWebsocketUrl + AnnotatedExtendingEndpoint.PATH, true);
	}



	/**
	 * Tries to open a websocket connection to a given {@code Endpoint} {@code type}. If succeeds,
	 * closes it immediately: intended for tests of invalid {@code Endpoint} classes that should
	 * fail to instantiate.
	 */
	protected Session testOpenConnectionToServerEndpoint(String type) throws Exception {
		final var url = URI.create(appWebsocketUrl + WEBSOCKET_PATH + type);
		final var endpoint = new ClientEndpoint(
			(message) -> {},
			(connection, error) -> {},
			(connection, closeReason) -> {}
		);
		return clientWebsocketContainer.connectToServer(endpoint, null, url);
	}



	@Test
	public void testOnOpenWithoutSessionParamEndpoint() {
		final var logger = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
		final var originalLevel = logger.getLevel();
		logger.setLevel(Level.OFF);
		Session connection = null;
		try {
			connection = testOpenConnectionToServerEndpoint(OnOpenWithoutSessionParamEndpoint.TYPE);
			fail("instantiation of OnOpenWithoutSessionParamEndpoint should throw an Exception");
		} catch (Exception expected) {
		} finally {
			logger.setLevel(originalLevel);
			try {
				if (connection != null && connection.isOpen()) connection.close();
			} catch (IOException ignored) {}
		}
	}



	@Test
	public void testPingingWithoutOnCloseEndpoint() {
		final var logger = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
		final var originalLevel = logger.getLevel();
		logger.setLevel(Level.OFF);
		Session connection = null;
		try {
			connection = testOpenConnectionToServerEndpoint(PingingWithoutOnCloseEndpoint.TYPE);
			fail("instantiation of PingingWithoutOnCloseEndpoint should throw an Exception");
		} catch (Exception expected) {
		} finally {
			logger.setLevel(originalLevel);
			try {
				if (connection != null && connection.isOpen()) connection.close();
			} catch (IOException ignored) {}
		}
	}



	protected void testBroadcast(String... urls)
		throws DeploymentException, IOException, InterruptedException {
		final var broadcastMessage = "broadcast";
		final var welcomesReceived = new CountDownLatch(urls.length);
		final var broadcastSent = new CountDownLatch(1);
		final var broadcastsReceived = new CountDownLatch(urls.length);
		@SuppressWarnings("unchecked")
		final List<String>[] messages = (List<String>[]) new List<?>[urls.length];
		final ClientEndpoint[] clientEndpoints = new ClientEndpoint[urls.length];
		final Session[] connections =  new Session[urls.length];

		try {
			for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
				// create clientEndpoints and open connections
				messages[clientNumber] = new ArrayList<>(2);
				final var localClientNumber = clientNumber;
				clientEndpoints[clientNumber] = new ClientEndpoint(
					(message) -> {
						messages[localClientNumber].add(message);
						if (message.equals(BroadcastEndpoint.WELCOME_MESSAGE)) {
							welcomesReceived.countDown();
						} else {
							broadcastsReceived.countDown();
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

			assertTrue("welcome messages should be received by all clients",
					welcomesReceived.await(2L, SECONDS));
			connections[0].getAsyncRemote().sendText(broadcastMessage);
			broadcastSent.countDown();
			assertTrue("broadcast messages should be received by all clients",
					broadcastsReceived.await(2L, SECONDS));
		} finally {
			for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
				if (connections[clientNumber] != null && connections[clientNumber].isOpen()) {
					try {
						connections[clientNumber].close();
					} catch (IOException ignored) {}
				}
			}
		}

		assertTrue(
			"all client endpoints should be closed",
			Awaitable.awaitMultiple(
				2L, SECONDS,
				Arrays.stream(clientEndpoints)
					.map(Awaitable.entryMapper(ClientEndpoint::toAwaitableOfClosure))
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
	public void testBroadcast() throws DeploymentException, IOException, InterruptedException {
		final var url = appWebsocketUrl + BroadcastEndpoint.PATH;
		testBroadcast(url, url);
	}



	protected static final Logger log = Logger.getLogger(WebsocketIntegrationTests.class.getName());



	@BeforeClass
	public static void setupLogging() {
		addOrReplaceLoggingConfigProperties(Map.of(
			LEVEL_SUFFIX, WARNING.toString(),
			ConsoleHandler.class.getName() + LEVEL_SUFFIX, FINEST.toString()
		));
		overrideLogLevelsWithSystemProperties("pl.morgwai");
	}
}
