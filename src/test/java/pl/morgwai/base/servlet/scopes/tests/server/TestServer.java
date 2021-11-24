// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketServletContainerInitializer;



public class TestServer extends org.eclipse.jetty.server.Server {



	public static final String APP_PATH = "/test";



	public TestServer(int port) {
		super(port);
		var appHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		appHandler.setContextPath(APP_PATH);
		appHandler.addEventListener(new ServletContextListener());
		setHandler(appHandler);
		JavaxWebSocketServletContainerInitializer.configure(
			appHandler,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(ExtendingEndpoint.class);
			}
		);
	}



	public static void main(String[] args) throws Exception {
		var port = DEFAULT_PORT;
		try {
			port = Integer.parseInt(args[0]);
		} catch (Exception e) {
			try {
				port = Integer.parseInt(System.getenv(PORT_ENVVAR));
			} catch (Exception ignored) {}
		}
		final var server = new TestServer(port);
		server.setStopAtShutdown(true);
		server.start();
		server.join();
	}

	public static final String PORT_ENVVAR = "SCOPES_SAMPLE_PORT";
	public static final int DEFAULT_PORT = 8080;
}
