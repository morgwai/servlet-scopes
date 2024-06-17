// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.util.Arrays;

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.LifeCycle;
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

		final var unregisteredDeploymentAppHandler =
				new ServletContextHandler(ServletContextHandler.SESSIONS);
		unregisteredDeploymentAppHandler.setDisplayName("unregisteredDeploymentApp");
		unregisteredDeploymentAppHandler.setContextPath(
				MultiAppServer.UNREGISTERED_DEPLOYMENT_APP_PATH);
		unregisteredDeploymentAppHandler.addEventListener(new ManualServletContextListener(false));
		JavaxWebSocketServletContainerInitializer.configure(
			unregisteredDeploymentAppHandler,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
			}
		);

		final var deployments = new ContextHandlerCollection();
		deployments.addHandler(testAppHandler);
		deployments.addHandler(secondAppHandler);
		deployments.addHandler(unregisteredDeploymentAppHandler);
		setHandler(deployments);

		addEventListener(new LifeCycle.Listener() {
			@Override public void lifeCycleStopped(LifeCycle event) {
				destroy();
			}
		});		start();
		try {
			this.port = Arrays.stream(getConnectors())
				.filter(NetworkConnector.class::isInstance)
				.findFirst()
				.map(NetworkConnector.class::cast)
				.map(NetworkConnector::getLocalPort)
				.orElseThrow();
		} catch (Throwable e) {
			stop();
			throw e;
		}
	}



	static final String URL_PREFIX = "ws://localhost:";

	@Override
	public String getAppWebsocketUrl() {
		return URL_PREFIX + port + Server.APP_PATH;
	}

	@Override
	public String getSecondAppWebsocketUrl() {
		return URL_PREFIX + port + MultiAppServer.SECOND_APP_PATH;
	}

	@Override
	public String getUnregisteredDeploymentAppWebsocketUrl() {
		return URL_PREFIX + port + MultiAppServer.UNREGISTERED_DEPLOYMENT_APP_PATH;
	}



	@Override
	public void shutdown() throws Exception {
		stop();
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
		server.setStopAtShutdown(true);
		server.join();
		System.out.println("exiting, bye!");
	}

	public static final String PORT_ENVVAR = "SCOPES_SAMPLE_PORT";
	public static final int DEFAULT_PORT = 8080;
}
