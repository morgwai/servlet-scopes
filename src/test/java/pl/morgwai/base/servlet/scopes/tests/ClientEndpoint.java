// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests;

import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import javax.websocket.MessageHandler.Whole;



class ClientEndpoint extends Endpoint {



	final Whole<String> messageHandler;
	final BiConsumer<Session, Throwable> errorHandler;

	private final CountDownLatch closureLatch = new CountDownLatch(1);



	public ClientEndpoint(Whole<String> messageHandler) {
		this(messageHandler, null);
	}

	public ClientEndpoint(Whole<String> messageHandler, BiConsumer<Session, Throwable> errorHandler)
	{
		this.messageHandler = messageHandler;
		this.errorHandler = errorHandler;
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
	}



	public void awaitClosure() {
		try {
			closureLatch.await();
		} catch (InterruptedException ignored) {}
	}
}