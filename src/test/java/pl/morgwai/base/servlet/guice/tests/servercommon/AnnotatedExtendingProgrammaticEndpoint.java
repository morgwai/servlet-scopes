// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



/** Not supported by Tyrus. */
@ServerEndpoint(
	value = AnnotatedExtendingProgrammaticEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class AnnotatedExtendingProgrammaticEndpoint extends ProgrammaticEndpoint {



	public static final String TYPE = "annotatedExtendingProgrammatic";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;
}
