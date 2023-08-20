// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.servlet.*;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.name.Names;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static pl.morgwai.base.servlet.guice.scopes.tests.server.ServletContextListener.*;



/**
 * A listener that does not extend {@link GuiceServletContextListener} and does exactly the same job
 * as {@link pl.morgwai.base.servlet.guice.scopes.tests.server.ServletContextListener} from this
 * package (that extends {@link pl.morgwai.base.servlet.guice.utils.PingingServletContextListener}).
 */
public class ManualServletContextListener implements ServletContextListener {



	ServletModule servletModule;
	final WebsocketPingerService pingerService = new WebsocketPingerService();



	@Override
	public final void contextInitialized(ServletContextEvent initialization) {
		try {
			final ServletContext servletContainer = initialization.getServletContext();
			servletModule = new ServletModule(servletContainer);
			final ServerContainer endpointContainer = ((ServerContainer)
					servletContainer.getAttribute("jakarta.websocket.server.ServerContainer"));
			servletContainer.addListener(new HttpSessionContext.SessionContextCreator());

			final var executor = servletModule.newContextTrackingExecutor("testExecutor", 2);
			final var modules = new LinkedList<Module>();
			modules.add(servletModule);
			modules.add((binder) -> {  // same as in ServletContextListener
				binder.bind(ServletContextTrackingExecutor.class).toInstance(executor);
				binder.bind(Service.class)
					.annotatedWith(Names.named(CONTAINER_CALL))
					.to(Service.class)
					.in(servletModule.containerCallScope);
				binder.bind(Service.class)
					.annotatedWith(Names.named(WEBSOCKET_CONNECTION))
					.to(Service.class)
					.in(servletModule.websocketConnectionScope);
				binder.bind(Service.class)
					.annotatedWith(Names.named(HTTP_SESSION))
					.to(Service.class)
					.in(servletModule.httpSessionScope);
			});
			final Injector injector = Guice.createInjector(modules);

			final var requestCtxFilter = servletContainer.createFilter(RequestContextFilter.class);
			injector.injectMembers(requestCtxFilter);
			final var requestCtxFilterRegistration = servletContainer.addFilter(
					RequestContextFilter.class.getSimpleName(), requestCtxFilter);
			requestCtxFilterRegistration.setAsyncSupported(true);
			requestCtxFilterRegistration.addMappingForUrlPatterns(
				EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC),
				false,
				"/*"
			);

			servletContainer.setAttribute(Injector.class.getName(), injector);
			servletContainer.setAttribute(WebsocketPingerService.class.getName(), pingerService);
			GuiceServerEndpointConfigurator.registerInjector(injector, servletContainer);
			PingingEndpointConfigurator.registerPingerService(pingerService, servletContainer);
			final var endpointConfigurator = new PingingEndpointConfigurator(
					injector, servletModule.containerCallContextTracker, pingerService);

			final var indexPageServlet = servletContainer.createServlet(ResourceServlet.class);
			injector.injectMembers(indexPageServlet);
			final var indexPageRegistration =
					servletContainer.addServlet("IndexPageServlet", indexPageServlet);
			indexPageRegistration.setAsyncSupported(true);
			indexPageRegistration.addMapping("", "/index.html");
			indexPageRegistration.setInitParameter(
					ResourceServlet.RESOURCE_PATH_PARAM, "/index.html");

			final var forwardingServlet = servletContainer.createServlet(ForwardingServlet.class);
			injector.injectMembers(forwardingServlet);
			final var forwardingServletRegistration = servletContainer.addServlet(
					ForwardingServlet.class.getSimpleName(), forwardingServlet);
			forwardingServletRegistration.setAsyncSupported(true);
			forwardingServletRegistration.addMapping("/" + ForwardingServlet.class.getSimpleName());

			final var asyncServlet = servletContainer.createServlet(AsyncServlet.class);
			injector.injectMembers(asyncServlet);
			final var asyncServletRegistration = servletContainer.addServlet(
					AsyncServlet.class.getSimpleName(), asyncServlet);
			asyncServletRegistration.setAsyncSupported(true);
			asyncServletRegistration.addMapping("/" + AsyncServlet.class.getSimpleName());

			final var targetedServlet = servletContainer.createServlet(TargetedServlet.class);
			injector.injectMembers(targetedServlet);
			final var targetedServletRegistration = servletContainer.addServlet(
					TargetedServlet.class.getSimpleName(), targetedServlet);
			targetedServletRegistration.setAsyncSupported(true);
			targetedServletRegistration.addMapping("/" + TargetedServlet.class.getSimpleName());

			final var ensureSessionFilterRegistration = servletContainer.addFilter(
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

			final var echoWebsocketPageServlet =
					servletContainer.createServlet(EchoWebsocketPageServlet.class);
			injector.injectMembers(echoWebsocketPageServlet);
			final var echoWebsocketPageServletRegistration = servletContainer.addServlet(
					EchoWebsocketPageServlet.class.getSimpleName(), echoWebsocketPageServlet);
			echoWebsocketPageServletRegistration.setAsyncSupported(true);
			echoWebsocketPageServletRegistration.addMapping("/echo");
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}



	@Override
	public void contextDestroyed(ServletContextEvent destruction) {
		pingerService.stop();
		GuiceServerEndpointConfigurator.deregisterInjector(destruction.getServletContext());
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
