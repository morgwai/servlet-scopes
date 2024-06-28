// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.tyrus;

import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.websocket.*;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServletModule;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public class TyrusServer implements Server {



	final String deploymentPath;
	final org.glassfish.tyrus.server.Server tyrus;



	public TyrusServer(int port, String deploymentPath) throws DeploymentException {
		this(port, deploymentPath, null);
	}



	public TyrusServer(int port, String deploymentPath, ClusterContext clusterCtx)
			throws DeploymentException {
		this.deploymentPath = deploymentPath;
		tyrus = new org.glassfish.tyrus.server.Server(
			"localhost",
			port,
			deploymentPath,
			clusterCtx == null ? null : Map.of(ClusterContext.CLUSTER_CONTEXT, clusterCtx),
			TyrusConfig.class
		);
		tyrus.start();
	}



	@Override
	public String getTestAppWebsocketUrl() {
		return "ws://localhost:" + tyrus.getPort() + deploymentPath;
	}



	@Override
	public void stop() {
		tyrus.stop();
	}



	public static class TyrusConfig implements ServerApplicationConfig {

		final PingingEndpointConfigurator configurator = new PingingEndpointConfigurator();



		@Override
		public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> classes)
		{
			return Set.of(
				ServerEndpointConfig.Builder
					.create(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH)
					.configurator(configurator)
					.build(),
				ServerEndpointConfig.Builder
					.create(RttReportingEndpoint.class, RttReportingEndpoint.PATH)
					.configurator(configurator)
					.build()
			);
		}



		@Override
		public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
			return Set.of(
				AnnotatedEndpoint.class,
				AnnotatedExtendingEndpoint.class,
				AnnotatedMethodOverridingEndpoint.class,
				OnOpenWithoutSessionParamEndpoint.class,
				PingingWithoutOnCloseEndpoint.class,
				AppSeparationTestEndpoint.class,
				NoSessionAppSeparationTestEndpoint.class,
				BroadcastEndpoint.class
			);
		}
	}



	public static StandaloneWebsocketContainerServletContext createDeployment(String path) {
		final var appDeployment = new StandaloneWebsocketContainerServletContext(path);

		// create and store injector
		final var servletModule = new ServletModule(appDeployment);
		final var modules = new LinkedList<Module>();
		modules.add(servletModule);
		modules.add(new ServiceModule(servletModule, false));
		final Injector injector = Guice.createInjector(modules);
		appDeployment.setAttribute(Injector.class.getName(), injector);

		// create and store pingerService
		final var intervalFromProperty = System.getProperty(PING_INTERVAL_MILLIS_PROPERTY);
		final var pingerService = new WebsocketPingerService(
			intervalFromProperty != null
					? Long.parseLong(intervalFromProperty)
					: DEFAULT_PING_INTERVAL_MILLIS,
			TimeUnit.MILLISECONDS,
			1
		);
		appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);

		GuiceServerEndpointConfigurator.registerDeployment(appDeployment);
		return appDeployment;
	}



	public static void cleanupDeployment(StandaloneWebsocketContainerServletContext appDeployment) {
		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
		((WebsocketPingerService)appDeployment.getAttribute(WebsocketPingerService.class.getName()))
				.stop();
	}
}
