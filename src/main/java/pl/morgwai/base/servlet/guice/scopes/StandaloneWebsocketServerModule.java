// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import com.google.inject.*;
import com.google.inject.Module;

import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator
		.APP_DEPLOYMENT_PATH_KEY;



/**
 * {@link Module} for standalone websocket server apps.
 * Initializes {@link GuiceServerEndpointConfigurator}.
 * <p>
 * Usually a single instance is created at the startup and passed to
 * {@link Guice#createInjector(Module...)} together with a {@link WebsocketModule} and other
 * {@link Module}s.</p>
 */
public class StandaloneWebsocketServerModule implements Module {



	/** Root path of the server app. */
	public final String appDeploymentPath;



	public StandaloneWebsocketServerModule(String appDeploymentPath) {
		this.appDeploymentPath = appDeploymentPath.equals("/") ? "" : appDeploymentPath;
	}



	/**
	 * {@link Binder#bind(Key) Binds}
	 * {@link GuiceServerEndpointConfigurator#APP_DEPLOYMENT_PATH_KEY APP_DEPLOYMENT_PATH_KEY} to
	 * {@link #appDeploymentPath} and stores the resulting {@link Injector} in static structures of
	 * {@link GuiceServerEndpointConfigurator}.
	 * This allows {@link GuiceServerEndpointConfigurator} instances created by the container (for
	 * {@code Endpoint}s annotated with @{@link javax.websocket.server.ServerEndpoint}) to get a
	 * reference to the {@link Injector}.
	 */
	@Override
	public void configure(Binder binder) {
		binder.bind(APP_DEPLOYMENT_PATH_KEY)
			.toInstance(appDeploymentPath);
		binder.requestStaticInjection(GuiceServerEndpointConfigurator.class);
				// calls GuiceServerEndpointConfigurator.registerInjector(...)
	}
}
