// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.websocket.*;

import com.google.inject.Inject;
import com.google.inject.Provider;
import pl.morgwai.base.servlet.guice.scopes.ContainerCallContext;
import pl.morgwai.base.servlet.guice.scopes.WebsocketConnectionContext;

import static javax.websocket.CloseReason.CloseCodes.NORMAL_CLOSURE;



public class TestGuiceClientEndpoint extends AbstractClientEndpoint {



	@Inject Provider<ContainerCallContext> clientEventCtxProvider;
	public List<ContainerCallContext> getClientEventCtxs() { return clientEventCtxs; }
	private final List<ContainerCallContext> clientEventCtxs = new ArrayList<>(4);

	@Inject Provider<WebsocketConnectionContext> clientConnectionCtxProvider;
	public List<WebsocketConnectionContext> getClientConnectionCtxs() {return clientConnectionCtxs;}
	private final List<WebsocketConnectionContext> clientConnectionCtxs = new ArrayList<>(4);

	public List<Properties> getServerReplies() { return serverReplies; }
	private final List<Properties> serverReplies = new ArrayList<>(2);
	private final CountDownLatch allRepliesReceived = new CountDownLatch(2);



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
		clientEventCtxs.add(clientEventCtxProvider.get());
		clientConnectionCtxs.add(clientConnectionCtxProvider.get());
		connection.addMessageHandler(String.class, this::onMessage);
	}



	void onMessage(String reply) {
		if (serverReplies.size() >= 2) return;  // extra pong in pinging Endpoint tests
		clientEventCtxs.add(clientEventCtxProvider.get());
		clientConnectionCtxs.add(clientConnectionCtxProvider.get());
		final var parsedReply = new Properties(5);
		try {
			parsedReply.load(new StringReader(reply));
			serverReplies.add(parsedReply);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			allRepliesReceived.countDown();
		}
	}



	@Override
	public void onClose(Session connection, CloseReason closeReason) {
		clientEventCtxs.add(clientEventCtxProvider.get());
		clientConnectionCtxs.add(clientConnectionCtxProvider.get());
		super.onClose(connection, closeReason);
		if (closeReason.getCloseCode().getCode() != NORMAL_CLOSURE.getCode()) {
			// server endpoint error: signal the main test Thread that may still be awaiting for
			// replies as they will never arrive in such case
			allRepliesReceived.countDown();
		}
	}



	public boolean awaitAllReplies(long timeout, TimeUnit unit) throws InterruptedException {
		return allRepliesReceived.await(timeout, unit);
	}
}
