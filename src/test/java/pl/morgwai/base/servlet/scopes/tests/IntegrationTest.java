// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.ServerSocketChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.util.component.LifeCycle;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import pl.morgwai.base.servlet.scopes.tests.server.AnnotatedEndpoint;
import pl.morgwai.base.servlet.scopes.tests.server.AsyncServlet;
import pl.morgwai.base.servlet.scopes.tests.server.DispatchingServlet;
import pl.morgwai.base.servlet.scopes.tests.server.EchoEndpoint;
import pl.morgwai.base.servlet.scopes.tests.server.ExtendingEndpoint;
import pl.morgwai.base.servlet.scopes.tests.server.ProgrammaticEndpoint;
import pl.morgwai.base.servlet.scopes.tests.server.ServletContextListener;
import pl.morgwai.base.servlet.scopes.tests.server.TestServer;
import pl.morgwai.base.servlet.scopes.tests.server.TestServlet;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.scopes.tests.server.AsyncServlet.*;



public class IntegrationTest {



	WebSocketContainer clientWebsocketContainer;
	HttpClient httpClient;
	TestServer server;
	int port;
	String dispatchingServletUrl;
	String websocketUrl;



	@Before
	public void setup() throws Exception {
		final var cookieManager = new CookieManager();
		CookieHandler.setDefault(cookieManager);
		clientWebsocketContainer = ContainerProvider.getWebSocketContainer();
		httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
		server = new TestServer(0);
		server.start();
		port = ((ServerSocketChannel) server.getConnectors()[0].getTransport())
				.socket().getLocalPort();
		dispatchingServletUrl =
				"http://localhost:" + port + TestServer.APP_PATH + DispatchingServlet.PATH;
		websocketUrl = "ws://localhost:" + port + TestServer.APP_PATH
				+ ServletContextListener.WEBSOCKET_PATH + '/';
	}



	@After
	public void shutdown() throws Exception {
		server.stop();
		server.join();
		LifeCycle.stop(clientWebsocketContainer);
	}



