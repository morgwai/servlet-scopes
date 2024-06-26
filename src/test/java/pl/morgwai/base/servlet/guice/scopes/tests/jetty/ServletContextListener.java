// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.util.LinkedList;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;
import javax.websocket.DeploymentException;

import com.google.inject.Module;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;
import pl.morgwai.base.servlet.guice.utils.PingingServletContextListener;

import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server.*;



@WebListener
public class ServletContextListener extends PingingServletContextListener {



	@Override
	protected LinkedList<Module> configureInjections() {
		final var modules = new LinkedList<Module>();
		modules.add(new ServiceModule(servletModule, true));
		return modules;
	}



	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException, DeploymentException
	{
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

		addEnsureSessionFilter(Server.WEBSOCKET_PATH + '*');

		addEndpoint(ProgrammaticEndpoint.class, ProgrammaticEndpoint.PATH);
		addServlet(
			EchoWebsocketPageServlet.class.getSimpleName(),
			EchoWebsocketPageServlet.class,
			"/echo"
		);

		addEndpoint(RttReportingEndpoint.class, RttReportingEndpoint.PATH);
		addServlet(
			"RttReportingPageServlet",
			ResourceServlet.class,
			"/rttReporting"
		).setInitParameter(
			ResourceServlet.RESOURCE_PATH_PARAM,
			"/rttReporting.html"
		);
	}



	@Override
	protected long getPingIntervalMillis() {
		final var intervalFromProperty = System.getProperty(PING_INTERVAL_MILLIS_PROPERTY);
		return intervalFromProperty != null
				? Long.parseLong(intervalFromProperty)
				: DEFAULT_PING_INTERVAL_MILLIS;
	}
}
