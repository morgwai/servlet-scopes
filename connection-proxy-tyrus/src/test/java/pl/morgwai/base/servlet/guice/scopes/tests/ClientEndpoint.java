// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.*;
import javax.websocket.MessageHandler.Whole;

import pl.morgwai.base.utils.concurrent.Awaitable;



class ClientEndpoint extends Endpoint {



	final Whole<String> messageHandler;
	final BiConsumer<Session, Throwable> errorHandler;
	final BiConsumer<Session, CloseReason> closeHandler;

	private final CountDownLatch closureLatch = new CountDownLatch(1);

	Session connection;



	public ClientEndpoint(
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
		this.connection = connection;
		if (log.isLoggable(Level.FINER)) {
			log.finer("opened connection to " + connection.getRequestURI().getPath());
		}
		connection.addMessageHandler(String.class, (message) -> {
			if (log.isLoggable(Level.FINE)) {
				log.fine("message from " + connection.getRequestURI().getPath() + '\n' + message);
			}
			messageHandler.onMessage(message);
		});
	}



	@Override
	public void onError(Session connection, Throwable error) {
		log.log(
			Level.WARNING,
			"error on connection to " + connection.getRequestURI().getPath(),
			error
		);
		error.printStackTrace();
		if (errorHandler != null) errorHandler.accept(connection, error);
	}



	@Override
	public void onClose(Session session, CloseReason closeReason) {
		if (log.isLoggable(Level.FINER)) {
			log.finer("connection to " + connection.getRequestURI().getPath() + " closed with "
					+ closeReason.getCloseCode() + ": '" + closeReason.getReasonPhrase() + "'");
		}
		if (closeHandler != null) closeHandler.accept(session, closeReason);
		closureLatch.countDown();
	}



	public Awaitable.WithUnit toAwaitableOfClosure() {
		return closureLatch::await;
	}



	static final Logger log = Logger.getLogger(WebsocketClusteringTests.class.getName());
}