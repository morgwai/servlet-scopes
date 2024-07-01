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
import org.junit.*;

import com.google.inject.*;
import org.eclipse.jetty.websocket.jakarta.client.JakartaWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.jakarta.common.JakartaWebSocketContainer;
import pl.morgwai.base.servlet.guice.scopes.*;
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

	protected final ServletModule clientServletModule = new ServletModule();
	protected final Injector clientInjector = Guice.createInjector(clientServletModule);
	protected final CookieManager cookieManager = new CookieManager();
	protected final org.eclipse.jetty.client.HttpClient wsHttpClient =
			new org.eclipse.jetty.client.HttpClient();
	protected WebSocketContainer clientWebsocketContainer;



	protected abstract Server createServer() throws Exception;
	protected abstract boolean isHttpSessionAvailable();



	@Before
	public void setup() throws Exception {
		server = createServer();
		appWebsocketUrl = server.getTestAppWebsocketUrl();

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

		server.stop();
	}



	@BeforeClass
	public static void setupProperties() {
		System.setProperty(Server.PING_INTERVAL_MILLIS_PROPERTY, "50");
	}



	public static class GuiceClientEndpoint extends AbstractClientEndpoint {

		@Inject Provider<ContainerCallContext> clientEventCtxProvider;
		@Inject Provider<WebsocketConnectionContext> clientConnectionCtxProvider;
		final List<ContainerCallContext> clientEventCtxs = new ArrayList<>(4);
		final List<WebsocketConnectionContext> clientConnectionCtxs = new ArrayList<>(4);

		final List<Properties> serverReplies = new ArrayList<>(2);
		final CountDownLatch allRepliesReceived = new CountDownLatch(2);

		final CountDownLatch testMessageSent = new CountDownLatch(1);
		final Thread testThread = Thread.currentThread();



		@Override
		public void onOpen(Session connection, EndpointConfig config) {
			super.onOpen(connection, config);
			clientEventCtxs.add(clientEventCtxProvider.get());
			clientConnectionCtxs.add(clientConnectionCtxProvider.get());
			connection.addMessageHandler(String.class, this::onMessage);
		}



		void onMessage(String reply) {
			if (serverReplies.size() >= 2) return;  // extra pong in pinging Endpoint tests
			clientEventCtxs.add(clientEventCtxProvider.get());
			clientConnectionCtxs.add(clientConnectionCtxProvider.get());
			final var parsedReply = new Properties(5);
			try {
				parsedReply.load(new StringReader(reply));
				serverReplies.add(parsedReply);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				allRepliesReceived.countDown();
			}
		}



		@Override
		public void onClose(Session session, CloseReason closeReason) {
			clientEventCtxs.add(clientEventCtxProvider.get());
			clientConnectionCtxs.add(clientConnectionCtxProvider.get());
			super.onClose(session, closeReason);
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
	}



	/**
	 * Connects to a server {@code Endpoint} at {@code url} and depending on {@code sendTestMessage}
	 * sends 1 test message.
	 * Afterwards verifies that:<ul>
	 *   <li>the server side {@link Service#CONTAINER_CALL event-scoped} object has changed</li>
	 *   <li>the server side {@link Service#WEBSOCKET_CONNECTION connection-scoped} object has
	 *       remained the same</li>
	 *   <li>the server side {@link Service#HTTP_SESSION HTTPSession-scoped} object has
	 *       remained the same</li>
	 *   <li>the client side {@link ContainerCallContext} object has changed</li>
	 *   <li>the client side {@link WebsocketConnectionContext} object has remained the same</li>
	 * </ul>
	 * @return the {@link GuiceClientEndpoint} that was used to make the connection.
	 */
	protected GuiceClientEndpoint testSingleSessionWithServerEndpoint(
		URI url,
		boolean sendTestMessage
	) throws Exception {
		final var testMessage = "test message for " + url;
		final var clientEndpoint = clientInjector.getInstance(GuiceClientEndpoint.class);
		final var connection = clientWebsocketContainer.connectToServer(
			new ClientEndpointProxy(
				clientEndpoint,
				clientServletModule.containerCallContextTracker
			),
			null,
			url
		);
		if (sendTestMessage) connection.getAsyncRemote().sendText(testMessage);
		clientEndpoint.testMessageSent.countDown();

		// server replies verifications
		try {
			assertTrue("replies should be received",
					clientEndpoint.allRepliesReceived.await(2L, SECONDS));
			connection.close();
			assertTrue ("client endpoint should be closed",
					clientEndpoint.awaitClosure(2L, SECONDS));
		} catch (InterruptedException e) {  // interrupted by clientEndpoint.closeHandler above
			fail("abnormal close code: " + clientEndpoint.getCloseReason().getCloseCode());
		}
		assertEquals("onOpen reply should be a welcome",
				WELCOME_MESSAGE, clientEndpoint.serverReplies.get(0).getProperty(MESSAGE_PROPERTY));
		if (sendTestMessage) {
			assertEquals("2nd reply should be an echo",
					testMessage, clientEndpoint.serverReplies.get(1).getProperty(MESSAGE_PROPERTY));
		}
		if (isHttpSessionAvailable()) {
			assertEquals(
				"server HttpSession scoped object hash should remain the same",
				clientEndpoint.serverReplies.get(0).getProperty(HTTP_SESSION),
				clientEndpoint.serverReplies.get(1).getProperty(HTTP_SESSION)
			);
		}
		assertEquals(
			"server connection scoped object hash should remain the same",
			clientEndpoint.serverReplies.get(0).getProperty(WEBSOCKET_CONNECTION),
			clientEndpoint.serverReplies.get(1).getProperty(WEBSOCKET_CONNECTION)
		);
		assertNotEquals(
			"server event scoped object hash should change",
			clientEndpoint.serverReplies.get(0).getProperty(CONTAINER_CALL),
			clientEndpoint.serverReplies.get(1).getProperty(CONTAINER_CALL)
		);

		// client ctxs verifications
		final var clientEventCtxSet = new HashSet<>(clientEndpoint.clientEventCtxs);
		assertEquals("clientEventCtx should be different each time",
				clientEndpoint.clientEventCtxs.size(), clientEventCtxSet.size());
		assertTrue("no clientEventCtx should be null",
				clientEventCtxSet.stream()
					.map(Objects::nonNull)
					.reduce(Boolean::logicalAnd)
					.orElseThrow()
		);
		assertNotNull(clientEndpoint.clientConnectionCtxs.get(0));
		assertEquals("clientConnectionCtx should remain the same",
				1, new HashSet<>(clientEndpoint.clientConnectionCtxs).size());

		return clientEndpoint;
	}



	/**
	 * Makes 2 connections to a server {@code Endpoint} at {@code url} using
	 * {@link #testSingleSessionWithServerEndpoint(URI, boolean)}.
	 * Afterwards verifies that:<ul>
	 *   <li>the server side {@link Service#WEBSOCKET_CONNECTION connection-scoped} object has
	 *       changed</li>
	 *   <li>the server side {@link Service#HTTP_SESSION HTTPSession-scoped} object has
	 *       remained the same</li>
	 *   <li>the client side {@link WebsocketConnectionContext} object has changed</li>
	 * </ul>
	 * @return a {@code List} containing results of both
	 *     {@link #testSingleSessionWithServerEndpoint(URI, boolean)} calls.
	 */
	protected List<GuiceClientEndpoint> test2SessionsWithServerEndpoint(
		String url,
		boolean sendTestMessage
	) throws Exception {
		final var uri = URI.create(url);
		final var firstEndpoint = testSingleSessionWithServerEndpoint(uri, sendTestMessage);
		final var secondEndpoint = testSingleSessionWithServerEndpoint(uri, sendTestMessage);
		assertNotEquals(
			"server connection scoped object hash should change",
			firstEndpoint.serverReplies.get(0).getProperty(WEBSOCKET_CONNECTION),
			secondEndpoint.serverReplies.get(0).getProperty(WEBSOCKET_CONNECTION)
		);
		if (isHttpSessionAvailable()) {
			assertEquals(
				"server session scoped object hash should remain the same",
				firstEndpoint.serverReplies.get(0).getProperty(HTTP_SESSION),
				secondEndpoint.serverReplies.get(0).getProperty(HTTP_SESSION)
			);
		}
		assertNotEquals(
			"clientConnectionCtx should change",
			firstEndpoint.clientConnectionCtxs.get(0),
			secondEndpoint.clientConnectionCtxs.get(0)
		);
		return List.of(firstEndpoint, secondEndpoint);
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
	public void testAnnotatedExtendingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(appWebsocketUrl + AnnotatedExtendingEndpoint.PATH, true);
	}

	@Test
	public void testAnnotatedMethodOverridingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(
				appWebsocketUrl + AnnotatedMethodOverridingEndpoint.PATH, true);
	}

	@Test
	public void testRttReportingEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(appWebsocketUrl + RttReportingEndpoint.PATH, false);
	}

	/** Not all servers support it. */
	@Test
	public void testAnnotatedExtendingProgrammaticEndpoint() throws Exception {
		test2SessionsWithServerEndpoint(
				appWebsocketUrl + AnnotatedExtendingProgrammaticEndpoint.PATH, true);
	}



	/**
	 * Tries to open a websocket connection to a given {@code Endpoint} {@code type}. If succeeds,
	 * closes it immediately: intended for tests of invalid {@code Endpoint} classes that should
	 * fail to instantiate.
	 */
	protected Session testOpenConnectionToServerEndpoint(String type) throws Exception {
		final var url = URI.create(appWebsocketUrl + WEBSOCKET_PATH + type);
		final var clientEndpoint = new ClientEndpoint(
			(message) -> {},
			(connection, error) -> {},
			(connection, closeReason) -> {}
		);
		return clientWebsocketContainer.connectToServer(clientEndpoint, null, url);
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
