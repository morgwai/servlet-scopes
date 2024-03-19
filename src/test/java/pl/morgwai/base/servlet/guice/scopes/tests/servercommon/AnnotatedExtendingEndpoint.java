// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import jakarta.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



/** Extending {@link ProgrammaticEndpoint} and annotated with {@link ServerEndpoint}. */
@ServerEndpoint(
	value = AnnotatedExtendingEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class AnnotatedExtendingEndpoint extends ProgrammaticEndpoint {



	public static final String TYPE = "annotatedExtending";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;
}
