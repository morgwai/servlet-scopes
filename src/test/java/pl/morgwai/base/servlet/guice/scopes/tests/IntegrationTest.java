// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.DeploymentException;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.junit.*;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.tests.server.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.server.AsyncServlet.*;



public class IntegrationTest {



	org.eclipse.jetty.client.HttpClient wsHttpClient;
	WebSocketContainer clientWebsocketContainer;
	HttpClient httpClient;
	TestServer server;
	int port;
	String forwardingServletUrl;
	String serverWebsocketUrl;
	String appWebsocketUrl;



	/**
	 * Creates a {@link #server servlet container}, an {@link #httpClient HTTP client}, a
	 * {@link #clientWebsocketContainer client websocket container} and some helper URL prefixes
	 * ({@link #forwardingServletUrl}, {@link #appWebsocketUrl}).
	 */
	@Before
	public void setup() throws Exception {
		final var cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JavaxWebSocketClientContainerProvider.getContainer(wsHttpClient);
		httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
		server = new TestServer(0);
		server.start();
		port = ((ServerSocketChannel) server.getConnectors()[0].getTransport())
				.socket().getLocalPort();
		forwardingServletUrl = "http://localhost:" + port
				+ TestServer.APP_PATH + '/' + ForwardingServlet.class.getSimpleName();
		serverWebsocketUrl = "ws://localhost:" + port;
		appWebsocketUrl = serverWebsocketUrl + TestServer.APP_PATH
				+ ServletContextListener.WEBSOCKET_PATH;
	}



	/** Shutdowns {@link #clientWebsocketContainer} adn {@link #server}. */
	@After
	public void shutdown() throws Exception {
		final var jettyWsContainer = ((JavaxWebSocketContainer) clientWebsocketContainer);
		jettyWsContainer.stop();
		jettyWsContainer.destroy();
		wsHttpClient.stop();
		wsHttpClient.destroy();
		server.stop();
		server.join();
		server.destroy();
	}



	/**
	 * Sends {@code request} to the {@link #server}, verifies and returns the response.
	 * Specifically, verifies if the response code is 200, if the body has 4 lines and if the first
	 * line contains the {@link Class#getSimpleName() simple name} of the class passed as
	 * {@code expectedTargetServletClass} param.
	 * @return an array of 4 {@code Strings} corresponding to the response body lines.
	 */
	String[] sendServletRequest(HttpRequest request, Class<?> expectedTargetServletClass)
			throws Exception {
		final var response = httpClient.send(request, BodyHandlers.ofString());
		if (log.isLoggable(Level.FINE)) log.fine("response from " + request.uri() + ", status: "
				+ response.statusCode() + '\n' + response.body());
		assertEquals("response code should be 'OK'", 200, response.statusCode());
		final var responseLines = response.body().split("\n");
		assertEquals("response should have 4 lines", 4, responseLines.length);
		assertEquals("processing should be dispatched to the correct servlet",
				expectedTargetServletClass.getSimpleName(), responseLines[0]);
		return responseLines;
	}

