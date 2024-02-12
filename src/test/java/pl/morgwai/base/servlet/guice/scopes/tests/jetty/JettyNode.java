// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import javax.servlet.http.*;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.session.*;
import org.eclipse.jetty.servlet.ServletContextHandler;
import pl.morgwai.base.servlet.guice.scopes.GuiceServletContextListener;
import pl.morgwai.base.servlet.guice.scopes.HttpSessionContext;



/**
 * Jetty server using {@link SessionDataStore HttpSession replication}, with a
 * {@link NodeInfoServlet Servlet}, that outputs some data
 * {@link HttpSession#setAttribute(String, Object) stored} and {@link HttpSessionContext scoped} to
 * {@link HttpSession}s.
 */
public class JettyNode extends org.eclipse.jetty.server.Server {



	public static final String APP_PATH = "/test";
	public static final String NODE_INFO_SERVLET_PATH = "/nodeInfo";
	public static final String NODE_ID_ATTRIBUTE = "nodeId";

	public static final String SESSION_ID_PROPERTY = "sessionId";
	public static final String SESSION_NODE_ID_PROPERTY = "sessionStoredNodeId";
	public static final String CONTEXT_NODE_ID_PROPERTY = "contextStoredNodeId";
	public static final String NON_SERIALIZABLE_CONTEXT_NODE_ID_PROPERTY =
			"nonSerializableContextStoredNodeId";

	public int getPort() { return port; }
	final int port;



	public JettyNode(
		int port,
		String nodeId,
		SessionDataStore sessionStore,
		boolean customSerialization
	) throws Exception {
		super(port);
		new DefaultSessionIdManager(this).setWorkerName(nodeId);

		final var testAppHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		testAppHandler.setDisplayName("testApp");
		testAppHandler.setContextPath(APP_PATH);
		testAppHandler.setInitParameter(NODE_ID_ATTRIBUTE, nodeId);
		testAppHandler.setInitParameter(
			HttpSessionContext.CUSTOM_SERIALIZATION_PARAM,
			String.valueOf(customSerialization)
		);
		testAppHandler.addEventListener(new ServletContextListener());
		setHandler(testAppHandler);

		final var sessionCache = new DefaultSessionCache(testAppHandler.getSessionHandler());
		sessionCache.setEvictionPolicy(60);
		sessionCache.setFlushOnResponseCommit(true);
		sessionCache.setSessionDataStore(sessionStore);
		testAppHandler.getSessionHandler().setSessionCache(sessionCache);

		start();
		this.port = Arrays.stream(getConnectors())
			.filter(NetworkConnector.class::isInstance)
			.map(NetworkConnector.class::cast)
			.map(NetworkConnector::getLocalPort)
			.findFirst()
			.orElseThrow();
	}



	public static class ServletContextListener extends GuiceServletContextListener {



		@Override protected LinkedList<Module> configureInjections() {
			final var modules = new LinkedList<Module>();
			modules.add((binder) -> {
				binder.bind(NonSerializableObject.class)
					.toProvider(() -> new NonSerializableObject(
							appDeployment.getInitParameter(NODE_ID_ATTRIBUTE)))
					.in(httpSessionScope);
				binder.bind(String.class)
					.annotatedWith(Names.named(NODE_ID_ATTRIBUTE))
					.toProvider(() -> appDeployment.getInitParameter(NODE_ID_ATTRIBUTE))
					.in(httpSessionScope);
			});
			return modules;
		}



		@Override protected void configureServletsFiltersEndpoints() throws Exception {
			addServlet(
				NodeInfoServlet.class.getSimpleName(),
				NodeInfoServlet.class,
				NODE_INFO_SERVLET_PATH
			);
		}
	}



	public static class NonSerializableObject {
		public final String value;
		public NonSerializableObject(String value) { this.value = value; }
	}



