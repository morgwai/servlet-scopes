// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import jakarta.websocket.OnOpen;
import jakarta.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
	value = OnOpenWithoutSessionParamEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class OnOpenWithoutSessionParamEndpoint {



	public static final String TYPE = "onOpenWithoutSessionParam";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;



	@OnOpen
	public void onOpen() {}
}