	/**
	 * Sends 2 GET requests to {@code url}.
	 * Returns a list containing both response bodies. Each response body is split into lines.
	 * Each response body is expected to have the format defined in {@link TestServlet}.
	 * Both responses are expected to be sent from the same HTTP session scope.
	 */
	List<String[]> testAsyncCtxDispatch(String url, Class<?> expectedTargetServletClass)
			throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(url)).GET()
				.timeout(Duration.ofSeconds(2)).build();

		final var response = httpClient.send(request, BodyHandlers.ofString()).body();
		if (log.isLoggable(Level.FINE)) log.fine("response from " + url + '\n' + response);
		final var responseLines = response.split("\n");
		assertEquals("response should have 4 lines", 4, responseLines.length);
		assertEquals("processing should be dispatched to the correct servlet",
				expectedTargetServletClass.getSimpleName(), responseLines[0]);

		final var response2 = httpClient.send(request, BodyHandlers.ofString()).body();
		if (log.isLoggable(Level.FINE)) log.fine("response from " + url + '\n' + response2);
		final var responseLines2 = response2.split("\n");
		assertEquals("response should have 4 lines", 4, responseLines2.length);
		assertEquals("processing should be dispatched to the correct servlet",
				expectedTargetServletClass.getSimpleName(), responseLines2[0]);
		assertEquals("session scoped object hash should remain the same",
				responseLines[3], responseLines2[3]);
		assertNotEquals("request scoped object hash should change",
				responseLines[2], responseLines2[2]);

		return List.of(responseLines, responseLines2);
	}

	@Test
	public void testUnwrappedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(dispatchingServletUrl, DispatchingServlet.class);
	}

	@Test
	public void testWrappedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
				AsyncServlet.class);
	}

	@Test
	public void testTargetedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + MODE_PARAM + '=' + MODE_TARGETED,
				AsyncServlet.class);
	}



	/**
	 * Connects to a server endpoint at {@code url} and sends 1 test message.
	 * Returns a list containing 2 messages received from the server: the initial welcome message
	 * and the reply to the test message that was sent.
	 * Each message is split into lines. Each message is expected to have the format defined in
	 * {@link EchoEndpoint}. Both messages are expected to be sent from the same HTTP session scope
	 * and the same websocket connection scope.
	 */
	List<String[]> testSingleConnectionToServerEndpoint(URI url) throws Exception {
		final var testMessage = "test message for " + url;
		final var messages = new ArrayList<String[]>(4);
		final var latch = new CountDownLatch(2);
		final var endpoint = new ClientEndpoint(
			(message) -> {
				if (log.isLoggable(Level.FINE)) log.fine("message from " + url + '\n' + message);
				messages.add(message.split("\n"));
				latch.countDown();
			},
			(connection, error) -> {
				log.log(Level.WARNING, "error on connection " + connection.getId(), error);
			}
		);
		final var connection = clientWebsocketContainer.connectToServer(endpoint, null, url);
		connection.getAsyncRemote().sendText(testMessage);
		if ( ! latch.await(2l, TimeUnit.SECONDS)) fail("timeout");
		connection.close();
		if ( ! endpoint.awaitClosure(2l, TimeUnit.SECONDS)) fail("timeout");
		assertEquals("message should have 5 lines", 5, messages.get(0).length);
		assertEquals("message should have 5 lines", 5, messages.get(1).length);
		assertEquals("onOpen message should be a welcome",
				EchoEndpoint.WELCOME_MESSAGE, messages.get(0)[0]);
		assertEquals("2nd message should be an echo",
				testMessage, messages.get(1)[0]);
		assertEquals("session scoped object hash should remain the same",
				messages.get(0)[3], messages.get(1)[3]);
		assertEquals("connection scoped object hash should remain the same",
				messages.get(0)[4], messages.get(1)[4]);
		assertNotEquals("evemt scoped object hash should change",
				messages.get(0)[2], messages.get(1)[2]);
		return messages;
	}

	/**
	 * Returns a list containing 4 messages received from the server via 2 calls to
	 * {@link #testSingleConnectionToServerEndpoint(URI)} made via separate websocket connections.
	 * All 4 messages are expected to be sent from the same HTTP session scope.
	 */
	List<String[]> testServerEndpoint(String type) throws Exception {
		final var url = URI.create(websocketUrl + type);
		final var messages = testSingleConnectionToServerEndpoint(url);
		messages.addAll(testSingleConnectionToServerEndpoint(url));
		assertEquals("session scoped object hash should remain the same",
				messages.get(0)[3], messages.get(2)[3]);
		assertNotEquals("connection scoped object hash should change",
				messages.get(0)[4], messages.get(2)[4]);
		return messages;
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



	@Test
	public void testAllInOne() throws Exception {
		final var requestScopedHashes = new HashSet<>();
		final var connectionScopedHashes = new HashSet<>();

		final var unwrappedAsyncCtxResponses = testAsyncCtxDispatch(
				dispatchingServletUrl,
				DispatchingServlet.class);
		final var servletSessionScopedHash = unwrappedAsyncCtxResponses.get(0)[3];
		requestScopedHashes.add(unwrappedAsyncCtxResponses.get(0)[2]);
		requestScopedHashes.add(unwrappedAsyncCtxResponses.get(1)[2]);

		final var wrappedAsyncCtxResponses = testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
				AsyncServlet.class);
		assertEquals("session scoped object hash should remain the same",
				servletSessionScopedHash, wrappedAsyncCtxResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(wrappedAsyncCtxResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(wrappedAsyncCtxResponses.get(1)[2]));

		final var targetedAsyncCtxResponses = testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + MODE_PARAM + '=' + MODE_TARGETED,
				AsyncServlet.class);
		assertEquals("session scoped object hash should remain the same",
				servletSessionScopedHash, targetedAsyncCtxResponses.get(0)[3]);
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(targetedAsyncCtxResponses.get(0)[2]));
		assertTrue("call scoped object hash should change",
				requestScopedHashes.add(targetedAsyncCtxResponses.get(1)[2]));

		final var programmaticEndpointResponses = testServerEndpoint(ProgrammaticEndpoint.TYPE);
		// TODO: figure out how to share cookieHandler between HttpClient and WebSocketContainer
		final var websocketSessionScopedHash = programmaticEndpointResponses.get(0)[3];
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
				websocketSessionScopedHash, extendingEndpointResponses.get(0)[3]);
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
				websocketSessionScopedHash, annotatedEndpointResponses.get(0)[3]);
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
