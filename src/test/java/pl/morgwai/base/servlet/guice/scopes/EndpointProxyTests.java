// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;
import javax.websocket.*;

import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextTracker;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator.*;



public class EndpointProxyTests extends EasyMockSupport {



	/** Test subject. */
	TestEndpoint endpointProxy;

	final GuiceServerEndpointConfigurator configurator = new GuiceServerEndpointConfigurator();
	final ContextTracker<ContainerCallContext> ctxTracker = new ContextTracker<>();
	@Mock Session mockConnection;
	final Map<String, Object> userProperties = new HashMap<>(1);
	@Mock HttpSession mockHttpSession;



	@Before
	public void setup() throws Exception {
		injectMocks(this);
		userProperties.put(HttpSession.class.getName(), mockHttpSession);
		expect(mockConnection.getUserProperties())
			.andReturn(userProperties)
			.anyTimes();
		replayAll();
		final var proxyClass = configurator.getProxyClass(TestEndpoint.class);
		endpointProxy = proxyClass.getConstructor().newInstance();
		final var endpointProxyHandler = new EndpointProxyHandler(
			configurator.getAdditionalDecorator(
					new TestEndpoint(ctxTracker, mockConnection, mockHttpSession)),
			ctxTracker
		);
		proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
				.set(endpointProxy, endpointProxyHandler);
	}

	@After
	public void verifyMocks() {
		verifyAll();
	}



	@Test
	public void testOnOpenThenOnClose() {
		endpointProxy.onOpen(mockConnection, null);
		endpointProxy.onClose(mockConnection, null);
	}



	@Test
	public void testToStringBeforeOnOpen() {
		var ignored = endpointProxy.toString();
		endpointProxy.onOpen(mockConnection, null);
		endpointProxy.onClose(mockConnection, null);
	}



	public static class TestEndpoint extends Endpoint {

		final ContextTracker<ContainerCallContext> ctxTracker;
		final Session mockConnection;
		final HttpSession mockHttpSession;



		public TestEndpoint() {
			this(null, null, null);
		}



		public TestEndpoint(
			ContextTracker<ContainerCallContext> ctxTracker,
			Session mockConnection, HttpSession mockHttpSession
		) {
			this.ctxTracker = ctxTracker;
			this.mockConnection = mockConnection;
			this.mockHttpSession = mockHttpSession;
		}



		WebsocketEventContext openEventCtx;
		WebsocketConnectionContext connectionCtx;



		@Override public void onOpen(Session connectionProxy, EndpointConfig config) {
			assertTrue("connection should be wrapped with a proxy",
					connectionProxy instanceof WebsocketConnectionProxy);
			assertSame("connectionProxy should be wrapping the connection passed to the method",
					mockConnection, ((WebsocketConnectionProxy) connectionProxy).wrappedConnection);
			openEventCtx = (WebsocketEventContext) ctxTracker.getCurrentContext();
			assertNotNull("Endpoint methods should be executed within a WebsocketEventContext",
					openEventCtx);
			assertSame("openEventCtx should be referencing the HttpSession from userProperties",
					mockHttpSession, openEventCtx.httpSession);
			connectionCtx = openEventCtx.connectionContext;
			assertNotNull("a new WebsocketConnectionContext should be created",
					connectionCtx);
		}



		@Override public void onClose(Session connectionProxy, CloseReason closeReason) {
			assertTrue("connection should be wrapped with a proxy",
					connectionProxy instanceof WebsocketConnectionProxy);
			assertSame("connectionProxy should be wrapping the connection passed to the method",
					mockConnection, ((WebsocketConnectionProxy) connectionProxy).wrappedConnection);
			final var closeEventCtx = (WebsocketEventContext) ctxTracker.getCurrentContext();
			assertNotSame("each method should be executed within a separate WebsocketEventContext",
					openEventCtx, closeEventCtx);
			assertSame("closeEventCtx should be referencing the HttpSession from userProperties",
					mockHttpSession, closeEventCtx.httpSession);
			assertSame(
				"WebsocketConnectionContext should remain the same across events on the same "
						+ "connection",
				connectionCtx,
				closeEventCtx.connectionContext
			);
		}
	}
}
