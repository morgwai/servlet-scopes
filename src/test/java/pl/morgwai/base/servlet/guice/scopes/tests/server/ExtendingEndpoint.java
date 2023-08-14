// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import jakarta.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
		value = ExtendingEndpoint.PATH, configurator = GuiceServerEndpointConfigurator.class)
public class ExtendingEndpoint extends ProgrammaticEndpoint {

	public static final String TYPE = "extending";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + '/' + TYPE;
}
