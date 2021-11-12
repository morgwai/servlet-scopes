// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import java.util.EnumSet;
import java.util.LinkedList;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebListener;
import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.servlet.guiced.utils.PingingServletContextListener;
import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;



@WebListener
public class ServletContextListener extends PingingServletContextListener {



	public static final String CONTAINER_CALL = "call";
	public static final String WS_CONNECTION = "wsconn";
	public static final String HTTP_SESSION = "session";

	ContextTrackingExecutor executor = servletModule.newContextTrackingExecutor("testExecutor", 2);



	@Override
	protected LinkedList<Module> configureInjections() {
		final var modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(Service.class).annotatedWith(Names.named(CONTAINER_CALL))
					.to(Service.class).in(servletModule.containerCallScope);
			binder.bind(Service.class).annotatedWith(Names.named(WS_CONNECTION))
					.to(Service.class).in(servletModule.websocketConnectionScope);
			binder.bind(Service.class).annotatedWith(Names.named(HTTP_SESSION))
					.to(Service.class).in(servletModule.httpSessionScope);
			binder.bind(ContextTrackingExecutor.class).toInstance(executor);
		});
		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		final var websocketPath = "/websocket/chat";
		// mappings with isMatchAfter==true don't match websocket requests
		addFilter(EnsureSessionFilter.class.getSimpleName(), EnsureSessionFilter.class)
				.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, websocketPath);
		addEndpoint(ChatEndpoint.class, websocketPath);

		addServlet(
				AsyncDispatchingServlet.class.getSimpleName(),
				AsyncDispatchingServlet.class,
				"/test");
	}



	@Override
	public void contextDestroyed(ServletContextEvent event) {
		super.contextDestroyed(event);
		ChatEndpoint.shutdown();
	}
}
