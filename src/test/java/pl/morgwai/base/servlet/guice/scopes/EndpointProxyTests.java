// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;

import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextTracker;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;



public abstract class EndpointProxyTests extends EasyMockSupport {



	/** Test subject. */
	protected Endpoint endpointProxy;

	/** {@link Endpoint} wrapped by {@link #endpointProxy the test subject}. */
	protected TestEndpoint testEndpoint;
	@Mock protected Session mockConnection;
	final Map<String, Object> userProperties = new HashMap<>(2);

	protected final ContextTracker<ContainerCallContext> ctxTracker = new ContextTracker<>();

	@Mock protected HttpSession mockHttpSession;



	@Before
	public final void setup() throws Exception {
		injectMocks(this);
		userProperties.put(HttpSession.class.getName(), mockHttpSession);
		expect(mockConnection.getUserProperties())
			.andReturn(userProperties)
			.anyTimes();

		testEndpoint = new TestEndpoint(ctxTracker, mockConnection, mockHttpSession);
		endpointProxy = createEndpointProxy(testEndpoint, ctxTracker, mockHttpSession);
	}

	protected abstract Endpoint createEndpointProxy(
		Endpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) throws Exception;



	@After
	public final void verifyMocks() {
		verifyAll();
	}



	public static class TestEndpoint extends Endpoint {

		final ContextTracker<ContainerCallContext> ctxTracker;
		final Session mockConnection;
		final HttpSession mockHttpSession;



		public TestEndpoint(
			ContextTracker<ContainerCallContext> ctxTracker,
			Session mockConnection,
			HttpSession mockHttpSession
		) {
			this.ctxTracker = ctxTracker;
			this.mockConnection = mockConnection;
			this.mockHttpSession = mockHttpSession;
		}

		/** Required by {@link ServerEndpointProxyTests} to create proxy instances. */
		public TestEndpoint() {
			this(null, null, null);
		}



		WebsocketEventContext openEventCtx;
		WebsocketConnectionContext connectionCtx;



		/** Verifies that all {@code Context}s have been properly setup by the test subject. */
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



		/**
		 * Verifies that its {@link WebsocketEventContext} has changed while
		 * its {@link WebsocketConnectionContext} and {@link HttpSession} have remained the same as
		 * in {@link #onOpen(Session, EndpointConfig) onOpen(...)}.
		 */
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
		replayAll();

		endpointProxy.onOpen(mockConnection, null);
		endpointProxy.onClose(mockConnection, null);
	}



	@Test
	public void testToStringBeforeOnOpen() throws Exception {
		replayAll();

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
		replayAll();

		// create the second Endpoint, its Proxy and all mocks
		final var secondUserProperties = new HashMap<String, Object>(2);
		secondUserProperties.put(HttpSession.class.getName(), mockHttpSession);
		final Session secondConnection = createMock(Session.class);
		expect(secondConnection.getUserProperties())
			.andReturn(secondUserProperties)
			.anyTimes();
		final var secondEndpoint = new TestEndpoint(ctxTracker, secondConnection, mockHttpSession);
		final var secondProxy = createSecondProxy(secondEndpoint, ctxTracker, mockHttpSession);
		replay(secondConnection);

		// open both Endpoint connections and verify there's no interference
		endpointProxy.onOpen(mockConnection, null);
		secondProxy.onOpen(secondConnection, null);
		assertNotSame("each connection should have a separate WebsocketConnectionContext",
				testEndpoint.connectionCtx, secondEndpoint.connectionCtx);
		assertNotSame("each method invocation should have a separate WebsocketEventContext",
				testEndpoint.openEventCtx, secondEndpoint.openEventCtx);

		// close both Endpoint connections and verify mocks
		endpointProxy.onClose(mockConnection, null);
		secondProxy.onClose(secondConnection, null);
		verify(secondConnection);
	}

	protected abstract Endpoint createSecondProxy(
		Endpoint secondEndpoint,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) throws Exception;
}
