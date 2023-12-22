// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.util.Arrays;

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketServletContainerInitializer;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;



/** An embedded Jetty server with {@code Servlets} and {@code Endpoints} from this package. */
public class JettyServer extends org.eclipse.jetty.server.Server
		implements MultiAppServer {



	public int getPort() { return port; }
	final int port;



	public JettyServer(int port) throws Exception {
		super(port);

		final var testAppHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		testAppHandler.setDisplayName("testApp");
		testAppHandler.setContextPath(Server.APP_PATH);
		testAppHandler.addEventListener(new ServletContextListener());
		JavaxWebSocketServletContainerInitializer.configure(
			testAppHandler,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingEndpoint.class);
				websocketContainer.addEndpoint(OnOpenWithoutSessionParamEndpoint.class);
				websocketContainer.addEndpoint(PingingWithoutOnCloseEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(BroadcastEndpoint.class);
			}
		);

		final var secondAppHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		secondAppHandler.setDisplayName("secondApp");
		secondAppHandler.setContextPath(MultiAppServer.SECOND_APP_PATH);
		secondAppHandler.addEventListener(new ManualServletContextListener());
		JavaxWebSocketServletContainerInitializer.configure(
			secondAppHandler,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
			}
		);

		final var appCollection = new ContextHandlerCollection();
		appCollection.addHandler(testAppHandler);
		appCollection.addHandler(secondAppHandler);
		setHandler(appCollection);

		setStopAtShutdown(true);
		start();
		this.port = Arrays.stream(getConnectors())
			.filter(NetworkConnector.class::isInstance)
			.findFirst()
			.map(NetworkConnector.class::cast)
			.map(NetworkConnector::getLocalPort)
			.orElseThrow();
	}



	@Override
	public String getAppWebsocketUrl() {
		return "ws://localhost:" + port + Server.APP_PATH;
	}



	@Override
	public String getSecondAppWebsocketUrl() {
		return "ws://localhost:" + port + MultiAppServer.SECOND_APP_PATH;
	}



	@Override
	public void stopz() throws Exception {
		stop();
		join();
		destroy();
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
		final var server = new JettyServer(port);
		server.join();
		server.destroy();
		System.out.println("exiting, bye!");
	}

	public static final String PORT_ENVVAR = "SCOPES_SAMPLE_PORT";
	public static final int DEFAULT_PORT = 8080;
}
