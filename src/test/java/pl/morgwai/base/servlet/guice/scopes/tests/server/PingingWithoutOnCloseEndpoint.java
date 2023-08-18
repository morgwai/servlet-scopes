// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;



/**
 * For {@link
 * pl.morgwai.base.servlet.guice.scopes.tests.IntegrationTests#testPingingWithoutOnCloseEndpoint()}.
 */
@ServerEndpoint(
		value = PingingWithoutOnCloseEndpoint.PATH,
		configurator = PingingEndpointConfigurator.class)
public class PingingWithoutOnCloseEndpoint {



	public static final String TYPE = "pingingWithoutOnClose";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + TYPE;



	@OnOpen
	public void onOpen(Session connection) {}
}
