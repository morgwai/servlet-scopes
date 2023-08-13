// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashSet;

import javax.websocket.OnClose;
import javax.websocket.Session;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnClose;
import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnOpen;



/**
 * A {@link GuiceServerEndpointConfigurator} that automatically registers and deregisters
 * endpoints to the {@link WebsocketPingerService} set with
 * {@link #setPingerService(WebsocketPingerService)}.
 *
 * @see PingingServletContextListener
 */
public class PingingEndpointConfigurator extends GuiceServerEndpointConfigurator {



	// must be static for this configurator to be usable in @ServerEndpoint annotations
	static WebsocketPingerService pingerService;



	/**
	 * Sets the {@link WebsocketPingerService} to be used. Must be called in {@link
	 * javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)}
	 * or in listener's constructor.
	 */
	public static void setPingerService(WebsocketPingerService pingerService) {
		PingingEndpointConfigurator.pingerService = pingerService;
	}



	@Override
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return new EndpointDecorator(endpoint);
	}



	@Override
	protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
		final var result = super.getRequiredEndpointMethodAnnotationTypes();
		result.add(OnClose.class);
		return result;
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
			} else if (isOnClose(method)) {
				pingerService.removeConnection(connection);
			}
			return method.invoke(endpoint, args);
		}
	}
}
