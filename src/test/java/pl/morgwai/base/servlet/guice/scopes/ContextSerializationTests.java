// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.util.HashMap;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.Session;

import org.junit.Test;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;

import static org.easymock.EasyMock.*;
import static pl.morgwai.base.guice.scopes.ContextSerializationTestUtils.testContextSerialization;
import static pl.morgwai.base.servlet.guice.scopes.HttpSessionContext.CUSTOM_SERIALIZATION_PARAM;



public class ContextSerializationTests {



	@Test
	public void testWebsocketConnectionContextSerialization() throws IOException {
		final var connectionProperties = new HashMap<String, Object>();
		final Session connectionMock = createMock(Session.class);
		expect(connectionMock.getUserProperties())
			.andReturn(connectionProperties)
			.anyTimes();
		replay(connectionMock);
		final var connectionProxy = new WebsocketConnectionProxy(connectionMock, null, true);
		final var ctx = new WebsocketConnectionContext(connectionProxy);
		testContextSerialization(ctx);
		verify(connectionMock);
	}



	public void testHttpSessionContextSerialization(boolean customSerialization) throws IOException
	{
		final var servletContextMock = new StandaloneWebsocketContainerServletContext("");
		servletContextMock.setInitParameter(
			CUSTOM_SERIALIZATION_PARAM,
			String.valueOf(customSerialization)
		);
		final HttpSession sessionMock = createMock(HttpSession.class);
		expect(sessionMock.getServletContext())
			.andReturn(servletContextMock)
			.anyTimes();
		replay(sessionMock);
		final var ctx = new HttpSessionContext(sessionMock);
		testContextSerialization(ctx);
		verify(sessionMock);
	}

	@Test
	public void testHttpSessionContextSerializationWithCustomSerialization() throws IOException {
		testHttpSessionContextSerialization(true);
	}

	@Test
	public void testHttpSessionContextSerializationWithStandardSerialization() throws IOException {
		testHttpSessionContextSerialization(false);
	}
}