	/**
	 * {@link #doGet(HttpServletRequest, HttpServletResponse) GET} outputs to
	 * {@link HttpServletResponse} in
	 * {@link Properties#store(java.io.OutputStream, String) Properties format} the following data:
	 * <ul>
	 *   <li>{@link HttpSession#getId()} at {@link #SESSION_ID_PROPERTY}</li>
	 *   <li>
	 *     {@link #NODE_ID_ATTRIBUTE}
	 *     {@link HttpSession#getAttribute(String) session attribuute} at
	 *     {@link #SESSION_NODE_ID_PROPERTY}.<br/>
	 *     If the {@link HttpSession#isNew() HttpSession is new}, then {@code "null"} is output and
	 *     the {@link HttpSession#setAttribute(String, Object) attribute is set} for subsequent
	 *     requests to the value of {@link #NODE_ID_ATTRIBUTE}
	 *     {@link javax.servlet.ServletContext#getInitParameter(String) deployment init-param}
	 * 	   containing the id of the {@link JettyNode} serving the current request.
	 *   </li>
	 *   <li>
	 *     {@link HttpSessionContext HttpSession-scoped} value of {@link #NODE_ID_ATTRIBUTE}
	 *     {@link javax.servlet.ServletContext#getInitParameter(String) deployment init-param}
	 *     at {@link #CONTEXT_NODE_ID_PROPERTY}.
	 *   </li>
	 *   <li>
	 *     {@link HttpSessionContext HttpSession-scoped} value of {@link #NODE_ID_ATTRIBUTE}
	 *     {@link javax.servlet.ServletContext#getInitParameter(String) deployment init-param}
	 *     wrapped with a {@link NonSerializableObject} at
	 *     {@link #NON_SERIALIZABLE_CONTEXT_NODE_ID_PROPERTY}.
	 *   </li>
	 * </ul>
	 */
	public static class NodeInfoServlet extends HttpServlet {

		@Inject @Named(NODE_ID_ATTRIBUTE) Provider<String> nodeIdProvider;
		@Inject Provider<NonSerializableObject> nonSerializableNodeIdProvider;



		@Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws IOException {
			final var session = request.getSession();
			final var nodeId = request.getServletContext().getInitParameter(NODE_ID_ATTRIBUTE);
			final String sessionStoredNodeId;
			if (session.isNew()) {
				sessionStoredNodeId = null;
				session.setAttribute(NODE_ID_ATTRIBUTE, nodeId);
			} else {
				sessionStoredNodeId = (String) session.getAttribute(NODE_ID_ATTRIBUTE);
			}
			final var outputData = new Properties();
			outputData.setProperty(SESSION_ID_PROPERTY, session.getId());
			outputData.setProperty(SESSION_NODE_ID_PROPERTY, String.valueOf(sessionStoredNodeId));
			outputData.setProperty(CONTEXT_NODE_ID_PROPERTY, nodeIdProvider.get());
			outputData.setProperty(
				NON_SERIALIZABLE_CONTEXT_NODE_ID_PROPERTY,
				nonSerializableNodeIdProvider.get().value
			);
			try (
				final var output = response.getOutputStream();
			) {
				outputData.store(output, null);
			}
		}
	}



	public static void main(String[] args) throws Exception {
		int port;
		try {
			port = Integer.parseInt(args[0]);
		} catch (Exception e) {
			port = DEFAULT_PORT;
		}

		final var nodeId = (args.length > 2)
				? args[2]
				: InetAddress.getLocalHost().getHostName() + (
					(port != 0)
							? ":" + port
							: "_pid" + ProcessHandle.current().pid()
				);

		final var sessionFolder = (args.length > 2)
				? args[2] : ("/tmp/" + JettyNode.class.getPackageName() + ".sessions");
		final var sessionStore = new FileSessionDataStore();
		sessionStore.setStoreDir(new File(sessionFolder));
		sessionStore.setGracePeriodSec(10);
		sessionStore.setSavePeriodSec(0);

		final var customSerialization = args.length > 3 && Boolean.parseBoolean(args[3]);

		final var node = new JettyNode(port, nodeId, sessionStore, customSerialization);
		node.setStopAtShutdown(true);
		node.join();
		node.destroy();
		System.out.println("exiting, bye!");
	}

	public static final int DEFAULT_PORT = 8080;
}
