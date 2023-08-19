// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import jakarta.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
	value= NoSessionAppSeparationTestEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class NoSessionAppSeparationTestEndpoint extends AppSeparationTestEndpoint {



	public static final String PATH = "/noSessionWebsocket/separation";
}
