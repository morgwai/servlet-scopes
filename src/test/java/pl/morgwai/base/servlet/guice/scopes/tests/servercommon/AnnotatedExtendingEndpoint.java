// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;



@ServerEndpoint(
	value = AnnotatedExtendingEndpoint.PATH,
	configurator = PingingEndpointConfigurator.class
)
public class AnnotatedExtendingEndpoint extends EchoEndpoint {



	public static final String TYPE = "annotatedExtending";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;
}
