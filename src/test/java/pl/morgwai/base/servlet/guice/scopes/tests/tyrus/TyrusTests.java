// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.tyrus;

import javax.websocket.Session;

import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.scopes.tests.WebsocketIntegrationTests;



public class TyrusTests extends WebsocketIntegrationTests {



	@Override
	protected Server createServer() throws Exception {
		return new Tyrus(-1, Server.APP_PATH);
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return false;
	}



	@Override
	protected Session testOpenConnectionToServerEndpoint(String type) throws Exception {
		// Tyrus reports error only after send attempt and not even right away...
		final var connection = super.testOpenConnectionToServerEndpoint(type);
		Thread.sleep(100L);
		connection.getBasicRemote().sendText("yo");
		return connection;
	}



	/** Tyrus does not support it. */
	@Override
	public void testAnnotatedExtendingEndpoint() {}
}
