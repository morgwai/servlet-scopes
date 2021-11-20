// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import java.util.EnumSet;
import java.util.LinkedList;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;
import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.servlet.guiced.utils.PingingServletContextListener;
import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;



@WebListener
public class ServletContextListener extends PingingServletContextListener {



	public static final String WEBSOCKET_PATH = "/websocket";

	public static final String CONTAINER_CALL = "containerCall";
	public static final String WEBSOCKET_CONNECTION = "wsConnection";
	public static final String HTTP_SESSION = "httpSession";

	ContextTrackingExecutor executor = servletModule.newContextTrackingExecutor("testExecutor", 2);



	@Override
	protected LinkedList<Module> configureInjections() {
		final var modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(Service.class).annotatedWith(Names.named(CONTAINER_CALL))
					.to(Service.class).in(servletModule.containerCallScope);
			binder.bind(Service.class).annotatedWith(Names.named(WEBSOCKET_CONNECTION))
					.to(Service.class).in(servletModule.websocketConnectionScope);
			binder.bind(Service.class).annotatedWith(Names.named(HTTP_SESSION))
					.to(Service.class).in(servletModule.httpSessionScope);
			binder.bind(ContextTrackingExecutor.class).toInstance(executor);
		});
		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		// mappings with isMatchAfter==true don't match websocket requests
		addFilter(EnsureSessionFilter.class.getSimpleName(), EnsureSessionFilter.class)
				.addMappingForUrlPatterns(
						EnumSet.of(DispatcherType.REQUEST), false, WEBSOCKET_PATH + "/*");
		addEndpoint(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH);
		addServlet(
				AsyncServlet.class.getSimpleName(), AsyncServlet.class, AsyncServlet.PATH);
		addServlet(
				DispatchingServlet.class.getSimpleName(),
				DispatchingServlet.class,
				DispatchingServlet.PATH);
		addServlet("IndexPageServlet", ResouceServlet.class, "", "/index.html")
				.setInitParameter(ResouceServlet.RESOURCE_PATH_PARAM, "/index.html");
		addServlet(
				WebsocketPageServlet.class.getSimpleName(),
				WebsocketPageServlet.class,
				WebsocketPageServlet.PATH);
	}
}
