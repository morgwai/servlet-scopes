// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpSession;
import javax.websocket.Endpoint;

import pl.morgwai.base.guice.scopes.ContextTracker;



public class ClientEndpointProxyTests extends EndpointProxyTests {



	@Override
	protected Endpoint createEndpointProxy(
		Endpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		return new ClientEndpointProxy(toWrap, ctxTracker, httpSession);
	}



	/**
	 * This method is final to ensure that
	 * {@link pl.morgwai.base.servlet.guice.utils.PingingClientEndpointProxyTests} also uses just
	 * {@link ClientEndpointProxy} not to confuse mockPingerService with a 2nd connection.
	 */
	@Override
	protected final Endpoint createSecondProxy(
		Endpoint secondEndpoint,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		return new ClientEndpointProxy(secondEndpoint, ctxTracker, mockHttpSession);
	}
}
