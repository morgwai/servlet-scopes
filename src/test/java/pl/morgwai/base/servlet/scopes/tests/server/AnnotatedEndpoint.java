// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guiced.utils.PingingEndpointConfigurator;



@ServerEndpoint(
		value = AnnotatedEndpoint.PATH,
		configurator = PingingEndpointConfigurator.class)
public class AnnotatedEndpoint extends EchoEndpoint {



	public static final String TYPE = "annotated";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + '/' + TYPE;



	@OnOpen @Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
	}



	@OnMessage @Override
	public void onMessage(String message) {
		super.onMessage(message);
	}



	@OnError @Override
	public void onError(Session connection, Throwable error) {
		super.onError(connection, error);
	}



	@OnClose @Override
	public void onClose(CloseReason closeReason) {
		super.onClose(closeReason);
	}
}
