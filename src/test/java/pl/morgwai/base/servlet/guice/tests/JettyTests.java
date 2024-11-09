// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import pl.morgwai.base.servlet.guice.tests.jetty.*;
import pl.morgwai.base.servlet.guice.tests.servercommon.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.tests.jetty.AsyncServlet.*;
import static pl.morgwai.base.servlet.guice.tests.jetty.CrossDeploymentIncludingServlet.*;
import static pl.morgwai.base.servlet.guice.tests.servercommon.Service.*;



public class JettyTests extends MultiAppWebsocketTests {



	HttpClient httpClient;
	String testAppUrl;
	String secondAppUrl;
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
	protected MultiAppServer createServer(String testName) throws Exception {
		final var server = new JettyServer(0, testName);
		final var serverAddress = "http://localhost:" + server.getPort();
		testAppUrl = serverAddress + Server.TEST_APP_PATH;
		secondAppUrl = serverAddress + MultiAppServer.SECOND_APP_PATH;
		forwardingServletUrl = testAppUrl + '/' + ForwardingServlet.class.getSimpleName();
		forwardingServletSecondAppUrl =
				secondAppUrl + '/' + ForwardingServlet.class.getSimpleName();
		return server;
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return true;
	}



	/**
	 * Sends {@code request} to the {@link #server}, verifies and returns the response.
	 * Specifically, verifies if the response code is 200, parses the body as {@link Properties} and
	 * checks if property {@link TestServlet#REPLYING_SERVLET} is equal to
	 * {@code expectedRespondingServletClass}.
	 * @return a Properties received from the {@link #server} (see
	 *     {@link TestServlet#verifyScopingAndSendReply(HttpServletRequest, HttpServletResponse)}).
	 */
	Properties sendServletRequest(
		HttpRequest request,
		int expectedStatusCode,
		Class<?> expectedRespondingServletClass
	) throws Exception {
		final var response = httpClient.send(request, BodyHandlers.ofInputStream());
		if (log.isLoggable(Level.FINE)) {
			log.fine("response from " + request.uri() + ", status: " + response.statusCode());
		}
		assertEquals("response status code should be " + expectedStatusCode,
				expectedStatusCode, response.statusCode());
		final var responseContent = new Properties(5);
		responseContent.load(response.body());
		assertEquals(
			"processing should be dispatched to the correct servlet",
			expectedRespondingServletClass.getSimpleName(),
			responseContent.getProperty(TestServlet.REPLYING_SERVLET)
		);
		return responseContent;
	}



	@Test
	public void testCrossDeploymentIncluding() throws Exception {
		final var path = testAppUrl + '/' + CrossDeploymentIncludingServlet.class.getSimpleName();
		final var request = HttpRequest.newBuilder(URI.create(path))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var reply = sendServletRequest(request, 200, CrossDeploymentIncludingServlet.class);
		assertEquals("there should be 5 properties in the reply",
				5, reply.size());
		assertNotEquals(
			"request-scoped instances of initial and included deployment should be different",
			reply.getProperty(CONTAINER_CALL),
			reply.getProperty(INCLUDED_DEPLOYMENT_PREFIX + CONTAINER_CALL)
		);
		assertNotEquals(
			"session-scoped instances of initial and included deployment should be different",
			reply.getProperty(HTTP_SESSION),
			reply.getProperty(INCLUDED_DEPLOYMENT_PREFIX + HTTP_SESSION)
		);
	}



