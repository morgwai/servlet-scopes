// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;



public class ProgrammaticEndpointDynamicProxyTests extends EndpointDynamicProxyTests {



	@Override
	protected TestEndpoint createEndpoint(
		ContextTracker<ContainerCallContext> ctxTracker,
		Session mockConnection,
		HttpSession mockHttpSession
	) {
		return new ProgrammaticTestEndpoint(ctxTracker, mockConnection, mockHttpSession);
	}
}
