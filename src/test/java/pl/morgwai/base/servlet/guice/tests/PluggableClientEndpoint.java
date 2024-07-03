// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests;

import java.util.function.BiConsumer;
import javax.websocket.*;
import javax.websocket.MessageHandler.Whole;

import static java.util.logging.Level.FINE;



public class PluggableClientEndpoint extends AbstractClientEndpoint {



	final Whole<String> messageHandler;
	final BiConsumer<Session, Throwable> errorHandler;
	final BiConsumer<Session, CloseReason> closeHandler;



	public PluggableClientEndpoint(
		Whole<String> messageHandler,
		BiConsumer<Session, Throwable> errorHandler,
		BiConsumer<Session, CloseReason> closeHandler
	) {
		this.messageHandler = messageHandler;
		this.errorHandler = errorHandler;
		this.closeHandler = closeHandler;
	}



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
		connection.addMessageHandler(String.class, (message) -> {
			if (log.isLoggable(FINE)) {
				log.fine("message from " + connection.getRequestURI().getPath() + '\n' + message);
			}
			messageHandler.onMessage(message);
		});
	}



	@Override
	public void onError(Session connection, Throwable error) {
		super.onError(connection, error);
		if (errorHandler != null) errorHandler.accept(connection, error);
	}



	@Override
	public void onClose(Session connection, CloseReason closeReason) {
		if (closeHandler != null) closeHandler.accept(connection, closeReason);
		super.onClose(connection, closeReason);
	}
}
