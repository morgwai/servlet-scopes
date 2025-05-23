// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.Session;
import org.junit.After;
import org.junit.Before;
import org.easymock.Mock;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public class PingingClientEndpointProxyTests extends EndpointProxyTests {



	@Mock WebsocketPingerService mockPingerService;
	MockPingerServiceUtil mockPingerServiceUtil;



	@Before
	public void setupPingingMocks() {
		mockPingerServiceUtil = new MockPingerServiceUtil(mockPingerService, mockConnection);
	}



	@After
	public void verifyConnectionRegistration() {
		mockPingerServiceUtil.verifyConnectionRegistration();
	}



	public static class PingingTestEndpointProxy extends PingingClientEndpointProxy
			implements TestEndpoint {

		final ProgrammaticTestEndpoint wrappedEndpoint;



		public PingingTestEndpointProxy(
			ProgrammaticTestEndpoint toWrap,
			ContextTracker<ContainerCallContext> ctxTracker,
			WebsocketPingerService pingerService,
			HttpSession httpSession
		) {
			super(pingerService, toWrap, ctxTracker, null, httpSession);
			this.wrappedEndpoint = toWrap;
		}



		@Override
		public WebsocketEventContext getOpenEventCtx() {
			return wrappedEndpoint.getOpenEventCtx();
		}



		@Override
		public WebsocketConnectionContext getConnectionCtx() {
			return wrappedEndpoint.getConnectionCtx();
		}
	}



	@Override
	protected TestEndpoint createEndpoint(
		ContextTracker<ContainerCallContext> ctxTracker,
		Session mockConnection,
		HttpSession mockHttpSession
	) {
		return new ProgrammaticTestEndpoint(ctxTracker, mockConnection, mockHttpSession);
	}



	@Override
	protected TestEndpoint createEndpointProxy(
		TestEndpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		return new PingingTestEndpointProxy(
			(ProgrammaticTestEndpoint) toWrap,
			ctxTracker,
			mockPingerService,
			httpSession
		);
	}



	@Override
	protected TestEndpoint createSecondProxy(
		TestEndpoint secondEndpoint,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		// Not using PingingTestEndpointProxy to not confuse mockPingerService with a 2nd connection
		return new ClientEndpointProxyTests.TestEndpointProxy(
			(ProgrammaticTestEndpoint) secondEndpoint,
			ctxTracker,
			httpSession
		);
	}
}
