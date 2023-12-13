// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.websocket.DeploymentException;

import org.junit.Before;
import org.junit.Test;
import pl.morgwai.base.servlet.guice.scopes.tests.*;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.jetty.AsyncServlet.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server.APP_PATH;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server.WEBSOCKET_PATH;



public class WebsocketAndServletJettyTests extends WebsocketIntegrationTests {



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
	protected Server createServer() throws Exception {
		final var server = new WebsocketAndServletJetty(0);
		final var port = server.getPort();
		forwardingServletUrl = "http://localhost:" + port + Server.APP_PATH + '/'
				+ ForwardingServlet.class.getSimpleName();
		forwardingServletSecondAppUrl = "http://localhost:" + port + Server.SECOND_APP_PATH + '/'
				+ ForwardingServlet.class.getSimpleName();
		return server;
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return true;
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



	@Test
	public void testProgrammaticEndpointManualListener() throws Exception {
		test2SessionsWithServerEndpoint(
			serverWebsocketUrl + Server.SECOND_APP_PATH + ProgrammaticEndpoint.PATH,
			true
		);
	}

	@Test
	public void testAnnotatedEndpointManualListener() throws Exception {
		test2SessionsWithServerEndpoint(
			serverWebsocketUrl + Server.SECOND_APP_PATH + AnnotatedEndpoint.PATH,
			true
		);
	}

	@Test
	public void testRttReportingEndpointManualListener() throws Exception {
		test2SessionsWithServerEndpoint(
			serverWebsocketUrl + Server.SECOND_APP_PATH + RttReportingEndpoint.PATH,
			false
		);
	}

	@Test
	public void testExtendingEndpointManualListener() throws Exception {
		test2SessionsWithServerEndpoint(
			serverWebsocketUrl + Server.SECOND_APP_PATH + AnnotatedExtendingEndpoint.PATH,
			true
		);
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

		final var programmaticEndpointResponses =
				test2SessionsWithServerEndpoint(appWebsocketUrl + ProgrammaticEndpoint.TYPE, true);
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

		final var extendingEndpointResponses = test2SessionsWithServerEndpoint(
				appWebsocketUrl + AnnotatedExtendingEndpoint.TYPE, true);
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

		final var annotatedEndpointResponses =
				test2SessionsWithServerEndpoint(appWebsocketUrl + AnnotatedEndpoint.TYPE, true);
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
			serverWebsocketUrl + Server.SECOND_APP_PATH
					+ WEBSOCKET_PATH + AppSeparationTestEndpoint.TYPE
		);
	}

	@Test
	public void testAppSeparationNoSession()
			throws InterruptedException, DeploymentException, IOException {
		testAppSeparation(
			serverWebsocketUrl + Server.APP_PATH + NoSessionAppSeparationTestEndpoint.PATH,
			serverWebsocketUrl + Server.SECOND_APP_PATH
					+ NoSessionAppSeparationTestEndpoint.PATH
		);
	}
}
