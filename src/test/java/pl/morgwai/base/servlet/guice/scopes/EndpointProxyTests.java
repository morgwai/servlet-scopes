// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpSession;
import javax.websocket.*;

import org.easymock.*;
import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextTracker;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator.*;



public class EndpointProxyTests extends EasyMockSupport {



	/** Test subject. */
	TestEndpoint endpointProxy;

	TestEndpoint testEndpoint;
	@Mock Session mockConnection;
	final Map<String, Object> userProperties = new HashMap<>(2);

	final GuiceServerEndpointConfigurator configurator = new GuiceServerEndpointConfigurator();
	final ContextTracker<ContainerCallContext> ctxTracker = new ContextTracker<>();
	Class<? extends TestEndpoint> proxyClass;
	@Mock HttpSession mockHttpSession;



	@Before
	public void setup() throws Exception {
		injectMocks(this);
		proxyClass = configurator.getProxyClass(TestEndpoint.class);

		userProperties.put(HttpSession.class.getName(), mockHttpSession);
		expect(mockConnection.getUserProperties())
			.andReturn(userProperties)
			.anyTimes();
		replayAll();
		endpointProxy = proxyClass.getConstructor().newInstance();
		testEndpoint = new TestEndpoint(ctxTracker, mockConnection, mockHttpSession);
	}

	@After
	public void verifyMocks() {
		verifyAll();
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



	@Test
	public void testOnOpenThenOnClose() throws Exception {
		final var endpointProxyHandler = new EndpointProxyHandler(
			(proxy, method, args) -> {
				assertNotNull("additional decorator should be executed within a Context",
						ctxTracker.getCurrentContext());
				return method.invoke(testEndpoint, args);
			},
			ctxTracker
		);
		proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
				.set(endpointProxy, endpointProxyHandler);

		endpointProxy.onOpen(mockConnection, null);
		endpointProxy.onClose(mockConnection, null);
	}



	@Test
	public void testToStringBeforeOnOpen() throws Exception {
		final var endpointProxyHandler = new EndpointProxyHandler(
			new InvocationHandler() {

				boolean onOpenCalled = false;

				@Override
				public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
					if (args != null) {
						for (var arg: args) {
							if (arg instanceof Session) {
								onOpenCalled = true;
								break;
							}
						}
					}
					if (onOpenCalled) {
						assertNotNull(
							"onOpen(...) and any subsequent method calls should be executed within "
									+ "a Context",
							ctxTracker.getCurrentContext()
						);
					} else {
						assertNull(
							"methods invoked before onOpen(...) should be executed outside of any "
									+ "Context",
							ctxTracker.getCurrentContext()
						);
					}
					return method.invoke(testEndpoint, args);
				}
			},
			ctxTracker
		);
		proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
				.set(endpointProxy, endpointProxyHandler);

		final var log = Logger.getLogger(EndpointProxyHandler.class.getName());
		final var levelBackup = log.getLevel();
		log.setLevel(Level.OFF);
		try {
			var ignored = endpointProxy.toString();
		} finally {
			log.setLevel(levelBackup);
		}
		endpointProxy.onOpen(mockConnection, null);
		endpointProxy.onClose(mockConnection, null);
	}



	@Test
	public void testTwoSeparateEndpoints() throws Exception {
		final var endpointProxyHandler = new EndpointProxyHandler(
			configurator.getAdditionalDecorator(testEndpoint),
			ctxTracker
		);
		proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
				.set(endpointProxy, endpointProxyHandler);

		final var secondUserProperties = new HashMap<String, Object>(2);
		secondUserProperties.put(HttpSession.class.getName(), mockHttpSession);
		final Session secondConnection = createMock(Session.class);
		expect(secondConnection.getUserProperties())
			.andReturn(secondUserProperties)
			.anyTimes();
		replay(secondConnection);
		final var secondProxy = proxyClass.getConstructor().newInstance();
		final var secondEndpoint = new TestEndpoint(ctxTracker, secondConnection, mockHttpSession);
		final var secondHandler = new EndpointProxyHandler(
			configurator.getAdditionalDecorator(secondEndpoint),
			ctxTracker
		);
		proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
				.set(secondProxy, secondHandler);

		endpointProxy.onOpen(mockConnection, null);
		secondProxy.onOpen(secondConnection, null);
		assertNotSame("each connection should have a separate WebsocketConnectionContext",
				testEndpoint.connectionCtx, secondEndpoint.connectionCtx);
		assertNotSame("each method invocation should have a separate WebsocketEventContext",
				testEndpoint.openEventCtx, secondEndpoint.openEventCtx);
		verify(secondConnection);
	}
}