	public void testErrorDispatching(String path) throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(testAppUrl + path))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		sendServletRequest(request, 404, ErrorTestingServlet.class);
	}

	@Test
	public void testErrorDispatchingFromUserMiss() throws Exception {
		testErrorDispatching("/nonExistent");
	}

	@Test
	public void testErrorDispatchingFromAppServletMiss() throws Exception {
		testErrorDispatching("/" + ErrorTestingServlet.class.getSimpleName());
	}



	/**
	 * {@link #sendServletRequest(HttpRequest, int, Class) Sends 2 GET requests} to {@code url} and
	 * verifies scoping.
	 * Specifically checks if {@link Service#HTTP_SESSION} property is the same in both responses
	 * and if {@link Service#CONTAINER_CALL} is different.
	 * @return a {@code List} containing both responses as returned by
	 *     {@link #sendServletRequest(HttpRequest, int, Class)}.
	 */
	List<Properties> testAsyncCtxDispatch(String url, Class<?> expectedReplyingServletClass)
			throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(url))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var firstReply = sendServletRequest(request, 200, expectedReplyingServletClass);
		final var secondReply = sendServletRequest(request, 200, expectedReplyingServletClass);
		assertEquals(
			"session scoped object hash should remain the same",
			firstReply.getProperty(HTTP_SESSION),
			secondReply.getProperty(HTTP_SESSION)
		);
		assertNotEquals(
			"request scoped object hash should change",
			firstReply.getProperty(CONTAINER_CALL),
			secondReply.getProperty(CONTAINER_CALL)
		);
		return List.of(firstReply, secondReply);
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
		final var containerCallScopedHashes = new HashSet<String>();
		final var connectionScopedHashes = new HashSet<String>();

		final var unwrappedAsyncCtxReplies = testAsyncCtxDispatch(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED,
			ForwardingServlet.class
		);
		final var sessionScopedHash = unwrappedAsyncCtxReplies.get(0).getProperty(HTTP_SESSION);
		addAndVerifyContainerCallHashes(unwrappedAsyncCtxReplies, containerCallScopedHashes);

		addAndVerifyServletReplies(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_UNWRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class,
			containerCallScopedHashes,
			sessionScopedHash
		);
		addAndVerifyServletReplies(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED,
			AsyncServlet.class,
			containerCallScopedHashes,
			sessionScopedHash
		);
		addAndVerifyServletReplies(
			forwardingServletUrl + '?' + MODE_PARAM + '=' + MODE_WRAPPED
					+ '&' + TARGET_PATH_PARAM + "=/" + TargetedServlet.class.getSimpleName(),
			TargetedServlet.class,
			containerCallScopedHashes,
			sessionScopedHash
		);
		addAndVerifyWebsocketResults(
			ProgrammaticEndpoint.PATH,
			containerCallScopedHashes,
			connectionScopedHashes,
			sessionScopedHash
		);
		addAndVerifyWebsocketResults(
			AnnotatedExtendingEndpoint.PATH,
			containerCallScopedHashes,
			connectionScopedHashes,
			sessionScopedHash
		);
		addAndVerifyWebsocketResults(
			AnnotatedEndpoint.PATH,
			containerCallScopedHashes,
			connectionScopedHashes,
			sessionScopedHash
		);
	}

	void addAndVerifyServletReplies(
		String url,
		Class<?> expectedRespondingServletClass,
		Set<String> containerCallScopedHashes,
		String sessionScopedHash
	) throws Exception {
		final var replies = testAsyncCtxDispatch(url, expectedRespondingServletClass);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, replies.get(0).getProperty(HTTP_SESSION));
		addAndVerifyContainerCallHashes(replies, containerCallScopedHashes);
	}

	void addAndVerifyWebsocketResults(
		String websocketPath,
		Set<String> containerCallScopedHashes,
		Set<String> connectionScopedHashes,
		String sessionScopedHash
	) throws Exception {
		final var clientEndpoints = test2SessionsWithServerEndpoint(
				appWebsocketUrl + websocketPath, true);
		assertEquals(
			"session scoped object hash should remain the same",
			sessionScopedHash,
			clientEndpoints.get(0).getServerReplies().get(0).getProperty(HTTP_SESSION)
		);
		for (var clientEndpoint: clientEndpoints) {
			final var serverReplies = clientEndpoint.getServerReplies();
			assertTrue("connection scoped object hash should change",
					connectionScopedHashes.add(
							serverReplies.get(0).getProperty(WEBSOCKET_CONNECTION)));
			addAndVerifyContainerCallHashes(serverReplies, containerCallScopedHashes);
		}
	}

	void addAndVerifyContainerCallHashes(
		List<Properties> replies,
		Set<String> containerCallScopedHashes
	) {
		for (var reply: replies) {
			assertTrue("call scoped object hash should change",
					containerCallScopedHashes.add(reply.getProperty(CONTAINER_CALL)));
		}
	}
}
