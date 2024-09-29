// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import org.junit.After;
import org.easymock.Mock;

import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public class AnnotatedPingingEndpointDynamicProxyTests extends AnnotatedEndpointDynamicProxyTests {



	@Mock WebsocketPingerService mockPingerService;
	MockPingerServiceUtil mockPingerServiceUtil;



	@Override
	public void additionalSetup() {
		mockPingerServiceUtil = new MockPingerServiceUtil(mockPingerService, mockConnection);
		configurator = new PingingEndpointConfigurator(null, ctxTracker, false, mockPingerService);
	}



	@After
	public void verifyConnectionRegistration() {
		mockPingerServiceUtil.verifyConnectionRegistration();
	}
}
