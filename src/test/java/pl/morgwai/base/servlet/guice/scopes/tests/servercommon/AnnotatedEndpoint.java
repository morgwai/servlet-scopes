// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import com.google.inject.Inject;
import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator;
import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator.RttObserver;



/** Delegates to its wrapped {@link EchoEndpoint} instance. */
@ServerEndpoint(
	value = AnnotatedEndpoint.PATH,
	configurator = PingingEndpointConfigurator.class
)
public class AnnotatedEndpoint implements RttObserver {



	public static final String TYPE = "annotated";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;



	@Inject EchoEndpoint echoEndpoint;



	@OnOpen
	public void onOpen(Session connection, EndpointConfig config) {
		echoEndpoint.onOpen(connection, config);
	}



	@OnMessage
	public void onMessage(String message) {
		echoEndpoint.onMessage(message);
	}



	@Override
	public void onPong(long rttNanos) {}



	@OnError
	public void onError(Session connection, Throwable error) {
		echoEndpoint.onError(connection, error);
	}



	@OnClose
	public void onClose(CloseReason closeReason) {
		echoEndpoint.onClose(closeReason);
	}
}
