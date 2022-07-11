// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import javax.websocket.*;

import com.google.inject.Inject;



public class ProgrammaticEndpoint extends Endpoint {



	public static final String TYPE = "programmatic";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + '/' + TYPE;



	@Inject EchoEndpoint echoEndpoint;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		connection.addMessageHandler(String.class, echoEndpoint::onMessage);
		echoEndpoint.onOpen(connection, config);
	}



	@Override
	public void onError(Session connection, Throwable error) {
		echoEndpoint.onError(connection, error);
	}
}
