// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
		value = OnOpenWithoutSessionParamEndpoint.PATH,
		configurator = GuiceServerEndpointConfigurator.class)
public class OnOpenWithoutSessionParamEndpoint {

	public static final String TYPE = "onOpenWithoutSessionParam";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + '/' + TYPE;



	@OnOpen
	public void onOpen() {}
}