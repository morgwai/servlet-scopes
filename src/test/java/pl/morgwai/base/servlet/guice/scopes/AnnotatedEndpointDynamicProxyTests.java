// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpSession;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;



public class AnnotatedEndpointDynamicProxyTests extends EndpointDynamicProxyTests {



	@Override
	protected TestEndpoint createEndpoint(
		ContextTracker<ContainerCallContext> ctxTracker,
		Session mockConnection,
		HttpSession mockHttpSession
	) {
		return new AnnotatedTestEndpoint(ctxTracker, mockConnection, mockHttpSession);
	}
}
