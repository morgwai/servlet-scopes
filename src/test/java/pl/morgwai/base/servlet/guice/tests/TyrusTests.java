// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests;

import javax.websocket.Session;

import pl.morgwai.base.servlet.guice.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.tests.tyrus.TyrusServer;



public class TyrusTests extends WebsocketIntegrationTests {



	@Override
	protected Server createServer(String testName) throws Exception {
		return new TyrusServer(-1, Server.TEST_APP_PATH);
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return false;
	}



	/** TyrusServer reports error only after send attempt and not even right away... */
	@Override
	protected Session testOpenConnectionToInvalidServerEndpoint(String type) throws Exception {
		final var connection = super.testOpenConnectionToInvalidServerEndpoint(type);
		Thread.sleep(100L);
		connection.getBasicRemote().sendText("yo");
		return connection;
	}



	/** TyrusServer does not support it. */
	@Override
	public void testAnnotatedExtendingProgrammaticEndpoint() {}
}
