// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;



/**
 * Extends {@link EchoEndpoint} and overrides its {@link #onOpen(Session, EndpointConfig) @OnOpen}
 * method.
 */
@ServerEndpoint(
	value = AnnotatedMethodOverridingEndpoint.PATH,
	configurator = PingingEndpointConfigurator.class
)
public class AnnotatedMethodOverridingEndpoint extends EchoEndpoint {



	public static final String TYPE = "annotatedMethodOverriding";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
	}
}
