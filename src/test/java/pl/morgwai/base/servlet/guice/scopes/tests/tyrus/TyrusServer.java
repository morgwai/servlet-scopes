// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.tyrus;

import java.util.*;
import java.util.stream.Collectors;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import com.google.inject.Module;
import com.google.inject.*;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static java.util.concurrent.TimeUnit.MILLISECONDS;



public class TyrusServer implements Server {



	final String deploymentPath;
	final StandaloneWebsocketContainerServletContext appDeployment;
	final ExecutorManager executorManager;
	final WebsocketPingerService pingerService;
	final org.glassfish.tyrus.server.Server tyrus;



	public TyrusServer(int port, String deploymentPath, ClusterContext clusterCtx)
			throws DeploymentException {
		this.deploymentPath = deploymentPath;
		appDeployment = new StandaloneWebsocketContainerServletContext(deploymentPath);
		final var intervalFromProperty = System.getProperty(PING_INTERVAL_MILLIS_PROPERTY);
		pingerService = new WebsocketPingerService(
			intervalFromProperty != null
				? Long.parseLong(intervalFromProperty)
				: DEFAULT_PING_INTERVAL_MILLIS,
			MILLISECONDS,
			1
		);
		final var servletModule = new ServletModule(
			appDeployment,
			new PingingWebsocketModule(pingerService)
		);
		executorManager = new ExecutorManager(servletModule.ctxBinder);

		// create and store injector
		final var modules = new LinkedList<Module>();
		modules.add(servletModule);
		modules.add(new ServiceModule(servletModule, executorManager, false));
		final Injector injector = Guice.createInjector(modules);

		tyrus = new org.glassfish.tyrus.server.Server(
			"localhost",
			port,
			deploymentPath,
			clusterCtx == null ? null : Map.of(ClusterContext.CLUSTER_CONTEXT, clusterCtx),
			TyrusConfig.class
		);
		tyrus.start();
	}

	public TyrusServer(int port, String deploymentPath) throws DeploymentException {
		this(port, deploymentPath, null);
	}



	@Override
	public String getTestAppWebsocketUrl() {
		return "ws://localhost:" + tyrus.getPort() + deploymentPath;
	}



	@Override
	public void stop() {
		tyrus.stop();
		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
		pingerService.stop();
		executorManager.shutdownAllExecutors();
		List<ServletContextTrackingExecutor> unterminated;
		try {
			unterminated = executorManager.awaitTerminationOfAllExecutors(100L, MILLISECONDS);
		} catch (InterruptedException e) {
			unterminated = executorManager.getExecutors().stream()
				.filter((executor) -> !executor.isTerminated())
				.collect(Collectors.toList());
		}
		for (var executor: unterminated) executor.shutdownNow();
	}



	public static class TyrusConfig implements ServerApplicationConfig {

		final PingingServerEndpointConfigurator configurator =
				new PingingServerEndpointConfigurator();



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
				TyrusAnnotatedMethodOverridingEndpoint.class,
				OnOpenWithoutSessionParamEndpoint.class,
				PingingWithoutOnCloseEndpoint.class,
				BroadcastEndpoint.class
			);
		}
	}
}