	/**
	 * {@link #sendServletRequest(HttpRequest, Class) Sends} 2 GET requests to {@code url} and
	 * verifies scoping. Specifically, assumes that the response corresponds to
	 * {@link TestServlet#RESPONSE_FORMAT} and verifies that request-scoped hash has changed while
	 * session-scoped remained the same.
	 * @return a {@code List} containing both response bodies as returned by
	 *     {@link #sendServletRequest(HttpRequest, Class)}.
	 */
	List<String[]> testAsyncCtxDispatch(String url, Class<?> expectedTargetServletClass)
			throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(url)).GET()
				.timeout(Duration.ofSeconds(2)).build();
		final var responseLines = sendServletRequest(request, expectedTargetServletClass);
		final var responseLines2 = sendServletRequest(request, expectedTargetServletClass);
		assertEquals("session scoped object hash should remain the same",
				responseLines[3], responseLines2[3]);
		assertNotEquals("request scoped object hash should change",
				responseLines[2], responseLines2[2]);
		return List.of(responseLines, responseLines2);
	}

	@Test
	public void testUnwrappedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED,
			ForwardingServlet.class
		);
	}

	@Test
	public void testUnwrappedTargetedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
	}

	@Test
	public void testWrappedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
			AsyncServlet.class
		);
	}

	@Test
	public void testWrappedTargetedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
	}



	/**
	 * Connects to a server endpoint at {@code url} and sends 1 test message.
	 * Returns a list containing 2 messages received from the server: the initial welcome message
	 * and the reply to the test message that was sent.
	 * Each message is split into lines. Each message is expected to have the format defined in
	 * {@link EchoEndpoint}. Both messages are expected to be sent from the same HTTP session scope
	 * and the same websocket connection scope.
	 */
	List<String[]> testSingleMessageToServerEndpoint(URI url) throws Exception {
		final var testMessage = "test message for " + url;
		final var replies = new ArrayList<String[]>(4);
		final var testMessageSent = new CountDownLatch(1);
		final var repliesReceived = new CountDownLatch(2);
		final var testThread = Thread.currentThread();
		final var endpoint = new ClientEndpoint(
			(reply) -> {
				replies.add(reply.split("\n"));
				repliesReceived.countDown();
			},
			(connection, error) -> {},
			(connection, closeReason) -> {
				if (closeReason.getCloseCode().getCode() != CloseCodes.NORMAL_CLOSURE.getCode()) {
					try {
						testMessageSent.await(500L, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ignored) {}
					testThread.interrupt();
				}
			}
		);
		final var connection = clientWebsocketContainer.connectToServer(endpoint, null, url);
		connection.getAsyncRemote().sendText(testMessage);
		testMessageSent.countDown();
		try {
			if ( !repliesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
		} catch (InterruptedException e) {
			fail("server side error");
		}
		connection.close();
		if ( !endpoint.awaitClosure(2L, TimeUnit.SECONDS)) fail("timeout");
		assertEquals("reply should have 5 lines", 5, replies.get(0).length);
		assertEquals("reply should have 5 lines", 5, replies.get(1).length);
		assertEquals("onOpen reply should be a welcome",
				EchoEndpoint.WELCOME_MESSAGE, replies.get(0)[0]);
		assertEquals("2nd reply should be an echo",
				testMessage, replies.get(1)[0]);
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
	 * {@link #testSingleMessageToServerEndpoint(URI)} made via separate websocket connections.
	 * All 4 messages are expected to be sent from the same HTTP session scope.
	 */
	List<String[]> testServerEndpoint(String type) throws Exception {
		final var url = URI.create(appWebsocketUrl + type);
		final var replies = testSingleMessageToServerEndpoint(url);
		replies.addAll(testSingleMessageToServerEndpoint(url));
		assertEquals("session scoped object hash should remain the same",
				replies.get(0)[3], replies.get(2)[3]);
		assertNotEquals("connection scoped object hash should change",
				replies.get(0)[4], replies.get(2)[4]);
		return replies;
	}

	@Test
	public void testProgrammaticEndpoint() throws Exception {
		testServerEndpoint(ProgrammaticEndpoint.TYPE);
	}

	@Test
	public void testExtendingEndpoint() throws Exception {
		testServerEndpoint(ExtendingEndpoint.TYPE);
	}

	@Test
	public void testAnnotatedEndpoint() throws Exception {
		testServerEndpoint(AnnotatedEndpoint.TYPE);
	}



	/** Performs all the above positive tests in 1 session to check for undesired interactions. */
	@Test
	public void testAllInOne() throws Exception {
		final var requestScopedHashes = new HashSet<>();
		final var connectionScopedHashes = new HashSet<>();

		final var unwrappedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED,
			ForwardingServlet.class
		);
		final var sessionScopedHash = unwrappedAsyncCtxResponses.get(0)[3];
		requestScopedHashes.add(unwrappedAsyncCtxResponses.get(0)[2]);
		requestScopedHashes.add(unwrappedAsyncCtxResponses.get(1)[2]);

		final var unwrappedTargetedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, unwrappedTargetedAsyncCtxResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(unwrappedTargetedAsyncCtxResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(unwrappedTargetedAsyncCtxResponses.get(1)[2]));

		final var wrappedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
			AsyncServlet.class
		);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, wrappedAsyncCtxResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(wrappedAsyncCtxResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(wrappedAsyncCtxResponses.get(1)[2]));

		final var wrappedTargetedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, wrappedTargetedAsyncCtxResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(wrappedTargetedAsyncCtxResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(wrappedTargetedAsyncCtxResponses.get(1)[2]));

		final var programmaticEndpointResponses = testServerEndpoint(ProgrammaticEndpoint.TYPE);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, programmaticEndpointResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(programmaticEndpointResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(programmaticEndpointResponses.get(1)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(programmaticEndpointResponses.get(2)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(programmaticEndpointResponses.get(3)[2]));
		connectionScopedHashes.add(programmaticEndpointResponses.get(0)[4]);
		connectionScopedHashes.add(programmaticEndpointResponses.get(2)[4]);

		final var extendingEndpointResponses = testServerEndpoint(ExtendingEndpoint.TYPE);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, extendingEndpointResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(extendingEndpointResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(extendingEndpointResponses.get(1)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(extendingEndpointResponses.get(2)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(extendingEndpointResponses.get(3)[2]));
		assertTrue("connection scoped object hash should change",
				connectionScopedHashes.add(extendingEndpointResponses.get(0)[4]));
		assertTrue("connection scoped object hash should change",
				connectionScopedHashes.add(extendingEndpointResponses.get(2)[4]));

		final var annotatedEndpointResponses = testServerEndpoint(AnnotatedEndpoint.TYPE);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, annotatedEndpointResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(annotatedEndpointResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(annotatedEndpointResponses.get(1)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(annotatedEndpointResponses.get(2)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(annotatedEndpointResponses.get(3)[2]));
		assertTrue("connection scoped object hash should change",
				connectionScopedHashes.add(annotatedEndpointResponses.get(0)[4]));
		assertTrue("connection scoped object hash should change",
				connectionScopedHashes.add(annotatedEndpointResponses.get(2)[4]));
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
		Logger.getLogger(GuiceServerEndpointConfigurator.class.getName()).setLevel(Level.OFF);
		try {
			testOpenConnectionToServerEndpoint(OnOpenWithoutSessionParamEndpoint.TYPE);
			fail("instantiation of OnOpenWithoutSessionParamEndpoint should throw an Exception");
		} catch (Exception expected) {}
	}

	@Test
	public void testPingingWithoutOnCloseEndpoint() {
		Logger.getLogger(GuiceServerEndpointConfigurator.class.getName()).setLevel(Level.OFF);
		try {
			testOpenConnectionToServerEndpoint(PingingWithoutOnCloseEndpoint.TYPE);
			fail("instantiation of PingingWithoutOnCloseEndpoint should throw an Exception");
		} catch (Exception expected) {}
	}



	public void testAppSeparation(String testAppWebsocketUrl, String secondAppWebsocketUrl)
			throws InterruptedException, DeploymentException, IOException {
		final var messages = new ArrayList<String>(2);

		final var endpoint = new ClientEndpoint(messages::add, null, null);
		final var uri1 = URI.create(testAppWebsocketUrl);
		clientWebsocketContainer.connectToServer(endpoint, null, uri1);
		if ( !endpoint.awaitClosure(500L, TimeUnit.MILLISECONDS)) fail("timeout");

		final var endpoint2 = new ClientEndpoint(messages::add, null, null);
		final var uri2 = URI.create(secondAppWebsocketUrl);
		clientWebsocketContainer.connectToServer(endpoint2, null, uri2);
		if ( !endpoint2.awaitClosure(500L, TimeUnit.MILLISECONDS)) fail("timeout");

		assertNotEquals("Endpoint Configurators of separate apps should have separate Injectors",
				messages.get(0), messages.get(1));
	}

	@Test
	public void testAppSeparation() throws InterruptedException, DeploymentException, IOException {
		testAppSeparation(
			appWebsocketUrl + AppSeparationTestEndpoint.TYPE,
			serverWebsocketUrl + TestServer.SECOND_APP_PATH
					+ ServletContextListener.WEBSOCKET_PATH + AppSeparationTestEndpoint.TYPE
		);
	}

	@Test
	public void testAppSeparationNoSession()
			throws InterruptedException, DeploymentException, IOException {
		testAppSeparation(
			serverWebsocketUrl + TestServer.APP_PATH + NoSessionAppSeparationTestEndpoint.PATH,
			serverWebsocketUrl + TestServer.SECOND_APP_PATH
					+ NoSessionAppSeparationTestEndpoint.PATH
		);
	}



	/**
	 * Change the below value if you need logging:<br/>
	 * <code>INFO</code> will log server startup and shutdown diagnostics<br/>
	 * <code>FINE</code> will log every response/message received from the server.
	 */
	static Level LOG_LEVEL = Level.WARNING;

	static final Logger log = Logger.getLogger(IntegrationTest.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					IntegrationTest.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
