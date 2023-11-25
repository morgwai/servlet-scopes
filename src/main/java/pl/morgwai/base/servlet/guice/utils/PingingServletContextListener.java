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
	protected WebsocketPingerService pingerService;



	/**
	 * Allows to override {@link #pingerService}'s mode. By default {@code false}.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected boolean isPingerInKeepAliveOnlyMode() {
		return false;
	}



	/**
	 * Allows to override {@link #pingerService}'s {@code interval} param.
	 * By default {@link WebsocketPingerService#DEFAULT_INTERVAL_SECONDS} converted to millis.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected long getPingIntervalMillis() {
		return WebsocketPingerService.DEFAULT_INTERVAL_SECONDS * 1000L;
	}



	/**
	 * Allows to override {@link #pingerService}'s {@code failureLimit} param.
	 * By default {@link WebsocketPingerService#DEFAULT_FAILURE_LIMIT}.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected int getPingFailureLimit() {
		return WebsocketPingerService.DEFAULT_FAILURE_LIMIT;
	}



	/**
	 * Allows to override {@link #pingerService}'s {@code synchronizeSending} flag.
	 * By default {@code false}.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected boolean shouldSynchronizePingSending() {
		return false;
	}



	/**
	 * Creates {@link #pingerService the app-wide pinger service}. By default it calls
	 * {@link #isPingerInKeepAliveOnlyMode()}, {@link #getPingIntervalMillis()} and
	 * {@link #getPingFailureLimit()} and {@link #shouldSynchronizePingSending()} to configure the
	 * service.
	 * <p>
	 * This method is called once in a {@link #createEndpointConfigurator()} and may be overridden
	 * if other customizations are required. It may use {@link #appDeployment} and
	 * {@link #injector}.</p>
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
	 * Overrides {@link #endpointConfigurator} to be a {@link PingingEndpointConfigurator}.
	 * Also {@link #createPingerService() creates the app-wide pinger service} and stores it as a
	 * {@link javax.servlet.ServletContext#setAttribute(String, Object) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.
	 */
	@Override
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		pingerService = createPingerService();
		addShutdownHook(pingerService::stop);
		appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);
		return new PingingEndpointConfigurator(appDeployment);
	}
}
