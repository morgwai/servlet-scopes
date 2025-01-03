// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.tyrus;

import java.util.*;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import com.google.inject.Module;
import com.google.inject.*;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.guice.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;
import pl.morgwai.base.utils.concurrent.TaskTrackingThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static pl.morgwai.base.utils.concurrent.Awaitable.awaitMultiple;



public class TyrusServer implements Server {



	final String deploymentPath;
	final WebsocketPingerService pingerService;
	final org.glassfish.tyrus.server.Server tyrus;
	final TaskTrackingThreadPoolExecutor executor;
	final Injector injector;



	public TyrusServer(int port, String deploymentPath, ClusterContext clusterCtx)
			throws DeploymentException {
		this.deploymentPath = deploymentPath.equals("/") ? "": deploymentPath;
		final var intervalFromProperty = System.getProperty(PING_INTERVAL_MILLIS_PROPERTY);
		pingerService = new WebsocketPingerService(
			intervalFromProperty != null
					? Long.parseLong(intervalFromProperty)
					: DEFAULT_PING_INTERVAL_MILLIS,
			MILLISECONDS,
			1
		);
		final var websocketModule = new PingingWebsocketModule(pingerService, true);
		final var websocketServerModule = new StandaloneWebsocketServerModule(deploymentPath);
		executor = new TaskTrackingThreadPoolExecutor(2, new NamingThreadFactory("testExecutor"));

		// create and store injector
		final var modules = new LinkedList<Module>();
		modules.add(websocketModule);
		modules.add(websocketServerModule);
		modules.add(new ServiceModule(
			websocketModule.containerCallScope,
			websocketModule.websocketConnectionScope,
			null,  // no HTTP session support
			ContextTrackingExecutor.of(executor, websocketModule.ctxBinder)
		));
		injector = Guice.createInjector(modules);

		tyrus = new org.glassfish.tyrus.server.Server(
			"localhost",
			port,
			this.deploymentPath,
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
		executor.shutdown();
		pingerService.shutdown();
		GuiceServerEndpointConfigurator.deregisterInjector(injector);
		try {
			awaitMultiple(
				5L, SECONDS,
				executor.toAwaitableOfEnforcedTermination(),
				pingerService::tryEnforceTermination
			);
		} catch (InterruptedException ignored) {}
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
