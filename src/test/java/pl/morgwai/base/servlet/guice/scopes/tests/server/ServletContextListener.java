// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.util.LinkedList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;

import com.google.inject.Module;
import com.google.inject.name.Names;
import pl.morgwai.base.servlet.guice.scopes.ServletContextTrackingExecutor;
import pl.morgwai.base.servlet.guice.utils.PingingServletContextListener;



@WebListener
public class ServletContextListener extends PingingServletContextListener {



	public static final String WEBSOCKET_PATH = "/websocket";

	public static final String CONTAINER_CALL = "containerCall";
	public static final String WEBSOCKET_CONNECTION = "wsConnection";
	public static final String HTTP_SESSION = "httpSession";

	final ServletContextTrackingExecutor executor =
			servletModule.newContextTrackingExecutor("testExecutor", 2);



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
			binder.bind(ServletContextTrackingExecutor.class).toInstance(executor);
		});
		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		installEnsureSessionFilter(WEBSOCKET_PATH + "/*");
		addEndpoint(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH);
		addServlet(AsyncServlet.class.getSimpleName(), AsyncServlet.class, AsyncServlet.PATH);
		addServlet(
			DispatchingServlet.class.getSimpleName(),
			DispatchingServlet.class,
			DispatchingServlet.PATH
		);
		addServlet("IndexPageServlet", ResourceServlet.class, "", "/index.html")
				// "" is for the raw app url (like http://localhost:8080/test ) to work
				.setInitParameter(ResourceServlet.RESOURCE_PATH_PARAM, "/index.html");
		addServlet(  // http://localhost:8080/test/echo
			WebsocketPageServlet.class.getSimpleName(),
			WebsocketPageServlet.class,
			WebsocketPageServlet.PATH
		);
	}
}
