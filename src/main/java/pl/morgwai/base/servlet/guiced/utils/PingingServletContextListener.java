// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.utils;

import java.lang.reflect.InvocationHandler;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.scopes.GuiceServletContextListener;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;


/**
 * A {@link GuiceServletContextListener} that automatically register/deregister endpoint instances
 * to a {@link WebsocketPingerService}. Endpoints need to be created with
 * {@link #addEndpoint(Class, String) addEndpoint(Class, String)} or annotated to use
 * {@link PingingEndpointConfigurator}.
 */
public abstract class PingingServletContextListener extends GuiceServletContextListener {



	final WebsocketPingerService pingerService =
			new WebsocketPingerService(getPingIntervalSeconds(), getMaxMalformedPongCount());

	/**
	 * Allows subclasses to override ping interval.
	 */
	protected int getPingIntervalSeconds() { return WebsocketPingerService.DEFAULT_PING_INTERVAL; }

	/**
	 * Allows subclasses to override maximum allowed malformed pongs.
	 */
	protected int getMaxMalformedPongCount() {
		return WebsocketPingerService.DEFAULT_MAX_MALFORMED_PONG_COUNT;
	}



	/**
	 * Stops the associated {@link WebsocketPingerService}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent destructionEvent) {
		pingerService.stop();
		super.contextDestroyed(destructionEvent);
	}



	/**
	 * Adds an endpoint using a {@link PingingEndpointConfigurator}.
	 */
	@Override
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		super.addEndpoint(endpointClass, path, new PingingEndpointConfigurator());
	}



	/**
	 * Automatically registers and deregisters created endpoints to the
	 * {@link WebsocketPingerService} of the {@link PingingServletContextListener}.
	 */
	public static class PingingEndpointConfigurator extends GuiceServerEndpointConfigurator {

		@Override
		protected InvocationHandler getAdditionalDecorator(Object endpoint) {
			return new EndpointPingerDecorator(endpoint, staticPingerService);
		}
	}

	public PingingServletContextListener() {
		// ugly hack as PingingEndpointConfigurator must be static in order to be usable as
		// configurator class in @ServerEndpoint.
		staticPingerService = pingerService;
	}

	static WebsocketPingerService staticPingerService;
}