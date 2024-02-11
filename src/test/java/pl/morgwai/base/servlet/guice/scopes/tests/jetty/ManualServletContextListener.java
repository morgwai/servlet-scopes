// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.ServletContextListener;
import javax.servlet.*;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;
import pl.morgwai.base.servlet.guice.utils.PingingServletContextListener;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * A listener that does not extend {@link GuiceServletContextListener} and does exactly the same job
 * as {@link pl.morgwai.base.servlet.guice.scopes.tests.jetty.ServletContextListener} from this
 * package (that extends {@link pl.morgwai.base.servlet.guice.utils.PingingServletContextListener}).
 */
@WebListener
public class ManualServletContextListener implements ServletContextListener {



	ServletModule servletModule;
	WebsocketPingerService pingerService;



	@Override
	public final void contextInitialized(ServletContextEvent initialization) {
		try {
			final var intervalFromProperty =
					System.getProperty(Server.PING_INTERVAL_MILLIS_PROPERTY);
			pingerService = new WebsocketPingerService(
				intervalFromProperty != null ? Long.parseLong(intervalFromProperty) : 500L,
				TimeUnit.MILLISECONDS,
				PingingServletContextListener.DEFAULT_FAILURE_LIMIT
			);
			final ServletContext appDeployment = initialization.getServletContext();
			servletModule = new ServletModule(appDeployment);
			final ServerContainer endpointContainer = ((ServerContainer)
					appDeployment.getAttribute(ServerContainer.class.getName()));
			appDeployment.addListener(new HttpSessionContext.SessionContextCreator());

			final var modules = new LinkedList<Module>();
			modules.add(servletModule);
			modules.add(new ServiceModule(servletModule, true));
			final Injector injector = Guice.createInjector(modules);

			final var requestCtxFilter = appDeployment.createFilter(RequestContextFilter.class);
			injector.injectMembers(requestCtxFilter);
			final var requestCtxFilterRegistration = appDeployment.addFilter(
					RequestContextFilter.class.getSimpleName(), requestCtxFilter);
			requestCtxFilterRegistration.setAsyncSupported(true);
			requestCtxFilterRegistration.addMappingForUrlPatterns(
				EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC),
				false,
				"/*"
			);

			appDeployment.setAttribute(Injector.class.getName(), injector);
			appDeployment.setAttribute(WebsocketPingerService.class.getName(), pingerService);
			GuiceServerEndpointConfigurator.registerDeployment(appDeployment);
			final var endpointConfigurator = new PingingEndpointConfigurator(appDeployment);

			final var indexPageServlet = appDeployment.createServlet(ResourceServlet.class);
			injector.injectMembers(indexPageServlet);
			final var indexPageRegistration =
					appDeployment.addServlet("IndexPageServlet", indexPageServlet);
			indexPageRegistration.setAsyncSupported(true);
			indexPageRegistration.addMapping("", "/index.html");
			indexPageRegistration.setInitParameter(
					ResourceServlet.RESOURCE_PATH_PARAM, "/index.html");

			final var forwardingServlet = appDeployment.createServlet(ForwardingServlet.class);
			injector.injectMembers(forwardingServlet);
			final var forwardingServletRegistration = appDeployment.addServlet(
					ForwardingServlet.class.getSimpleName(), forwardingServlet);
			forwardingServletRegistration.setAsyncSupported(true);
			forwardingServletRegistration.addMapping("/" + ForwardingServlet.class.getSimpleName());

			final var asyncServlet = appDeployment.createServlet(AsyncServlet.class);
			injector.injectMembers(asyncServlet);
			final var asyncServletRegistration = appDeployment.addServlet(
					AsyncServlet.class.getSimpleName(), asyncServlet);
			asyncServletRegistration.setAsyncSupported(true);
			asyncServletRegistration.addMapping("/" + AsyncServlet.class.getSimpleName());

			final var targetedServlet = appDeployment.createServlet(TargetedServlet.class);
			injector.injectMembers(targetedServlet);
			final var targetedServletRegistration = appDeployment.addServlet(
					TargetedServlet.class.getSimpleName(), targetedServlet);
			targetedServletRegistration.setAsyncSupported(true);
			targetedServletRegistration.addMapping("/" + TargetedServlet.class.getSimpleName());

			final var ensureSessionFilterRegistration = appDeployment.addFilter(
				"ensureSessionFilter",
				(request, response, chain) -> {
					((HttpServletRequest) request).getSession();
					chain.doFilter(request, response);
				}
			);
			ensureSessionFilterRegistration.setAsyncSupported(true);
			ensureSessionFilterRegistration.addMappingForUrlPatterns(
					null, false, Server.WEBSOCKET_PATH + "*");

			endpointContainer.addEndpoint(
				ServerEndpointConfig.Builder
					.create(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH)
					.configurator(endpointConfigurator)
					.build()
			);

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
		pingerService.stop();
		GuiceServerEndpointConfigurator.deregisterDeployment(destruction.getServletContext());
		servletModule.shutdownAllExecutors();
		List<ServletContextTrackingExecutor> unterminated;
		try {
			unterminated = servletModule.awaitTerminationOfAllExecutors(5L, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			unterminated = servletModule.getExecutors().stream()
				.filter((executor) -> !executor.isTerminated())
				.collect(Collectors.toList());
		}
		for (var executor: unterminated) {
			executor.shutdownNow();  // ...or something more specific
		}
	}
}
