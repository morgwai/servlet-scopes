// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
	value = BroadcastEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class BroadcastEndpoint {



	public static final String PATH = "/echo";
	public static final String WELCOME_MESSAGE = "yo!";

	Session connection;



	@OnOpen
	public void onOpen(Session connection) {
		log.fine("onOpen: connection id: " + connection.getId());
		this.connection = connection;
		connection.setMaxIdleTimeout(5L * 60L * 1000L);
		connection.getAsyncRemote().sendText(WELCOME_MESSAGE);
	}



	@OnMessage
	public void onMessage(String message) {
		log.fine("onMessage: connection id: " + connection.getId() + ", message: " + message);
		for (var peerConnection: connection.getOpenSessions()) {
			peerConnection.getAsyncRemote().sendText(message);
		}
	}



	@OnError
	public void onError(Session connection, Throwable error) {
		log.log(Level.WARNING, "onError: connection id: " + connection.getId(), error);
		error.printStackTrace();
		try {
			connection.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, error.toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	@OnClose
	public void onClose(CloseReason closeReason) {
		log.info("onClose: connection id: " + connection.getId() + ", code: "
				+ closeReason.getCloseCode() + ", reason: " + closeReason.getReasonPhrase());
	}



	static final Logger log = Logger.getLogger(BroadcastEndpoint.class.getPackageName());
}
