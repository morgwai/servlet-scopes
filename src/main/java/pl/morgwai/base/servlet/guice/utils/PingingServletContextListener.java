// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.concurrent.TimeUnit;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.GuiceServletContextListener;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * Subclass of {@link GuiceServletContextListener} that automatically registers and deregisters
 * {@code Endpoints} added with {@link #addEndpoint(Class, String)} to the associated
 * {@link WebsocketPingerService}.
 */
public abstract class PingingServletContextListener extends GuiceServletContextListener {



	/**
	 * The app-wide pinger service to which {@link #addEndpoint(Class, String)} method registers
	 * {@code Endpoints}. Initialized with the result of {@link #createPingerService()}.
	 * <p>
	 * The app-wide pinger service is also stored as a
	 * {@link javax.servlet.ServletContext#getAttribute(String) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.</p>
	 */
	protected final WebsocketPingerService pingerService;



	/** Allows to override {@link #pingerService}'s mode. By default {@code false}. */
	protected boolean isPingerInKeepAliveOnlyMode() {
		return false;
	}

	/**
	 * Allows to override {@link #pingerService}'s {@code interval} param.
	 * By default {@link WebsocketPingerService#DEFAULT_INTERVAL_SECONDS} converted to millis.
	 */
	protected long getPingIntervalMillis() {
		return WebsocketPingerService.DEFAULT_INTERVAL_SECONDS * 1000L;
	}

	/**
	 * Allows to override {@link #pingerService}'s {@code failureLimit} param.
	 * By default {@link WebsocketPingerService#DEFAULT_FAILURE_LIMIT}.
	 */
	protected int getPingFailureLimit() {
		return WebsocketPingerService.DEFAULT_FAILURE_LIMIT;
	}

	/**
	 * Allows to override {@link #pingerService}'s {@code synchronizeSending} flag.
	 * By default {@code false}.
	 */
	protected boolean shouldSynchronizePingSending() {
		return false;
	}

	/**
	 * Creates {@link #pingerService the app-wide pinger service}. This method is called once in
	 * {@link #PingingServletContextListener() the constructor}. By default it calls
	 * {@link #isPingerInKeepAliveOnlyMode()}, {@link #getPingIntervalMillis()} and
	 * {@link #getPingFailureLimit()} to configure the service. May be overridden if other
	 * customizations are required.</p>
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



	/**
	 * {@link #createPingerService() Creates the app-wide pinger service} and
	 * {@link #addShutdownHook(Runnable) schedules} its {@link WebsocketPingerService#stop() stop}
	 * during the app shutdown.
	 */
	public PingingServletContextListener() {
		pingerService = createPingerService();
		addShutdownHook(pingerService::stop);
	}



	/**
	 * Overrides {@link javax.websocket.server.ServerEndpointConfig.Configurator} used by
	 * {@link #addEndpoint(Class, String)} to be a {@link PingingEndpointConfigurator}. Also stores
	 * {@link #pingerService} as a
	 * {@link javax.servlet.ServletContext#setAttribute(String, Object) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.
	 */
	@Override
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);
		return new PingingEndpointConfigurator(appDeployment);
	}
}
