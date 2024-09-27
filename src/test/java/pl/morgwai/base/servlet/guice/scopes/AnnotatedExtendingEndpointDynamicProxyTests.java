// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpSession;
import javax.websocket.*;

import pl.morgwai.base.guice.scopes.ContextTracker;



public class AnnotatedExtendingEndpointDynamicProxyTests extends EndpointDynamicProxyTests {



	@Override
	protected TestEndpoint createEndpoint(
		ContextTracker<ContainerCallContext> ctxTracker,
		Session mockConnection,
		HttpSession mockHttpSession
	) {
		return new AnnotatedExtendingTestEndpoint(ctxTracker, mockConnection, mockHttpSession);
	}



	@ClientEndpoint
	public static class AnnotatedExtendingTestEndpoint extends AnnotatedTestEndpoint {



		public AnnotatedExtendingTestEndpoint(
			ContextTracker<ContainerCallContext> ctxTracker,
			Session mockConnection,
			HttpSession mockHttpSession
		) {
			super(ctxTracker, mockConnection, mockHttpSession);
		}

		public AnnotatedExtendingTestEndpoint() {}



		@Override public void onOpen(Session connectionProxy, EndpointConfig config) {
			super.onOpen(connectionProxy, config);
		}
	}
}
