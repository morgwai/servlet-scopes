// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.net.CookieManager;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.junit.*;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.tests.server.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.server.ServletContextListener
		.WEBSOCKET_PATH;
import static pl.morgwai.base.servlet.guice.scopes.tests.server.TestServer.APP_PATH;



public abstract class WebsocketIntegrationTests {



	protected CookieManager cookieManager;
	protected org.eclipse.jetty.client.HttpClient wsHttpClient;
	protected WebSocketContainer clientWebsocketContainer;
	protected Server server;
	protected int port;
	protected String serverWebsocketUrl;
	protected String appWebsocketUrl;



	/**
	 * Creates {@link #server}, {@link #clientWebsocketContainer} and calculates helper URL prefixes
	 * ({@link #serverWebsocketUrl}, {@link #appWebsocketUrl}).
	 */
	@Before
	public void setup() throws Exception {
		cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JavaxWebSocketClientContainerProvider.getContainer(wsHttpClient);
		server = createServer();
		port = server.getPort();
		serverWebsocketUrl = "ws://localhost:" + port;
		appWebsocketUrl = serverWebsocketUrl + APP_PATH + WEBSOCKET_PATH;
	}



	protected abstract Server createServer() throws Exception;



	/** Shutdowns {@link #clientWebsocketContainer} and {@link #server}. */
	@After
	public void shutdown() throws Exception {
		final var jettyWsContainer = ((JavaxWebSocketContainer) clientWebsocketContainer);
		jettyWsContainer.stop();
		jettyWsContainer.destroy();
		wsHttpClient.stop();
		wsHttpClient.destroy();
		server.stopz();
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
	protected List<String[]> testSingleSessionWithServerEndpoint(URI url, boolean sendTestMessage)
			throws Exception {
		final var testMessage = "test message for " + url;
		final var replies = new ArrayList<String[]>(4);
		final var testMessageSent = new CountDownLatch(1);
		final var repliesReceived = new CountDownLatch(2);
		final var testThread = Thread.currentThread();
		final CloseReason[] closeReasonHolder = {null};
		final var clientEndpoint = new ClientEndpoint(
			(reply) -> {
				if (replies.size() >= 2 && !sendTestMessage) return;  // extra pong
				replies.add(reply.split("\n"));
				repliesReceived.countDown();
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
			if ( !repliesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
			connection.close();
			if ( !clientEndpoint.awaitClosure(2L, TimeUnit.SECONDS)) fail("timeout");
		} catch (InterruptedException e) {  // interrupted by clientEndpoint.closeHandler above
			fail("abnormal close code: " + closeReasonHolder[0].getCloseCode());
		}
		assertEquals("reply should have 5 lines", 5, replies.get(0).length);
		assertEquals("reply should have 5 lines", 5, replies.get(1).length);
		assertEquals("onOpen reply should be a welcome",
				EchoEndpoint.WELCOME_MESSAGE, replies.get(0)[0]);
		if (sendTestMessage) {
			assertEquals("2nd reply should be an echo",
					testMessage, replies.get(1)[0]);
		}
		assertEquals("session scoped object hash should remain the same",
				replies.get(0)[3], replies.get(1)[3]);
		assertEquals("connection scoped object hash should remain the same",
				replies.get(0)[4], replies.get(1)[4]);
		assertNotEquals("event scoped object hash should change",
				replies.get(0)[2], replies.get(1)[2]);
		return replies;
	}

	/**
	 * Returns a list containing 4 messages received from the server via 2 calls to
	 * {@link #testSingleSessionWithServerEndpoint(URI, boolean)} made via separate websocket
	 * connections. All 4 messages are expected to be sent from the same HTTP session scope.
	 */
	protected List<String[]> test2SessionsWithServerEndpoint(String url, boolean sendTestMessage)
			throws Exception {
		final var uri = URI.create(url);
		final var replies = testSingleSessionWithServerEndpoint(uri, sendTestMessage);
		replies.addAll(testSingleSessionWithServerEndpoint(uri, sendTestMessage));
		assertEquals("session scoped object hash should remain the same",
				replies.get(0)[3], replies.get(2)[3]);
		assertNotEquals("connection scoped object hash should change",
				replies.get(0)[4], replies.get(2)[4]);
		return replies;
	}

	@Test
	public void testProgrammaticEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(
				serverWebsocketUrl + APP_PATH + ProgrammaticEndpoint.PATH, true);
	}

	@Test
	public void testExtendingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(
				serverWebsocketUrl + APP_PATH + ExtendingEndpoint.PATH, true);
	}

	@Test
	public void testAnnotatedEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(
				serverWebsocketUrl + APP_PATH + AnnotatedEndpoint.PATH, true);
	}

