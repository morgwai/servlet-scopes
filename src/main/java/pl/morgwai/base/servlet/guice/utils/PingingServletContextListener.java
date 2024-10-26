// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import javax.servlet.ServletContext;

import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;



/** {@link GuiceServletContextListener} that uses {@link PingingServerEndpointConfigurator}. */
public abstract class PingingServletContextListener extends GuiceServletContextListener {



	/**
	 * The app-wide {@link WebsocketPingerService} to which
	 * {@link PingingServerEndpointConfigurator} registers {@code Endpoints}.
	 * Initialized by {@link #createWebsocketModule(boolean, Set)} with the result of
	 * {@link #createPingerService()}.
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
	 * Called to creates {@link #pingerService the app-wide PingerService}.
	 * By default it calls {@link #isPingerInKeepAliveOnlyMode()}, {@link #getPingIntervalMillis()}
	 * and {@link #getPingFailureLimit()}, {@link #getHashFunctionName()},
	 * {@link #createScheduler()} and {@link #shouldSynchronizePingSending()} to configure the
	 * {@link WebsocketPingerService}.
	 * <p>
	 * This method is called once in {@link #createWebsocketModule(boolean, Set)} and may be
	 * overridden if further customizations are required.</p>
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
	 * Calls {@link #createPingerService()} to create
	 * {@link #pingerService the app-wide PingerService} and returns a new
	 * {@link PingingWebsocketModule}.
	 */
	@Override
	protected PingingWebsocketModule createWebsocketModule(
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		pingerService = createPingerService();
		addShutdownHook(() -> {
			if ( !pingerService.tryEnforceTermination()) {
				log.warning(deploymentName + ": pingerService failed to shutdown cleanly");
			}
		});
		return new PingingWebsocketModule(
			pingerService,
			requireTopLevelMethodAnnotations,
			clientEndpointClasses
		);
	}

	static final Logger log = Logger.getLogger(PingingServletContextListener.class.getName());



	/** Overrides {@link #endpointConfigurator} to be a {@link PingingServerEndpointConfigurator}.*/
	@Override
	protected PingingServerEndpointConfigurator createEndpointConfigurator(
			ServletContext appDeployment) {
		return new PingingServerEndpointConfigurator(appDeployment);
	}
}
