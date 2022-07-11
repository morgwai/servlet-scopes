// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.utils;

import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnClose;
import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnOpen;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.websocket.Session;

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
	 * or in listener's constructor. This must be static for this configurator to be usable in
	 * {@link javax.websocket.server.ServerEndpoint ServerEndpoint} annotations.
	 */
	public static void setPingerService(WebsocketPingerService pingerService) {
		PingingEndpointConfigurator.pingerService = pingerService;
	}
	static WebsocketPingerService pingerService;



	@Override
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return new EndpointDecorator(endpoint);
	}



	static class EndpointDecorator implements InvocationHandler {

		final Object endpoint;



		EndpointDecorator(Object endpoint) {
			this.endpoint = endpoint;
		}



		Session connection;

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (isOnOpen(method)) {
				for (var arg: args) {
					if (arg instanceof Session) {
						connection = (Session) arg;
						break;
					}
				}
				pingerService.addConnection(connection);
			}
			if (isOnClose(method)) {
				pingerService.removeConnection(connection);
			}
			return method.invoke(endpoint, args);
		}
	}
}
