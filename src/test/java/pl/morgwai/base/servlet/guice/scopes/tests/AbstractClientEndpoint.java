// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jakarta.websocket.*;

import pl.morgwai.base.utils.concurrent.Awaitable;

import static java.util.logging.Level.FINER;
import static java.util.logging.Level.WARNING;



/** Implements {@link #awaitClosure(long, TimeUnit) closure awaiting} and some logging. */
public abstract class AbstractClientEndpoint extends Endpoint {



	protected Session connection;

	public CloseReason getCloseReason() { return closeReason; }
	private CloseReason closeReason;

	private final CountDownLatch closureLatch = new CountDownLatch(1);



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		this.connection = connection;
		if (log.isLoggable(FINER)) {
			log.finer("opened connection to " + connection.getRequestURI().getPath());
		}
	}



	@Override
	public void onError(Session connection, Throwable error) {
		log.log(WARNING, "error on connection to " + connection.getRequestURI().getPath(), error);
		error.printStackTrace();
	}



	@Override
	public void onClose(Session connection, CloseReason closeReason) {
		this.closeReason = closeReason;
		if (log.isLoggable(FINER)) {
			log.finer("connection to " + connection.getRequestURI().getPath() + " closed with "
					+ closeReason.getCloseCode() + ": '" + closeReason.getReasonPhrase() + "'");
		}
		closureLatch.countDown();
	}



	public boolean awaitClosure(long timeout, TimeUnit unit) throws InterruptedException {
		return closureLatch.await(timeout, unit);
	}



	public Awaitable.WithUnit toAwaitableOfClosure() {
		return closureLatch::await;
	}



	static final Logger log = Logger.getLogger(AbstractClientEndpoint.class.getName());
}
