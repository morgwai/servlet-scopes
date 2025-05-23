// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.util.Arrays;

import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ErrorPageErrorHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import pl.morgwai.base.servlet.guice.tests.servercommon.*;



/** An embedded Jetty server with {@code Servlets} and {@code Endpoints} from this package. */
public class JettyServer extends org.eclipse.jetty.server.Server
		implements MultiAppServer {



	public int getPort() { return port; }
	final int port;



	public JettyServer(int port, String name) throws Exception {
		super(port);

		final var testDeployment = new ServletContextHandler(ServletContextHandler.SESSIONS);
		testDeployment.setDisplayName(name + "TestApp");
		testDeployment.setContextPath(Server.TEST_APP_PATH);
		testDeployment.addEventListener(new ServletContextListener());
		final var errorMapper = new ErrorPageErrorHandler();
		errorMapper.addErrorPage(404, ErrorDispatchingServlet.ERROR_HANDLER_PATH);
		testDeployment.setErrorHandler(errorMapper);
		JakartaWebSocketServletContainerInitializer.configure(
			testDeployment,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedMethodOverridingEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingProgrammaticEndpoint.class);
				websocketContainer.addEndpoint(OnOpenWithoutSessionParamEndpoint.class);
				websocketContainer.addEndpoint(PingingWithoutOnCloseEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(BroadcastEndpoint.class);
			}
		);

		final var secondDeployment = new ServletContextHandler(ServletContextHandler.SESSIONS);
		secondDeployment.setDisplayName(name + "SecondApp");
		secondDeployment.setContextPath(MultiAppServer.SECOND_APP_PATH);
		secondDeployment.addEventListener(new ManualServletContextListener());
		final var secondErrorMapper = new ErrorPageErrorHandler();
		secondErrorMapper.addErrorPage(404, ErrorDispatchingServlet.ERROR_HANDLER_PATH);
		secondDeployment.setErrorHandler(secondErrorMapper);
		JakartaWebSocketServletContainerInitializer.configure(
			secondDeployment,
			(servletContainer, websocketContainer) -> {
				websocketContainer.setDefaultMaxTextMessageBufferSize(1023);
				websocketContainer.addEndpoint(AnnotatedEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedMethodOverridingEndpoint.class);
				websocketContainer.addEndpoint(AnnotatedExtendingProgrammaticEndpoint.class);
				websocketContainer.addEndpoint(AppSeparationTestEndpoint.class);
				websocketContainer.addEndpoint(NoSessionAppSeparationTestEndpoint.class);
			}
		);

		final var deploymentHandlers = new ContextHandlerCollection();
		deploymentHandlers.addHandler(testDeployment);
		deploymentHandlers.addHandler(secondDeployment);
		setHandler(deploymentHandlers);

		addEventListener(new LifeCycle.Listener() {
			@Override public void lifeCycleStopped(LifeCycle event) {
				destroy();
			}
		});
		start();
		try {
			this.port = Arrays.stream(getConnectors())
				.filter(NetworkConnector.class::isInstance)
				.map(NetworkConnector.class::cast)
				.map(NetworkConnector::getLocalPort)
				.findFirst()
				.orElseThrow();
		} catch (Throwable e) {
			stop();
			throw e;
		}
	}



	static final String URL_PREFIX = "ws://localhost:";

	@Override
	public String getTestAppWebsocketUrl() {
		return URL_PREFIX + port + Server.TEST_APP_PATH;
	}

	@Override
	public String getSecondAppWebsocketUrl() {
		return URL_PREFIX + port + MultiAppServer.SECOND_APP_PATH;
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
		final var server = new JettyServer(port, "");
		server.setStopAtShutdown(true);
		server.join();
		System.out.println("exiting, bye!");
	}

	public static final String PORT_ENVVAR = "SCOPES_SAMPLE_PORT";
	public static final int DEFAULT_PORT = 8080;
}
