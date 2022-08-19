// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import jakarta.websocket.*;

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



	@Override
	public void onClose(Session session, CloseReason closeReason) {
		echoEndpoint.onClose(closeReason);
	}
}
