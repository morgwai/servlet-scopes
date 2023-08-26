// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketServletContainerInitializer;



/** An embedded Jetty server with {@code Servlets} and {@code Endpoints} from this package. */
public class TestServer extends org.eclipse.jetty.server.Server {



	public static final String APP_PATH = "/test";
	public static final String SECOND_APP_PATH = "";  // root app



	public TestServer(int port) {
		super(port);

		final var testAppHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		testAppHandler.setDisplayName("testApp");
		testAppHandler.setContextPath(APP_PATH);
		testAppHandler.addEventListener(new ServletContextListener());
		JavaxWebSocketServletContainerInitializer.configure(
			testAppHandler,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(ExtendingEndpoint.class);
				websocketContainer.addEndpoint(OnOpenWithoutSessionParamEndpoint.class);
				websocketContainer.addEndpoint(PingingWithoutOnCloseEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
			}
		);

		final var secondAppHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		secondAppHandler.setDisplayName("secondApp");
		secondAppHandler.setContextPath(SECOND_APP_PATH);
		secondAppHandler.addEventListener(new ManualServletContextListener());
		JavaxWebSocketServletContainerInitializer.configure(
			secondAppHandler,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(ExtendingEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
			}
		);

		final var appCollection = new ContextHandlerCollection();
		appCollection.addHandler(testAppHandler);
		appCollection.addHandler(secondAppHandler);
		setHandler(appCollection);
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
