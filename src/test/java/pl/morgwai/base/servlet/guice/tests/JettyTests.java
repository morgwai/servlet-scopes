// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.*;
import java.util.logging.Level;
import jakarta.servlet.DispatcherType;
import org.junit.Before;
import org.junit.Test;

import pl.morgwai.base.servlet.guice.tests.jetty.*;
import pl.morgwai.base.servlet.guice.tests.servercommon.*;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.tests.jetty.AsyncServlet.*;
import static pl.morgwai.base.servlet.guice.tests.jetty.CrossDeploymentDispatchingServlet.*;
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
	 * Sends {@code request} to the {@link #server}, verifies if the status code was
	 * {@code expectedStatusCode} and returns the reply as a {@link Properties}.
	 */
	Properties sendServletRequest(HttpRequest request, int expectedStatusCode) throws Exception {
		final var response = httpClient.send(request, BodyHandlers.ofInputStream());
		if (log.isLoggable(Level.FINE)) {
			log.fine("response from " + request.uri() + ", status: " + response.statusCode());
		}
		assertEquals("response status code should be " + expectedStatusCode,
				expectedStatusCode, response.statusCode());
		final var responseContent = new Properties(5);
		responseContent.load(response.body());
		return responseContent;
	}



	Properties testCrossDeploymentDispatching(DispatcherType dispatcherType) throws Exception {
		final var url = testAppUrl + '/' + CrossDeploymentDispatchingServlet.class.getSimpleName()
				+ '?' + DispatcherType.class.getSimpleName() + '=' + dispatcherType;
		final var request = HttpRequest.newBuilder(URI.create(url))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var reply = sendServletRequest(request, 200);
		assertEquals("there should be 9 properties in the reply",
				9, reply.size());
		assertNotEquals(
			"request-scoped instances of the initial and second deployments should be different",
			reply.getProperty(INITIAL_DEPLOYMENT_PREFIX + CONTAINER_CALL),
			reply.getProperty(SECOND_DEPLOYMENT_PREFIX + CONTAINER_CALL)
		);
		assertNotEquals(
			"session-scoped instances of the initial and second deployments should be different",
			reply.getProperty(INITIAL_DEPLOYMENT_PREFIX + HTTP_SESSION),
			reply.getProperty(SECOND_DEPLOYMENT_PREFIX + HTTP_SESSION)
		);
		return reply;
	}

	@Test
	public void testCrossDeploymentIncluding() throws Exception {
		final var reply = testCrossDeploymentDispatching(DispatcherType.INCLUDE);
		assertEquals(
			"request-scoped instances in the async Thread should be the same as in the initial "
					+ "deployment",
			reply.getProperty(INITIAL_DEPLOYMENT_PREFIX + CONTAINER_CALL),
			reply.getProperty(ASYNC_PREFIX + CONTAINER_CALL)
		);
		assertEquals(
			"session-scoped instances in the async Thread should be the same as in the initial "
					+ "deployment",
			reply.getProperty(INITIAL_DEPLOYMENT_PREFIX + HTTP_SESSION),
			reply.getProperty(ASYNC_PREFIX + HTTP_SESSION)
		);
	}

	@Test
	public void testCrossDeploymentForwarding() throws Exception {
		final var reply = testCrossDeploymentDispatching(DispatcherType.FORWARD);
		assertEquals(
			"request-scoped instances in the async Thread should be the same as in the second "
					+ "deployment",
			reply.getProperty(SECOND_DEPLOYMENT_PREFIX + CONTAINER_CALL),
			reply.getProperty(ASYNC_PREFIX + CONTAINER_CALL)
		);
		assertEquals(
			"session-scoped instances in the async Thread should be the same as in the second "
					+ "deployment",
			reply.getProperty(SECOND_DEPLOYMENT_PREFIX + HTTP_SESSION),
			reply.getProperty(ASYNC_PREFIX + HTTP_SESSION)
		);
	}



	public void testErrorDispatching(String path) throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(testAppUrl + path))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var reply = sendServletRequest(request, 404);
		assertEquals(
			"processing of the second request should be dispatched to the correct servlet",
			ErrorDispatchingServlet.class.getSimpleName(),
			reply.getProperty(TestServlet.REPLYING_SERVLET)
		);
	}

	@Test
	public void testErrorDispatchingFromUserMiss() throws Exception {
		testErrorDispatching(ErrorDispatchingServlet.NON_EXISTENT_PATH);
	}

	@Test
	public void testErrorDispatchingFromAppServletMiss() throws Exception {
		testErrorDispatching("/" + ErrorDispatchingServlet.class.getSimpleName());
	}



	/**
	 * {@link #sendServletRequest(HttpRequest, int) Sends 2 GET requests} to {@code url} and
	 * verifies scoping.
	 * Specifically checks if {@link Service#HTTP_SESSION} property is the same in both responses
	 * and if {@link Service#CONTAINER_CALL} is different.
	 * @return a {@code List} containing both responses as returned by
	 *     {@link #sendServletRequest(HttpRequest, int)}.
	 */
	List<Properties> testAsyncCtxDispatch(String url, Class<?> expectedReplyingServletClass)
			throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(url))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var firstReply = sendServletRequest(request, 200);
		final var secondReply = sendServletRequest(request, 200);
		assertEquals(
			"processing of the first request should be dispatched to the correct servlet",
			expectedReplyingServletClass.getSimpleName(),
			firstReply.getProperty(TestServlet.REPLYING_SERVLET)
		);
		assertEquals(
			"processing of the second request should be dispatched to the correct servlet",
			expectedReplyingServletClass.getSimpleName(),
			secondReply.getProperty(TestServlet.REPLYING_SERVLET)
		);
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
