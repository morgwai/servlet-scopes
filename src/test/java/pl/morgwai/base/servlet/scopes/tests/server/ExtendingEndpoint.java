// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
		value = ExtendingEndpoint.PATH,
		configurator = GuiceServerEndpointConfigurator.class)
public class ExtendingEndpoint extends ProgrammaticEndpoint {

	public static final String TYPE = "extending";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + '/' + TYPE;
}
