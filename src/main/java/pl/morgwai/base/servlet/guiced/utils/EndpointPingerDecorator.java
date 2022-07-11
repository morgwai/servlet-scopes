// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.utils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.websocket.Session;

import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static pl.morgwai.base.servlet.utils.EndpointUtils.*;



/**
 * Decorator for websocket {@link javax.websocket.Endpoint}s that automatically registers and
 * deregisters them to {@link WebsocketPingerService}.
 * For use with {@link
 * pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator#getAdditionalDecorator(Object)}.
 *
 * @see PingingEndpointConfigurator
 * @see PingingServletContextListener
 */
public class EndpointPingerDecorator implements InvocationHandler {



	final Object endpoint;
	final WebsocketPingerService pingerService;

	public EndpointPingerDecorator(Object endpoint, WebsocketPingerService pingerService) {
		this.endpoint = endpoint;
		this.pingerService = pingerService;
	}



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

	Session connection;
}
