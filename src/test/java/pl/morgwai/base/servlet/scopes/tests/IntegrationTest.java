// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.channels.ServerSocketChannel;
import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.morgwai.base.servlet.scopes.tests.server.AsyncServlet;
import pl.morgwai.base.servlet.scopes.tests.server.DispatchingServlet;
import pl.morgwai.base.servlet.scopes.tests.server.TestServer;

import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.scopes.tests.server.AsyncServlet.DISPATCH_PARAM_NAME;
import static pl.morgwai.base.servlet.scopes.tests.server.AsyncServlet.MODE_PARAM_NAME;
import static pl.morgwai.base.servlet.scopes.tests.server.AsyncServlet.MODE_WRAPPED;



public class IntegrationTest {



	HttpClient client;
	TestServer server;
	String dispatchingServletUrl;

	@Before
	public void setup() throws Exception {
		client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
		server = new TestServer(0);
		server.start();
		final var port = ((ServerSocketChannel) server.getConnectors()[0].getTransport())
				.socket().getLocalPort();
		dispatchingServletUrl =
				"http://localhost:" + port + TestServer.APP_PATH + DispatchingServlet.PATH;
	}

	@After
	public void shutdown() throws Exception {
		server.stop();
		server.join();
	}



	String[] testAsyncCtxDispatch(String url, Class<?> targetServletClass) throws Exception {
		final var request = HttpRequest.newBuilder(URI.create(url)).GET().build();

		final var response = client.send(request, BodyHandlers.ofString()).body();
		if (log.isDebugEnabled()) log.debug("response to " + url + '\n' + response);
		final var responseLines = response.split("\n");
		assertEquals("response should have 4 lines", 4, responseLines.length);
		assertEquals("processing should be dispatched to the correct servlet",
				targetServletClass.getSimpleName(), responseLines[0]);

		final var response2 = client.send(request, BodyHandlers.ofString()).body();
		if (log.isDebugEnabled()) log.debug("response to " + url + '\n' + response2);
		final var responseLines2 = response2.split("\n");
		assertEquals("response should have 4 lines", 4, responseLines2.length);
		assertEquals("processing should be dispatched to the correct servlet",
				targetServletClass.getSimpleName(), responseLines2[0]);
		assertEquals("session scoped object hash should remain the same",
				responseLines[3], responseLines2[3]);
		assertNotEquals("request scoped object hash should change",
				responseLines[2], responseLines2[2]);

		return new String[] {responseLines[3], responseLines[2], responseLines2[2]};
	}



	@Test
	public void testUnwrappedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(dispatchingServletUrl, DispatchingServlet.class);
	}



	@Test
	public void testWrappedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + MODE_PARAM_NAME + '=' + MODE_WRAPPED,
				AsyncServlet.class);
	}



	@Test
	public void testTargetedAsyncCtxDispatch() throws Exception {
		testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + DISPATCH_PARAM_NAME + '=' + AsyncServlet.PATH,
				AsyncServlet.class);
	}



	@Test
	public void testAllInOne() throws Exception {
		final var requestScopedHashes = new HashSet<>();

		final var unwrappedAsyncCtxResponseLines = testAsyncCtxDispatch(
				dispatchingServletUrl,
				DispatchingServlet.class);
		final var sessionScopedHash = unwrappedAsyncCtxResponseLines[0];
		requestScopedHashes.add(unwrappedAsyncCtxResponseLines[1]);
		requestScopedHashes.add(unwrappedAsyncCtxResponseLines[2]);

		final var wrappedAsyncCtxResponseLines = testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + MODE_PARAM_NAME + '=' + MODE_WRAPPED,
				AsyncServlet.class);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, wrappedAsyncCtxResponseLines[0]);
		assertTrue("request scoped object hash should change",
				requestScopedHashes.add(wrappedAsyncCtxResponseLines[1]));
		assertTrue("request scoped object hash should change",
				requestScopedHashes.add(wrappedAsyncCtxResponseLines[2]));

		final var targetedAsyncCtxResponseLines = testAsyncCtxDispatch(
				dispatchingServletUrl + '?' + DISPATCH_PARAM_NAME + '=' + AsyncServlet.PATH,
				AsyncServlet.class);
		assertEquals("session scoped object hash should remain the same",
				sessionScopedHash, targetedAsyncCtxResponseLines[0]);
		assertTrue("request scoped object hash should change",
				requestScopedHashes.add(targetedAsyncCtxResponseLines[1]));
		assertTrue("request scoped object hash should change",
				requestScopedHashes.add(targetedAsyncCtxResponseLines[2]));

		// TODO: add websockets
	}



	static final Logger log = LoggerFactory.getLogger(IntegrationTest.class.getName());
}
