// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import java.util.EnumSet;
import java.util.LinkedList;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;
import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.servlet.guiced.utils.SimplePingingEndpointServletContextListener;



@WebListener
public class ServletContextListener extends SimplePingingEndpointServletContextListener {



	public static final String REQUEST = "request";
	public static final String WS_CONNECTION = "wsconn";
	public static final String HTTP_SESSION = "session";



	@Override
	protected LinkedList<Module> configureInjections() {
		final var modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(Service.class).annotatedWith(Names.named(REQUEST))
					.to(Service.class).in(servletModule.requestScope);
			binder.bind(Service.class).annotatedWith(Names.named(WS_CONNECTION))
					.to(Service.class).in(servletModule.websocketConnectionScope);
			binder.bind(Service.class).annotatedWith(Names.named(HTTP_SESSION))
					.to(Service.class).in(servletModule.httpSessionScope);
		});
		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		final var websocketPath = "/websocket/chat";

		// mappings with isMatchAfter==true don't match websocket requests, so we can't just do
		// addFilter("ensureSessionFilter", EnsureSessionFilter.class, websocketPath);
		final var ensureSessionFilter = ctx.createFilter(EnsureSessionFilter.class);
		INJECTOR.injectMembers(ensureSessionFilter);
		final FilterRegistration.Dynamic reg =
				ctx.addFilter(EnsureSessionFilter.class.getSimpleName(), ensureSessionFilter);
		reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, websocketPath);
		reg.setAsyncSupported(true);

		addEndpoint(ChatEndpoint.class, websocketPath);
		addServlet(TestServlet.class.getSimpleName(), TestServlet.class, "/test");
	}



	@Override
	public void contextDestroyed(ServletContextEvent event) {
		super.contextDestroyed(event);
		ChatEndpoint.shutdown();
	}
}
