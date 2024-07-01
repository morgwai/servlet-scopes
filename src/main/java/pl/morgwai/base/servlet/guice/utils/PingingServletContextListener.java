// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.concurrent.ScheduledExecutorService;

import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;



/**
 * Subclass of {@link GuiceServletContextListener} that automatically registers its programmatic
 * {@code Endpoints} to its associated {@link WebsocketPingerService}.
 * @see PingingServerEndpointConfigurator
 */
public abstract class PingingServletContextListener extends GuiceServletContextListener {



	/**
	 * The app-wide pinger service to which {@link #addEndpoint(Class, String)} method registers
	 * {@code Endpoints}.
	 * Initialized with the result of {@link #createPingerService()}.
	 * <p>
	 * The app-wide pinger service is also stored as a
	 * {@link javax.servlet.ServletContext#getAttribute(String) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.</p>
	 */
	protected WebsocketPingerService pingerService;



	/**
	 * Allows to override {@link #pingerService}'s mode.
	 * By default {@code false}.
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
		return SECONDS.toMillis(WebsocketPingerService.DEFAULT_INTERVAL_SECONDS);
	}



	/**
	 * Allows to override {@link #pingerService}'s {@code failureLimit} param.
	 * By default {@value #DEFAULT_FAILURE_LIMIT}.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected int getPingFailureLimit() {
		return DEFAULT_FAILURE_LIMIT;
	}

	static final int DEFAULT_FAILURE_LIMIT = 1;



	/**
	 * Allows to override name of a {@link java.security.MessageDigest} to use for ping content
	 * hashing by {@link #pingerService}.
	 * By default {@value WebsocketPingerService#DEFAULT_HASH_FUNCTION}.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected String getHashFunctionName() {
		return WebsocketPingerService.DEFAULT_HASH_FUNCTION;
	}



	/**
	 * Creates a scheduler to be used by {@link #pingerService} for ping scheduling.
	 * By default {@link WebsocketPingerService#newDefaultScheduler()}.
	 * <p>
	 * This method is called by {@link #createPingerService()}, it may use {@link #appDeployment}
	 * and {@link #injector}.</p>
	 */
	protected ScheduledExecutorService createScheduler() {
		return WebsocketPingerService.newDefaultScheduler();
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
	 * Creates {@link #pingerService the app-wide pinger service}.
	 * By default it calls {@link #isPingerInKeepAliveOnlyMode()}, {@link #getPingIntervalMillis()}
	 * and {@link #getPingFailureLimit()}, {@link #getHashFunctionName()},
	 * {@link #createScheduler()} and {@link #shouldSynchronizePingSending()} to configure the
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
				MILLISECONDS,
				getHashFunctionName(),
				createScheduler(),
				shouldSynchronizePingSending()
			);
		} else {
			return new WebsocketPingerService(
				getPingIntervalMillis(),
				MILLISECONDS,
				getPingFailureLimit(),
				getHashFunctionName(),
				createScheduler(),
				shouldSynchronizePingSending()
			);
		}
	}



	/**
	 * Overrides {@link #endpointConfigurator} to be a {@link PingingServerEndpointConfigurator}.
	 * Also {@link #createPingerService() creates the app-wide pinger service} and stores it as a
	 * {@link javax.servlet.ServletContext#setAttribute(String, Object) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.
	 */
	@Override
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		pingerService = createPingerService();
		addShutdownHook(pingerService::stop);
		appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);
		return new PingingServerEndpointConfigurator(appDeployment);
	}
}
