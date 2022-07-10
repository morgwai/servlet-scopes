// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.utils;

import java.lang.reflect.InvocationHandler;

import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A {@link GuiceServerEndpointConfigurator} that automatically registers and deregisters
 * endpoints to a {@link WebsocketPingerService}. Must be initialized by a call to
 * {@link #setPingerService(WebsocketPingerService)}.
 *
 * @see PingingServletContextListener
 */
public class PingingEndpointConfigurator extends GuiceServerEndpointConfigurator {



	/**
	 * Sets the {@link WebsocketPingerService} to be used. Must be called in {@link
	 * javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)}
	 * or in listener's constructor. This is static for this configurator to be usable in
	 * {@link javax.websocket.server.ServerEndpoint ServerEndpoint} annotations.
	 */
	public static void setPingerService(WebsocketPingerService pingerService) {
		PingingEndpointConfigurator.pingerService = pingerService;
	}
	static WebsocketPingerService pingerService;



	@Override
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return new EndpointPingerDecorator(endpoint, pingerService);
	}
}
