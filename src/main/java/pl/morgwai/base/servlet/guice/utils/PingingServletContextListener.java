// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.concurrent.TimeUnit;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.GuiceServletContextListener;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A {@link GuiceServletContextListener} that automatically registers and deregisters
 * {@code Endpoints} added with {@link #addEndpoint(Class, String)} to its associated
 * {@link WebsocketPingerService}.
 */
public abstract class PingingServletContextListener extends GuiceServletContextListener {



	protected final WebsocketPingerService pingerService;



	/** Allows subclasses to override pinger mode. By default {@code false}. */
	protected boolean isPingerInKeepAliveOnlyMode() {
		return false;
	}

	/**
	 * Allows subclasses to override ping interval.
	 * By default {@link WebsocketPingerService#DEFAULT_INTERVAL_SECONDS} converted to millis.
	 */
	protected long getPingIntervalMillis() {
		return WebsocketPingerService.DEFAULT_INTERVAL_SECONDS * 1000L;
	}

	/**
	 * Allows subclasses to override ping failure limit.
	 * By default {@link WebsocketPingerService#DEFAULT_FAILURE_LIMIT}.
	 */
	protected int getPingFailureLimit() {
		return WebsocketPingerService.DEFAULT_FAILURE_LIMIT;
	}

	/** Allows subclasses to override {@code synchronizeSending} flag. By default {@code false}. */
	protected boolean shouldSynchronizePingSending() {
		return false;
	}

	/**
	 * Creates a {@link WebsocketPingerService}. Used in constructor to initialize
	 * {@link #pingerService}. By default uses {@link #isPingerInKeepAliveOnlyMode()},
	 * {@link #getPingIntervalMillis()} and {@link #getPingFailureLimit()} to configure the
	 * returned service. May be overridden if non-standard customizations are required.
	 */
	protected WebsocketPingerService createPingerService() {
		if (isPingerInKeepAliveOnlyMode()) {
			return new WebsocketPingerService(
				getPingIntervalMillis(),
				TimeUnit.MILLISECONDS,
				shouldSynchronizePingSending()
			);
		} else {
			return new WebsocketPingerService(
				getPingIntervalMillis(),
				TimeUnit.MILLISECONDS,
				getPingFailureLimit(),
				shouldSynchronizePingSending()
			);
		}
	}



	public PingingServletContextListener() {
		pingerService = createPingerService();
		addShutdownHook(this::stopPingerService);
	}



	/**
	 * Overrides default configurator used by {@link #addEndpoint(Class, String)} to be a
	 * {@link PingingEndpointConfigurator}. Stores {@link #pingerService} as a
	 * {@link jakarta.servlet.ServletContext#setAttribute(String, Object) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.
	 */
	@Override
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);
		return new PingingEndpointConfigurator(appDeployment);
	}



	void stopPingerService() {
		pingerService.stop();
	}
}
