// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;
import pl.morgwai.base.servlet.guice.scopes.tests.jetty.*;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.jetty.AsyncServlet.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Service.*;



public class JettyTests extends MultiAppWebsocketTests {



	HttpClient httpClient;
	String forwardingServletUrl;
	String forwardingServletSecondAppUrl;



	/**
	 * Creates {@link #httpClient} and calculates helper URL prefixes
	 * ({@link #forwardingServletUrl}, {@link #forwardingServletSecondAppUrl}).
	 */
	@Before
	public void setupHttpClient() {
		httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
	}



	@Override
	protected MultiAppServer createServer() throws Exception {
		final var server = new JettyServer(0);
		final var port = server.getPort();
		forwardingServletUrl = "http://localhost:" + port + Server.APP_PATH + '/'
				+ ForwardingServlet.class.getSimpleName();
		forwardingServletSecondAppUrl = "http://localhost:" + port + MultiAppServer.SECOND_APP_PATH
				+ '/' + ForwardingServlet.class.getSimpleName();
		return server;
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return true;
	}



	/**
	 * Sends {@code request} to the {@link #server}, verifies and returns the response.
	 * Specifically, verifies if the response code is 200, parses the body as {@link Properties} and
	 * checks if property {@link TestServlet#RESPONDING_SERVLET} is equal to
	 * {@code expectedTargetServletClass}.
	 * @return a Properties received from the {@link #server} (see
	 *     {@link TestServlet#doAsyncHandling(HttpServletRequest, HttpServletResponse)}).
	 */
	Properties sendServletRequest(HttpRequest request, Class<?> expectedTargetServletClass)
			throws Exception {
		final var response = httpClient.send(request, BodyHandlers.ofInputStream());
		if (log.isLoggable(Level.FINE)) {
			log.fine("response from " + request.uri() + ", status: " + response.statusCode());
		}
		assertEquals("response code should be 'OK'",
				200, response.statusCode());
		final var responseContent = new Properties(5);
		responseContent.load(response.body());
		assertEquals(
			"processing should be dispatched to the correct servlet",
			expectedTargetServletClass.getSimpleName(),
			responseContent.getProperty(TestServlet.RESPONDING_SERVLET)
		);
		return responseContent;
	}

