// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.*;

import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.BroadcastEndpoint;
import pl.morgwai.base.utils.concurrent.Awaitable;

import static org.junit.Assert.*;



public interface WebsocketBroadcastingTests {



	static void testBroadcast(WebSocketContainer clientWebsocketContainer, String... urls)
			throws DeploymentException, IOException, InterruptedException {
		final var broadcastMessage = "broadcast";
		final var welcomesReceived = new CountDownLatch(urls.length);
		final var broadcastSent = new CountDownLatch(1);
		final var broadcastsReceived = new CountDownLatch(urls.length);
		@SuppressWarnings("unchecked")
		final List<String>[] messages = (List<String>[]) new List<?>[urls.length];
		final ClientEndpoint[] clientEndpoints = new ClientEndpoint[urls.length];
		final Session[] connections =  new Session[urls.length];

		try {
			for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
				// create clientEndpoints and open connections
				messages[clientNumber] = new ArrayList<>(2);
				final var localClientNumber = clientNumber;
				clientEndpoints[clientNumber] = new ClientEndpoint(
					(message) -> {
						messages[localClientNumber].add(message);
						if (message.equals(BroadcastEndpoint.WELCOME_MESSAGE)) {
							welcomesReceived.countDown();
						} else {
							broadcastsReceived.countDown();
						}
					},
					(connection, error) -> {},
					(connection, closeReason) -> {}
				);
				connections[clientNumber] = clientWebsocketContainer.connectToServer(
					clientEndpoints[clientNumber],
					null,
					URI.create(urls[clientNumber])
				);
			}

			if ( !welcomesReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
			connections[0].getAsyncRemote().sendText(broadcastMessage);
			broadcastSent.countDown();
			if ( !broadcastsReceived.await(2L, TimeUnit.SECONDS)) fail("timeout");
		} finally {
			for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
				if (connections[clientNumber] != null && connections[clientNumber].isOpen()) {
					try {
						connections[clientNumber].close();
					} catch (IOException ignored) {}
				}
			}
		}

		assertTrue(
			"timeout",
			Awaitable.awaitMultiple(
				2L, TimeUnit.SECONDS,
				Arrays.stream(clientEndpoints)
					.map(Awaitable.entryMapper(ClientEndpoint::toAwaitableOfClosure))
			).isEmpty()
		);
		for (int clientNumber = 0; clientNumber < urls.length; clientNumber++) {
			assertEquals("client " + (clientNumber + 1) + " should receive 2 messages",
					2, messages[clientNumber].size());
			assertEquals(
				"the 1st message of client " + (clientNumber + 1) + " should be the welcome",
				BroadcastEndpoint.WELCOME_MESSAGE,
				messages[clientNumber].get(0)
			);
			assertEquals(
				"the 2nd message of client " + (clientNumber + 1) + " should be the broadcast",
				broadcastMessage,
				messages[clientNumber].get(1)
			);
		}
	}
}
