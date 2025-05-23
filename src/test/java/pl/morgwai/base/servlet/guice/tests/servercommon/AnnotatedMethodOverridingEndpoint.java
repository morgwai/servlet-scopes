// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.utils.PingingServerEndpointConfigurator;



/**
 * Extends {@link EchoEndpoint} and overrides its {@link #onOpen(Session, EndpointConfig) @OnOpen}
 * method.
 * Tyrus requires overriding methods to be re-annotated, hence
 * {@link pl.morgwai.base.servlet.guice.tests.tyrus.TyrusAnnotatedMethodOverridingEndpoint}
 * exists.
 */
@ServerEndpoint(
	value = AnnotatedMethodOverridingEndpoint.PATH,
	configurator = PingingServerEndpointConfigurator.class
)
public class AnnotatedMethodOverridingEndpoint extends EchoEndpoint {



	public static final String TYPE = "annotatedMethodOverriding";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
	}
}
