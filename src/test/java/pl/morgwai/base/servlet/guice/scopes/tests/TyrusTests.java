// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import javax.websocket.Session;

import org.junit.After;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.scopes.tests.tyrus.TyrusServer;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;



public class TyrusTests extends WebsocketIntegrationTests {



	protected StandaloneWebsocketContainerServletContext appDeployment;



	@Override
	protected Server createServer() throws Exception {
		appDeployment = TyrusServer.createDeployment(Server.TEST_APP_PATH);
		return new TyrusServer(-1, Server.TEST_APP_PATH);
	}



	@After
	public void cleanupDeployment() {
		TyrusServer.cleanupDeployment(appDeployment);
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
	public void testAnnotatedExtendingProgrammaticEndpoint() {}
}
