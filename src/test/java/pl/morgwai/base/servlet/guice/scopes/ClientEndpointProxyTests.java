// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpSession;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;



public class ClientEndpointProxyTests extends EndpointProxyTests {



	public static class TestEndpointProxy extends ClientEndpointProxy implements TestEndpoint {

		final ProgrammaticTestEndpoint wrappedEndpoint;



		public TestEndpointProxy(
			ProgrammaticTestEndpoint toWrap,
			ContextTracker<ContainerCallContext> ctxTracker,
			HttpSession httpSession
		) {
			super(toWrap, ctxTracker, null, httpSession);
			this.wrappedEndpoint = toWrap;
		}



		@Override public WebsocketEventContext getOpenEventCtx() {
			return wrappedEndpoint.getOpenEventCtx();
		}

		@Override public WebsocketConnectionContext getConnectionCtx() {
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
		return new TestEndpointProxy((ProgrammaticTestEndpoint) toWrap, ctxTracker, httpSession);
	}



	@Override
	protected TestEndpoint createSecondProxy(
		TestEndpoint secondEndpoint,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		return new TestEndpointProxy(
			(ProgrammaticTestEndpoint) secondEndpoint,
			ctxTracker,
			httpSession
		);
	}
}
