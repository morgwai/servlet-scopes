// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import javax.websocket.Session;

import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.scopes.tests.tyrus.TyrusServer;



public class TyrusTests extends WebsocketIntegrationTests {



	@Override
	protected Server createServer() throws Exception {
		return new TyrusServer(-1, Server.APP_PATH);
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return false;
	}



	/** TyrusServer reports error only after send attempt and not even right away... */
	@Override
	protected Session testOpenConnectionToServerEndpoint(String type) throws Exception {
		final var connection = super.testOpenConnectionToServerEndpoint(type);
		Thread.sleep(100L);
		connection.getBasicRemote().sendText("yo");
		return connection;
	}



	/** TyrusServer does not support it. */
	@Override
	public void testAnnotatedExtendingEndpoint() {}
}
