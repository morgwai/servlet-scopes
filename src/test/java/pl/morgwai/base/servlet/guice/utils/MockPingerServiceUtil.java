// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import javax.websocket.Session;

import org.easymock.Capture;
import pl.morgwai.base.servlet.guice.scopes.WebsocketConnectionProxy;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertSame;



/**
 * Setups expectation on its {@link WebsocketPingerService} mock and verifies if the expected
 * connection was registered and deregistered correctly.
 */
public class MockPingerServiceUtil {



	final WebsocketPingerService mockPingerService;
	final Session expectedConnection;
	final Capture<WebsocketConnectionProxy> registeredProxyCapture = Capture.newInstance();
	final Capture<WebsocketConnectionProxy> deregisteredProxyCapture = Capture.newInstance();



	public MockPingerServiceUtil(
		WebsocketPingerService mockPingerService,
		Session expectedConnection
	) {
		this.mockPingerService = mockPingerService;
		this.expectedConnection = expectedConnection;
		mockPingerService.addConnection(capture(registeredProxyCapture));
		expectLastCall()
			.times(1);
		expect(mockPingerService.removeConnection(capture(deregisteredProxyCapture)))
			.andReturn(true)
			.times(1);
	}



	public void verifyConnectionRegistration() {
		assertSame("registered WebsocketConnectionProxy should be wrapping expectedConnection",
				expectedConnection, registeredProxyCapture.getValue().getWrappedConnection());
		assertSame("registered WebsocketConnectionProxy should be deregistered at the end",
				registeredProxyCapture.getValue(), deregisteredProxyCapture.getValue());
	}
}
