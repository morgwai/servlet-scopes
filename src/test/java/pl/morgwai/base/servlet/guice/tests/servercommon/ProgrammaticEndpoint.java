// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import javax.websocket.*;

import com.google.inject.Inject;



public class ProgrammaticEndpoint extends Endpoint {



	public static final String TYPE = "programmatic";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;



	@Inject EchoEndpoint echoEndpoint;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		final var handler = new MessageHandler.Whole<String>() {
			@Override public void onMessage(String message) {
				echoEndpoint.onMessage(message);
			}
		};
		connection.addMessageHandler(handler);
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
