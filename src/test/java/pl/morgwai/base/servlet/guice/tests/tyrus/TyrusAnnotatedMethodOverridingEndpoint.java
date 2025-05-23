// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.tyrus;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.tests.servercommon.AnnotatedMethodOverridingEndpoint;
import pl.morgwai.base.servlet.guice.utils.PingingServerEndpointConfigurator;



@ServerEndpoint(
	value = AnnotatedMethodOverridingEndpoint.PATH,
	configurator = PingingServerEndpointConfigurator.class
)
public class TyrusAnnotatedMethodOverridingEndpoint extends AnnotatedMethodOverridingEndpoint {



	@OnOpen	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
	}
}
