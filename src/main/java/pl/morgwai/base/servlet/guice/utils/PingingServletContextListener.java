// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.LinkedList;

import javax.servlet.ServletContextEvent;

import com.google.inject.Injector;
import com.google.inject.Module;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.GuiceServletContextListener;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A {@link GuiceServletContextListener} that automatically registers and deregisters endpoints
 * to a {@link WebsocketPingerService}. Endpoints need to be created with
 * {@link #addEndpoint(Class, String) addEndpoint(Class, String)} or annotated to use
 * {@link PingingEndpointConfigurator}.
 */
public abstract class PingingServletContextListener extends GuiceServletContextListener {



	protected final WebsocketPingerService pingerService;



	/** Allows subclasses to override pinger mode. By default {@code false}. */
	protected boolean isPingerInKeepAliveOnlyMode() { return false; }

	/**
	 * Allows subclasses to override ping interval.
	 * By default {@link WebsocketPingerService#DEFAULT_INTERVAL}.
	 */
	protected int getPingIntervalSeconds() { return WebsocketPingerService.DEFAULT_INTERVAL; }

	/**
	 * Allows subclasses to override ping failure limit.
	 * By default {@link WebsocketPingerService#DEFAULT_FAILURE_LIMIT}.
	 */
	protected int getPingFailureLimit() { return WebsocketPingerService.DEFAULT_FAILURE_LIMIT; }

	/**
	 * Allows subclasses to override ping data size.
	 * By default {@link WebsocketPingerService#DEFAULT_PING_SIZE}.
	 */
	protected int getPingSize() { return WebsocketPingerService.DEFAULT_PING_SIZE; }

	/** Allows subclasses to override {@code synchronizeSending} flag. By default {@code false}. */
	protected boolean shouldSynchronizePingSending() { return false; }

	/**
	 * Creates a {@link WebsocketPingerService}. Used in constructor to initialize
	 * {@link #pingerService}. By default uses {@link #isPingerInKeepAliveOnlyMode()},
	 * {@link #getPingIntervalSeconds()}, {@link #getPingFailureLimit()} and {@link #getPingSize()}
	 * to configure the returned service. May be overridden if non-standard customizations are
	 * required.
	 */
	protected WebsocketPingerService createPingerService() {
		if (isPingerInKeepAliveOnlyMode()) {
			return new WebsocketPingerService(
					getPingIntervalSeconds(), shouldSynchronizePingSending());
		} else {
			return new WebsocketPingerService(
				getPingIntervalSeconds(),
				getPingFailureLimit(),
				getPingSize(),
				shouldSynchronizePingSending()
			);
		}
	}



	public PingingServletContextListener() {
		pingerService = createPingerService();
	}



	@Override
	protected Injector createInjector(LinkedList<Module> modules) {
		servletContainer.setAttribute(WebsocketPingerService.class.getName(), pingerService);
		PingingEndpointConfigurator.registerPingerService(pingerService, servletContainer);
		return super.createInjector(modules);
	}



	/**
	 * Overrides default configurator used by {@link #addEndpoint(Class, String)} to be a
	 * {@link PingingEndpointConfigurator}.
	 */
	@Override
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		return new PingingEndpointConfigurator(
				injector ,containerCallContextTracker, pingerService);
	}



	/** Stops the associated {@link WebsocketPingerService}. */
	@Override
	public void contextDestroyed(ServletContextEvent destruction) {
		PingingEndpointConfigurator.deregisterPingerService(servletContainer);
		pingerService.stop();
		super.contextDestroyed(destruction);
	}
}
