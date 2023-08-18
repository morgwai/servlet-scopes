// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.io.IOException;

import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.google.inject.Inject;
import com.google.inject.Injector;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



/** For {@link pl.morgwai.base.servlet.guice.scopes.tests.IntegrationTests#testAppSeparation()}. */
@ServerEndpoint(
	value = AppSeparationTestEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class AppSeparationTestEndpoint {



	public static final String TYPE = "separation";
	public static final String PATH = ServletContextListener.WEBSOCKET_PATH + TYPE;

	@Inject Injector injector;



	@OnOpen
	public void onOpen(Session connection) {
		try {
			connection.getBasicRemote().sendText("injector: " + injector.hashCode());
			connection.close();
		} catch (IOException ignored) {}
	}
}
