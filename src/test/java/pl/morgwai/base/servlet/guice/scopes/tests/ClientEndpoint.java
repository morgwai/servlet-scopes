// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import javax.websocket.*;
import javax.websocket.MessageHandler.Whole;



class ClientEndpoint extends Endpoint {



	final Whole<String> messageHandler;
	final BiConsumer<Session, Throwable> errorHandler;
	final BiConsumer<Session, CloseReason> closeHandler;

	private final CountDownLatch closureLatch = new CountDownLatch(1);



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
		connection.addMessageHandler(String.class, messageHandler);
	}



	@Override
	public void onError(Session connection, Throwable error) {
		if (errorHandler != null) errorHandler.accept(connection, error);
	}



	@Override
	public void onClose(Session session, CloseReason closeReason) {
		closureLatch.countDown();
		if (closeHandler != null) closeHandler.accept(session, closeReason);
	}



	public boolean awaitClosure(long timeout, TimeUnit unit) throws InterruptedException {
		return closureLatch.await(timeout, unit);
	}
}