	/**
	 * {@link #sendServletRequest(HttpRequest, Class) Sends 2 GET requests} to {@code url} and
	 * verifies scoping.
	 * Specifically checks if {@link Service#HTTP_SESSION} property is the same in both responses
	 * and if {@link Service#CONTAINER_CALL} is different.
	 * @return a {@code List} containing both responses as returned by
	 *     {@link #sendServletRequest(HttpRequest, Class)}.
	 */
	List<Properties> testAsyncCtxDispatch(String url, Class<?> expectedTargetServletClass)
			throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(url)).GET()
				.timeout(Duration.ofSeconds(2)).build();
		final var responseLines = sendServletRequest(request, expectedTargetServletClass);
		final var responseLines2 = sendServletRequest(request, expectedTargetServletClass);
		assertEquals(
			"session scoped object hash should remain the same",
			responseLines.getProperty(HTTP_SESSION),
			responseLines2.getProperty(HTTP_SESSION)
		);
		assertNotEquals(
			"request scoped object hash should change",
			responseLines.getProperty(CONTAINER_CALL),
			responseLines2.getProperty(CONTAINER_CALL)
		);
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

	@Test
	public void testUnwrappedAsyncCtxDispatchManualListener() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletSecondAppUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED,
			ForwardingServlet.class
		);
	}

	@Test
	public void testUnwrappedTargetedAsyncCtxDispatchManualListener() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletSecondAppUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED
				+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
	}

	@Test
	public void testWrappedAsyncCtxDispatchManualListener() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletSecondAppUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
			AsyncServlet.class
		);
	}

	@Test
	public void testWrappedTargetedAsyncCtxDispatchManualListener() throws Exception {
		testAsyncCtxDispatch(
			forwardingServletSecondAppUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED
				+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
	}



	/** Performs several servlet and websocket requests in a single {@code HttpSession}. */
	@Test
	public void testAllInOne() throws Exception {
		final var containerCallScopedHashes = new HashSet<>();
		final var connectionScopedHashes = new HashSet<>();

		final var unwrappedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED,
			ForwardingServlet.class
		);
		final var sessionScopedHash = unwrappedAsyncCtxResponses.get(0).getProperty(HTTP_SESSION);
		containerCallScopedHashes.add(
				unwrappedAsyncCtxResponses.get(0).getProperty(CONTAINER_CALL));
		containerCallScopedHashes.add(
				unwrappedAsyncCtxResponses.get(1).getProperty(CONTAINER_CALL));

		final var unwrappedTargetedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
		assertEquals(
			"session scoped object hash should remain the same",
			sessionScopedHash,
			unwrappedTargetedAsyncCtxResponses.get(0).getProperty(HTTP_SESSION)
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					unwrappedTargetedAsyncCtxResponses.get(0).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					unwrappedTargetedAsyncCtxResponses.get(1).getProperty(CONTAINER_CALL))
		);

		final var wrappedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
			AsyncServlet.class
		);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, wrappedAsyncCtxResponses.get(0).getProperty(HTTP_SESSION));
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					wrappedAsyncCtxResponses.get(0).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					wrappedAsyncCtxResponses.get(1).getProperty(CONTAINER_CALL))
		);

		final var wrappedTargetedAsyncCtxResponses = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class
		);
		assertEquals(
			"session scoped object hash should remain the same",
			sessionScopedHash,
			wrappedTargetedAsyncCtxResponses.get(0).getProperty(HTTP_SESSION)
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					wrappedTargetedAsyncCtxResponses.get(0).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					wrappedTargetedAsyncCtxResponses.get(1).getProperty(CONTAINER_CALL))
		);

		final var programmaticEndpointResponses =
				test2SessionsWithServerEndpoint(appWebsocketUrl + ProgrammaticEndpoint.PATH, true);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, programmaticEndpointResponses.get(0).getProperty(HTTP_SESSION));
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					programmaticEndpointResponses.get(0).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					programmaticEndpointResponses.get(1).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					programmaticEndpointResponses.get(2).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					programmaticEndpointResponses.get(3).getProperty(CONTAINER_CALL))
		);
		connectionScopedHashes.add(
				programmaticEndpointResponses.get(0).getProperty(WEBSOCKET_CONNECTION));
		connectionScopedHashes.add(
				programmaticEndpointResponses.get(2).getProperty(WEBSOCKET_CONNECTION));

		final var extendingEndpointResponses = test2SessionsWithServerEndpoint(
				appWebsocketUrl + AnnotatedExtendingEndpoint.PATH, true);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, extendingEndpointResponses.get(0).getProperty(HTTP_SESSION));
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					extendingEndpointResponses.get(0).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					extendingEndpointResponses.get(1).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					extendingEndpointResponses.get(2).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					extendingEndpointResponses.get(3).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"connection scoped object hash should change",
			connectionScopedHashes.add(
				extendingEndpointResponses.get(0).getProperty(WEBSOCKET_CONNECTION)));
		assertTrue(
			"connection scoped object hash should change",
			connectionScopedHashes.add(
					extendingEndpointResponses.get(2).getProperty(WEBSOCKET_CONNECTION))
		);

		final var annotatedEndpointResponses =
				test2SessionsWithServerEndpoint(appWebsocketUrl + AnnotatedEndpoint.PATH, true);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, annotatedEndpointResponses.get(0).getProperty(HTTP_SESSION));
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					annotatedEndpointResponses.get(0).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					annotatedEndpointResponses.get(1).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					annotatedEndpointResponses.get(2).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"call scoped object hash should change",
			containerCallScopedHashes.add(
					annotatedEndpointResponses.get(3).getProperty(CONTAINER_CALL))
		);
		assertTrue(
			"connection scoped object hash should change",
			connectionScopedHashes.add(
					annotatedEndpointResponses.get(0).getProperty(WEBSOCKET_CONNECTION))
		);
		assertTrue(
			"connection scoped object hash should change",
			connectionScopedHashes.add(
					annotatedEndpointResponses.get(2).getProperty(WEBSOCKET_CONNECTION))
		);
	}
}
