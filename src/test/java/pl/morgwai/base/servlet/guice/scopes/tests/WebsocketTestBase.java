// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.net.CookieManager;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.WebSocketContainer;

import org.eclipse.jetty.websocket.javax.client.JavaxWebSocketClientContainerProvider;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.junit.*;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



public abstract class WebsocketTestBase {



	protected CookieManager cookieManager;
	protected org.eclipse.jetty.client.HttpClient wsHttpClient;
	protected WebSocketContainer clientWebsocketContainer;



	@Before
	public void createWebsocketClientContainer() {
		cookieManager = new CookieManager();
		wsHttpClient = new org.eclipse.jetty.client.HttpClient();
		wsHttpClient.setCookieStore(cookieManager.getCookieStore());
		clientWebsocketContainer = JavaxWebSocketClientContainerProvider.getContainer(wsHttpClient);
	}



	@After
	public void shutdownClientContainer() throws Exception {
		final var jettyWsContainer = ((JavaxWebSocketContainer) clientWebsocketContainer);
		jettyWsContainer.stop();
		jettyWsContainer.destroy();
		wsHttpClient.stop();
		wsHttpClient.destroy();
	}



	/**
	 * Change the below value if you need logging:<br/>
	 * <code>INFO</code> will log server startup and shutdown diagnostics<br/>
	 * <code>FINE</code> will log every response/message received from the server.
	 */
	protected static Level LOG_LEVEL = Level.WARNING;

	protected static final Logger log =
			Logger.getLogger(WebsocketTestBase.class.getPackageName());
	protected static final Logger scopesLog =
			Logger.getLogger(GuiceServerEndpointConfigurator.class.getPackageName());
	protected static final Logger pingerLog =
			Logger.getLogger(WebsocketPingerService.class.getName());

	@BeforeClass
	public static void setupLogging() {
		try {
			LOG_LEVEL = Level.parse(System.getProperty(
					WebsocketTestBase.class.getPackageName() + ".level"));
		} catch (Exception ignored) {}
		log.setLevel(LOG_LEVEL);
		scopesLog.setLevel(LOG_LEVEL);
		pingerLog.setLevel(LOG_LEVEL);
		for (final var handler: Logger.getLogger("").getHandlers()) handler.setLevel(LOG_LEVEL);
	}
}
