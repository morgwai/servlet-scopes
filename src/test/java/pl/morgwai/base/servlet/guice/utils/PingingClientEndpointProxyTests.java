// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import javax.servlet.http.HttpSession;
import javax.websocket.Endpoint;

import org.easymock.Mock;
import org.junit.After;
import org.junit.Before;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public class PingingClientEndpointProxyTests extends ClientEndpointProxyTests {



	@Mock WebsocketPingerService mockPingerService;
	MockPingerServiceUtil mockPingerServiceUtil;



	@Before
	public void setupPingingMocks() {
		mockPingerServiceUtil = new MockPingerServiceUtil(mockPingerService, mockConnection);
	}



	@Override
	protected Endpoint createEndpointProxy(
		Endpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		return new PingingClientEndpointProxy(
			toWrap,
			ctxTracker,
			mockPingerService,
			httpSession
		);
	}



	@After
	public void verifyConnectionRegistration() {
		mockPingerServiceUtil.verifyConnectionRegistration();
	}
}
