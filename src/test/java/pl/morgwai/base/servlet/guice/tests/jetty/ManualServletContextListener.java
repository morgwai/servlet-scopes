// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.util.*;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.guice.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.PingingServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.utils.PingingWebsocketModule;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;
import pl.morgwai.base.utils.concurrent.TaskTrackingThreadPoolExecutor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static pl.morgwai.base.servlet.guice.tests.servercommon.Server.*;
import static pl.morgwai.base.utils.concurrent.Awaitable.awaitMultiple;



/**
 * A listener that does not extend {@link GuiceServletContextListener} and does exactly the same job
 * as {@link pl.morgwai.base.servlet.guice.tests.jetty.ServletContextListener} from this package
 * (that extends {@link pl.morgwai.base.servlet.guice.utils.PingingServletContextListener}).
 */
public class ManualServletContextListener implements ServletContextListener {



	ServletContext appDeployment;
	WebsocketPingerService pingerService;
	TaskTrackingThreadPoolExecutor executor;
	Injector injector;



	ServletRegistration.Dynamic addServlet(
		String name,
		Class<? extends HttpServlet> servletClass,
		String... urlPatterns
	) throws ServletException {
		final var servlet = appDeployment.createServlet(servletClass);
		injector.injectMembers(servlet);
		final var registration = appDeployment.addServlet(name, servlet);
		registration.addMapping(urlPatterns);
		registration.setAsyncSupported(true);
		return registration;
	}



	@Override
	public final void contextInitialized(ServletContextEvent initialization) {
		try {
			final var intervalFromProperty = System.getProperty(PING_INTERVAL_MILLIS_PROPERTY);
			pingerService = new WebsocketPingerService(
				intervalFromProperty != null
					? Long.parseLong(intervalFromProperty)
					: DEFAULT_PING_INTERVAL_MILLIS,
				MILLISECONDS,
				1
			);
			appDeployment = initialization.getServletContext();
			ServletWebsocketModule servletModule = new ServletWebsocketModule(
				appDeployment,
				new PingingWebsocketModule(pingerService, false)
			);
			final ServerContainer endpointContainer = ((ServerContainer)
					appDeployment.getAttribute(ServerContainer.class.getName()));
			appDeployment.addListener(new HttpSessionContext.SessionContextCreator());

			executor = new TaskTrackingThreadPoolExecutor(
					2, new NamingThreadFactory("testExecutor"));
			final var modules = new LinkedList<Module>();
			modules.add(servletModule);
			modules.add(new ServiceModule(
				servletModule.containerCallScope,
				servletModule.websocketConnectionScope,
				servletModule.httpSessionScope,
				ContextTrackingExecutor.of(executor, servletModule.ctxBinder)
			));
			injector = Guice.createInjector(modules);

			final var requestCtxFilter = appDeployment.createFilter(RequestContextFilter.class);
			final var requestCtxFilterRegistration = appDeployment.addFilter(
					RequestContextFilter.class.getSimpleName(), requestCtxFilter);
			requestCtxFilterRegistration.setAsyncSupported(true);
			requestCtxFilterRegistration.addMappingForUrlPatterns(
				EnumSet.allOf(DispatcherType.class),
				false,
				"/*"
			);

			final var endpointConfigurator = new PingingServerEndpointConfigurator(injector);

			addServlet(
				"IndexPageServlet",
				ResourceServlet.class,
				"", "/index.html"  // "" is for the raw app url (like http://localhost:8080/test )
			).setInitParameter(
				ResourceServlet.RESOURCE_PATH_PARAM,
				"/index.html"
			);

			addServlet(
				ForwardingServlet.class.getSimpleName(),
				ForwardingServlet.class,
				"/" + ForwardingServlet.class.getSimpleName()
			);
			addServlet(
				AsyncServlet.class.getSimpleName(),
				AsyncServlet.class,
				"/" + AsyncServlet.class.getSimpleName()
			);
			addServlet(
				TargetedServlet.class.getSimpleName(),
				TargetedServlet.class,
				"/" + TargetedServlet.class.getSimpleName()
			);

			addServlet(
				CrossDeploymentDispatchingServlet.class.getSimpleName(),
				CrossDeploymentDispatchingServlet.class,
				"/" + CrossDeploymentDispatchingServlet.class.getSimpleName()
			);
			addServlet(
				ErrorDispatchingServlet.class.getSimpleName(),
				ErrorDispatchingServlet.class,
				"/" + ErrorDispatchingServlet.class.getSimpleName(),
				ErrorDispatchingServlet.ERROR_HANDLER_PATH
			);

			final var ensureSessionFilterRegistration = appDeployment.addFilter(
				"ensureSessionFilter",
				(request, response, chain) -> {
					((HttpServletRequest) request).getSession();
					chain.doFilter(request, response);
				}
			);
			ensureSessionFilterRegistration.setAsyncSupported(true);
			ensureSessionFilterRegistration.addMappingForUrlPatterns(
					null, false, WEBSOCKET_PATH + "*");

			endpointContainer.addEndpoint(
				ServerEndpointConfig.Builder
					.create(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH)
					.configurator(endpointConfigurator)
					.build()
			);
			endpointConfigurator.getProxyClass(ProgrammaticEndpoint.class);


			final var echoWebsocketPageServlet =
					appDeployment.createServlet(EchoWebsocketPageServlet.class);
			injector.injectMembers(echoWebsocketPageServlet);
			final var echoWebsocketPageServletRegistration = appDeployment.addServlet(
					EchoWebsocketPageServlet.class.getSimpleName(), echoWebsocketPageServlet);
			echoWebsocketPageServletRegistration.setAsyncSupported(true);
			echoWebsocketPageServletRegistration.addMapping("/echo");

			endpointContainer.addEndpoint(
				ServerEndpointConfig.Builder
					.create(RttReportingEndpoint.class, RttReportingEndpoint.PATH)
					.configurator(endpointConfigurator)
					.build()
			);
			endpointConfigurator.getProxyClass(RttReportingEndpoint.class);

			final var rttReportingPageServlet =
					appDeployment.createServlet(ResourceServlet.class);
			injector.injectMembers(rttReportingPageServlet);
			final var rttReportingPageRegistration =
					appDeployment.addServlet("RttReportingPageServlet", rttReportingPageServlet);
			rttReportingPageRegistration.setAsyncSupported(true);
			rttReportingPageRegistration.addMapping("/rttReporting");
			rttReportingPageRegistration.setInitParameter(
					ResourceServlet.RESOURCE_PATH_PARAM, "/rttReporting.html");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(
					"couldn't deploy the app with ManualServletContextListener", e);
		}
	}



	@Override
	public void contextDestroyed(ServletContextEvent destruction) {
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
}
