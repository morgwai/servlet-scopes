// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.utils;

import java.lang.reflect.InvocationHandler;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;

import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.scopes.GuiceServletContextListener;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A {@link GuiceServletContextListener} that creates 1 instance of {@link WebsocketPingerService}
 * and decorates endpoint instances created with {@link #addEndpoint(Class, String)} to
 * automatically register/de-register themselves to it.
 * <p>
 * <b>NOTE:</b> in case of a huge number of websocket connections, 1 pingerService instance may not
 * be sufficient. A more complex strategy that creates more pingers should be implemented in such
 * case.</p>
 */
public abstract class SimplePingingEndpointServletContextListener
		extends GuiceServletContextListener {



	final WebsocketPingerService pingerService =
			new WebsocketPingerService(getPingIntervalSeconds(), getMaxMalformedPongCount());

	protected int getPingIntervalSeconds() { return WebsocketPingerService.DEFAULT_PING_INTERVAL; }

	protected int getMaxMalformedPongCount() {
		return WebsocketPingerService.DEFAULT_MAX_MALFORMED_PONG_COUNT;
	}



	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		pingerService.stop();
		super.contextDestroyed(sce);
	}



	@Override
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		super.addEndpoint(endpointClass, path, new SimplePingingEndpointConfigurator());
	}



	public class SimplePingingEndpointConfigurator extends GuiceServerEndpointConfigurator {

		@Override
		protected InvocationHandler getAdditionalDecorator(Object endpoint) {
			return new EndpointPingerDecorator(endpoint, pingerService);
		}
	}
}
