// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import java.io.IOException;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import com.google.inject.Inject;
import com.google.inject.Injector;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;



@ServerEndpoint(
	value = AppSeparationTestEndpoint.PATH,
	configurator = GuiceServerEndpointConfigurator.class
)
public class AppSeparationTestEndpoint {



	public static final String TYPE = "separation";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;

	@Inject Injector injector;



	@OnOpen
	public void onOpen(Session connection) {
		try {
			connection.getBasicRemote().sendText("injector: " + injector.hashCode());
			connection.close();
		} catch (IOException ignored) {}
	}
}
