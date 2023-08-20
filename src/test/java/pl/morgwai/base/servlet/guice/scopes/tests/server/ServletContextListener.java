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



	/** All {@code Endpoints} are deployed somewhere under this path. */
	public static final String WEBSOCKET_PATH = "/websocket/";

	// values for @Named corresponding to available Scopes
	public static final String CONTAINER_CALL = "containerCall";
	public static final String WEBSOCKET_CONNECTION = "wsConnection";
	public static final String HTTP_SESSION = "httpSession";



	@Override
	protected LinkedList<Module> configureInjections() {
		final var executor = servletModule.newContextTrackingExecutor("testExecutor", 2);
		final var modules = new LinkedList<Module>();
		modules.add((binder) -> {
			// usually Executors are bound with same name, but in this app there's only 1
			binder.bind(ServletContextTrackingExecutor.class).toInstance(executor);

			// bind Service in 3 different scopes depending on the value of @Named
			binder.bind(Service.class)
				.annotatedWith(Names.named(CONTAINER_CALL))
				.to(Service.class)
				.in(containerCallScope);
			binder.bind(Service.class)
				.annotatedWith(Names.named(WEBSOCKET_CONNECTION))
				.to(Service.class)
				.in(websocketConnectionScope);
			binder.bind(Service.class)
				.annotatedWith(Names.named(HTTP_SESSION))
				.to(Service.class)
				.in(httpSessionScope);
		});
		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
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

		installEnsureSessionFilter(WEBSOCKET_PATH + '*');
		addEndpoint(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH);
		addServlet(
			EchoWebsocketPageServlet.class.getSimpleName(),
			EchoWebsocketPageServlet.class,
			"/echo"
		);
	}
}
