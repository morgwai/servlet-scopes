// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import org.junit.After;
import org.junit.Before;
import org.easymock.Mock;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServerEndpointProxyTests;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public class PingingServerEndpointProxyTests extends ServerEndpointProxyTests {



	@Mock WebsocketPingerService mockPingerService;
	MockPingerServiceUtil mockPingerServiceUtil;



	@Override
	protected GuiceServerEndpointConfigurator createConfigurator() {
		return new PingingEndpointConfigurator();
	}



	@Before
	public void setupPingingMocks() {
		mockPingerServiceUtil = new MockPingerServiceUtil(mockPingerService, mockConnection);
		((PingingEndpointConfigurator) configurator).pingerService = mockPingerService;
	}



	@After
	public void verifyConnectionRegistration() {
		mockPingerServiceUtil.verifyConnectionRegistration();
	}
}