	@Test
	public void testRttReportingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(
				serverWebsocketUrl + APP_PATH + RttReportingEndpoint.PATH, false);
	}



	/**
	 * Tries to open a websocket connection to a given {@code Endpoint} {@code type}. If succeeds,
	 * closes it immediately: intended for tests of invalid {@code Endpoint} classes that should
	 * fail to instantiate.
	 */
	void testOpenConnectionToServerEndpoint(String type) throws Exception {
		final var url = URI.create(appWebsocketUrl + type);
		final var endpoint = new ClientEndpoint(
			(message) -> {},
			(connection, error) -> {
				log.log(Level.WARNING, "error on connection " + connection.getId(), error);
			},
			(connection, closeReason) -> {}
		);
		final var connection = clientWebsocketContainer.connectToServer(endpoint, null, url);
		connection.close();
	}

	@Test
	public void testOnOpenWithoutSessionParamEndpoint() {
		final var logger = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
		final var originalLevel = logger.getLevel();
		logger.setLevel(Level.OFF);
		try {
			testOpenConnectionToServerEndpoint(OnOpenWithoutSessionParamEndpoint.TYPE);
			fail("instantiation of OnOpenWithoutSessionParamEndpoint should throw an Exception");
		} catch (Exception expected) {
		} finally {
			logger.setLevel(originalLevel);
		}
	}

	@Test
	public void testPingingWithoutOnCloseEndpoint() {
		final var logger = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
		final var originalLevel = logger.getLevel();
		logger.setLevel(Level.OFF);
		try {
			testOpenConnectionToServerEndpoint(PingingWithoutOnCloseEndpoint.TYPE);
			fail("instantiation of PingingWithoutOnCloseEndpoint should throw an Exception");
		} catch (Exception expected) {
		} finally {
			logger.setLevel(originalLevel);
		}
	}



/*
	public void testAppSeparation(String testAppWebsocketUrl, String secondAppWebsocketUrl)
			throws InterruptedException, DeploymentException, IOException {
		final var messages = new ArrayList<String>(2);

		final var endpoint = new ClientEndpoint(messages::add, null, null);
		final var uri1 = URI.create(testAppWebsocketUrl);
		try (
			final var ignored = clientWebsocketContainer.connectToServer(endpoint, null, uri1);
		) {
			if ( !endpoint.awaitClosure(500L, TimeUnit.MILLISECONDS)) fail("timeout");
		}

		final var endpoint2 = new ClientEndpoint(messages::add, null, null);
		final var uri2 = URI.create(secondAppWebsocketUrl);
		try (
			final var ignored = clientWebsocketContainer.connectToServer(endpoint2, null, uri2);
		) {
			if ( !endpoint2.awaitClosure(500L, TimeUnit.MILLISECONDS)) fail("timeout");
		}

		assertNotEquals("Endpoint Configurators of separate apps should have separate Injectors",
				messages.get(0), messages.get(1));
	}

	@Test
	public void testAppSeparation() throws InterruptedException, DeploymentException, IOException {
		testAppSeparation(
			appWebsocketUrl + AppSeparationTestEndpoint.TYPE,
			serverWebsocketUrl + TestServer.SECOND_APP_PATH
					+ WEBSOCKET_PATH + AppSeparationTestEndpoint.TYPE
		);
	}

	@Test
	public void testAppSeparationNoSession()
			throws InterruptedException, DeploymentException, IOException {
		testAppSeparation(
			serverWebsocketUrl + APP_PATH + NoSessionAppSeparationTestEndpoint.PATH,
			serverWebsocketUrl + TestServer.SECOND_APP_PATH
					+ NoSessionAppSeparationTestEndpoint.PATH
		);
	}
*/



	@BeforeClass
	public static void setupProperties() {
		System.setProperty(ServletContextListener.PING_INTERVAL_MILLIS_PROPERTY, "5");
	}



	/**
	 * Change the below value if you need logging:<br/>
	 * <code>INFO</code> will log server startup and shutdown diagnostics<br/>
	 * <code>FINE</code> will log every response/message received from the server.
	 */
	static Level LOG_LEVEL = Level.WARNING;

	static final Logger log = Logger.getLogger(WebsocketIntegrationTests.class.getPackageName());
	static final Logger scopesLog =
			Logger.getLogger(GuiceServerEndpointConfigurator.class.getPackageName());
	static final Logger pingerLog = Logger.getLogger(WebsocketPingerService.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					WebsocketIntegrationTests.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		scopesLog.setLevel(LOG_LEVEL);
		pingerLog.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
