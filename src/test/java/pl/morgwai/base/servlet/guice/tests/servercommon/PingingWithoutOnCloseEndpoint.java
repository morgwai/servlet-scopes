// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.utils.PingingServerEndpointConfigurator;



@ServerEndpoint(
	value = PingingWithoutOnCloseEndpoint.PATH,
	configurator = PingingServerEndpointConfigurator.class
)
public class PingingWithoutOnCloseEndpoint {



	public static final String TYPE = "pingingWithoutOnClose";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;



	@OnOpen
	public void onOpen(Session connection) {}
}
