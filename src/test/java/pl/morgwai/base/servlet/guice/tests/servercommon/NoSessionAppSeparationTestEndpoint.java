// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
	value= NoSessionAppSeparationTestEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class NoSessionAppSeparationTestEndpoint extends AppSeparationTestEndpoint {



	public static final String PATH = "/noSessionWebsocket/separation";
}
