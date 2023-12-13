// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.tyrus;

import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.websocket.*;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServletModule;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public class Tyrus implements Server {



	final StandaloneWebsocketContainerServletContext appDeployment;
	final WebsocketPingerService pingerService;
	final org.glassfish.tyrus.server.Server tyrus;



	public Tyrus(int port, String deploymentPath) throws DeploymentException {
		appDeployment  = new StandaloneWebsocketContainerServletContext(deploymentPath);

		final var servletModule = new ServletModule(appDeployment);
		final var modules = new LinkedList<Module>();
		modules.add(servletModule);
		modules.add(new ServiceModule(servletModule, false));
		final Injector injector = Guice.createInjector(modules);

		final var intervalFromProperty = System.getProperty(Server.PING_INTERVAL_MILLIS_PROPERTY);
		pingerService = new WebsocketPingerService(
			intervalFromProperty != null ? Long.parseLong(intervalFromProperty) : 500L,
			TimeUnit.MILLISECONDS,
			WebsocketPingerService.DEFAULT_FAILURE_LIMIT
		);

		appDeployment.setAttribute(Injector.class.getName(), injector);
		appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);
		GuiceServerEndpointConfigurator.registerDeployment(appDeployment);

		tyrus = new org.glassfish.tyrus.server.Server(
			"localhost",
			port,
			deploymentPath,
			null,
			TyrusConfig.class
		);
		tyrus.start();
	}



	@Override
	public int getPort() {
		return tyrus.getPort();
	}



	@Override
	public void stopz() {
		tyrus.stop();
		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
		pingerService.stop();
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
				OnOpenWithoutSessionParamEndpoint.class,
				PingingWithoutOnCloseEndpoint.class
			);
		}
	}
}
