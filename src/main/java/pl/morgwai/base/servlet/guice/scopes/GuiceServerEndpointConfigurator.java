// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.*;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import com.google.inject.*;

import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;



/**
 * {@link GuiceEndpointConfigurator#getProxiedEndpointInstance(Class)
 * Obtains server Endpoint instances} from a {@link GuiceEndpointConfigurator}.
 * To enable this {@code Configurator}, a {@link ServletWebsocketModule} must be passed to
 * {@link Guice#createInjector(com.google.inject.Module...)} first.
 * <p>
 * To use this {@code Configurator} for programmatically added {@code Endpoints}, create an instance
 * using {@link #GuiceServerEndpointConfigurator(ServletContext)} and pass it to the
 * {@link ServerEndpointConfig.Builder#configurator(Configurator) config}:</p>
 * <pre>{@code
 * final var configurator = new GuiceServerEndpointConfigurator(servletContext);
 * websocketContainer.addEndpoint(
 *     ServerEndpointConfig.Builder
 *         .create(MyProgrammaticEndpoint.class, "/websocket/programmatic")
 *         .configurator(configurator)
 *         .build()
 * );}</pre>
 * <p>
 * Additionally
 * {@link #getProxyClass(Class) configurator.getProxyClass(MyProgrammaticEndpoint.class)} may be
 * called to pre-build a dynamic class of a context-aware proxy for {@code MyProgrammaticEndpoint}.
 * <br/>
 * A single {@code GuiceServerEndpointConfigurator} instance may be shared among multiple
 * {@link ServerEndpointConfig}s.</p>
 * <p>
 * To use this {@code Configurator} for @{@link ServerEndpoint} annotated {@code Endpoints}, simply
 * pass this class as {@link ServerEndpoint#configurator() configurator} param of the annotation:
 * </p>
 * <pre>
 * &#64;ServerEndpoint(
 *     value = "/websocket/annotated",
 *     configurator = GuiceServerEndpointConfigurator.class
 * )
 * public class MyAnnotatedEndpoint {
 *
 *     &#64;OnOpen public void onOpen(
 *         Session connection  // other optional params here...
 *     ) {
 *         // ...
 *     }
 *
 *     // other methods here...
 * }</pre>
 * <p>
 * At an app shutdown {@link #deregisterDeployment(ServletContext)} must be called to avoid resource
 * leaks (if an app uses {@link GuiceServletContextListener} then this is done automatically).</p>
 */
public class GuiceServerEndpointConfigurator extends Configurator {



	/**
	 * Maps {@link ServletContext#getContextPath() app deployment paths} to
	 * {@link ServletContext appDeployments} for container-created {@code Configurator} instances to
	 * obtain a reference to their respective {@link ServletContext appDeployments} in
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} to call
	 * {@link #initialize(ServletContext)}.
	 * {@link WeakReference} wrapping prevents leaks of {@link ServletContext appDeployments} if
	 * {@link #deregisterDeployment(ServletContext)} is not called. Empty {@link WeakReference}
	 * objects together with their path keys will still be leaked though, but that's very little
	 * comparing to whole {@link ServletContext appDeployments}.
	 */
	static final ConcurrentMap<String, WeakReference<ServletContext>> appDeployments =
			new ConcurrentHashMap<>(5);



	/**
	 * Stores {@code appDeployment} and {@code injector} in the static structures for
	 * container-created {@code Configurator} instances to initialize their references to these
	 * objects in their first invocation of
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)}.
	 * Stores {@code injector} as an {@link ServletContext#setAttribute(String, Object) attribute}
	 * of {@code appDeployment} under {@link Class#getName() fully-qualified name} of
	 * {@link Injector} class.
	 * <p>
	 * This method is called automatically during static injection requested by
	 * {@link ServletWebsocketModule}.</p>
	 */
	@Inject
	static void registerDeployment(ServletContext appDeployment, Injector injector) {
		appDeployment.setAttribute(Injector.class.getName(), injector);
		appDeployments.put(appDeployment.getContextPath(), new WeakReference<>(appDeployment));
	}



	/**
	 * Removes the reference to {@code appDeployment} and the associated {@link Injector} from the
	 * static structures.
	 * <p>
	 * This method is called automatically by
	 * {@link GuiceServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)},
	 * apps that don't use it must call this method manually during their shutdowns.</p>
	 */
	public static void deregisterDeployment(ServletContext appDeployment) {
		final var deregisteredDeployment = appDeployments.remove(appDeployment.getContextPath());
		if (deregisteredDeployment != null) {
			deregisteredDeployment.clear();
		} else {
			log.warning("attempting to deregister unregistered deployment with path \""
					+ appDeployment.getContextPath() + '"');
		}
	}



	volatile ServletContext appDeployment;
	GuiceEndpointConfigurator backingConfigurator;



	/**
	 * Used by websocket containers to create instances for @{@link ServerEndpoint} annotated
	 * {@code Endpoints} using this class as their
	 * {@link ServerEndpoint#configurator() configurator}.
	 * Such instances are not properly initialized until their first invocation of
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} when they
	 * obtain references to their {@link ServletContext appDeployment} and {@link Injector} from
	 * the static structures filled with {@link #registerDeployment(ServletContext, Injector)}.
	 */
	public GuiceServerEndpointConfigurator() {}



	/**
	 * Constructs and initializes an instance for
	 * {@link ServerEndpointConfig.Builder#configurator(Configurator) configuring} programmatic
	 * {@code Endpoints}.
	 */
	public GuiceServerEndpointConfigurator(ServletContext appDeployment) {
		initialize(appDeployment);
	}



	/**
	 * Initializes {@link #backingConfigurator} using {@link Injector} obtained from
	 * {@code appDeployment}'s {@link ServletContext#getAttribute(String) attribute}.
	 * Called either by {@link #GuiceServerEndpointConfigurator(ServletContext)} or by
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} in case
	 * of container-created instances for {@code Endpoints} annotated with {@link ServerEndpoint}.
	 * @throws NullPointerException if {@code appDeployment} does not contain an {@link Injector}
	 *     {@link ServletContext#getAttribute(String) attribute}.
	 * @throws ClassCastException if {@link Injector}
	 *     {@link ServletContext#getAttribute(String) attribute} of {@code appDeployment} is not an
	 *     {@link Injector}.
	 */
	void initialize(ServletContext appDeployment) {
		backingConfigurator = newGuiceEndpointConfigurator(
				(Injector) appDeployment.getAttribute(Injector.class.getName()));
		// this.appDeployment is used fot double-checked locking in modifyHandshake(...), so
		// the below assignment must be the last statement of this method
		this.appDeployment = appDeployment;
	}



	/**
	 * Creates a new {@link GuiceEndpointConfigurator} with
	 * {@link GuiceEndpointConfigurator#createEndpointProxyInstance(Class)
	 * createEndpointProxyInstance(Class)} method overridden to call
	 * {@link #createEndpointProxyInstance(Class)} of this {@code GuiceServerEndpointConfigurator}.
	 * Obtains all arguments required by {@link
	 * GuiceEndpointConfigurator#GuiceEndpointConfigurator(Injector,
	 * pl.morgwai.base.guice.scopes.ContextTracker) the constructor of
	 * GuiceEndpointConfigurator} from {@code injector}.
	 * @return by default an instance of anonymous subclass of {@link GuiceEndpointConfigurator},
	 *     may be overridden if more specialized implementation is needed.
	 * @throws NullPointerException if {@code injector} is {@code null}.
	 */
	protected GuiceEndpointConfigurator newGuiceEndpointConfigurator(Injector injector) {
		return new GuiceEndpointConfigurator(
			injector,
			injector.getInstance(WebsocketModule.ctxTrackerKey),
			injector.getInstance(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY)
		) {
			@Override
			protected <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
					throws InstantiationException, InvocationTargetException {
				return GuiceServerEndpointConfigurator.this.createEndpointProxyInstance(proxyClass);
			}
		};
	}

	/**
	 * Delegates a {@code proxyClass} instance creation to container's default {@link Configurator}.
	 */
	protected final <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
			throws InstantiationException, InvocationTargetException {
		try {
			return super.getEndpointInstance(proxyClass);
		} catch (InstantiationException | RuntimeException e) {
			throw e;
		} catch (Exception e) {  // workaround for containers using Class.newInstance()
			throw new InvocationTargetException(e);
		}
	}



	/**
	 * {@link GuiceEndpointConfigurator#getProxiedEndpointInstance(Class) Obtains} an instance of
	 * {@code endpointClass} wrapped with a
	 * {@link GuiceEndpointConfigurator#getProxyClass(Class) dynamic context-aware proxy}.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		try {
			return backingConfigurator.getProxiedEndpointInstance(endpointClass);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Endpoint instantiation failed", e);
			if (e instanceof IllegalArgumentException) e.printStackTrace(); // signal an obvious bug
			throw new InstantiationException(e.toString());
		}
	}



	/** See {@link GuiceEndpointConfigurator#getProxyClass(Class)}. */
	public <EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
		return backingConfigurator.getProxyClass(endpointClass);
	}



	/**
	 * Stores into {@link ServerEndpointConfig#getUserProperties() user properties} the
	 * {@link HttpSession} associated with {@code request}.
	 * <p>
	 * For container-created {@code Configurator} instances using
	 * {@link #GuiceServerEndpointConfigurator() the paramless constructor}, this method on its
	 * first invocation will also initialize the references to the respective
	 * {@link ServletContext appDeployment} and {@link Injector}.</p>
	 */
	@Override
	public void modifyHandshake(
		ServerEndpointConfig config,
		HandshakeRequest request,
		HandshakeResponse response
	) {
		final var httpSession = request.getHttpSession();
		if (httpSession != null) {
			config.getUserProperties().put(HttpSession.class.getName(), httpSession);
		}
		if (this.appDeployment != null) return;

		// uninitialized container-created Configurator instance using param-less constructor:
		// retrieve appDeployment and call initialize(...)
		synchronized (this) {
			if (this.appDeployment != null) return;
			final var appDeployment = getAppDeployment(config, request);
			initialize(appDeployment);
		}
	}

	ServletContext getAppDeployment(ServerEndpointConfig config, HandshakeRequest request) {
		final var httpSession = request.getHttpSession();
		if (httpSession != null) return ((HttpSession) httpSession).getServletContext();

		// try retrieving from appDeployments Map (appDeploymentPath -> appDeployment)
		final var requestPath = request.getRequestURI().getPath();
		final var appDeploymentPath = requestPath.substring(
				0, requestPath.lastIndexOf(config.getPath()));
		final var appDeploymentRef = appDeployments.get(appDeploymentPath);
		if (appDeploymentRef != null) return appDeploymentRef.get();

		// pick first non-null from appDeployments and ask it for a reference to the desired one
		// (for cases when the desired deployment is matched by more than 1 path (as described in
		// ServletContext.getContextPath() javadoc) and request comes to a non-primary path)
		ServletContext appDeployment = null;
		for (var deploymentRef: appDeployments.values()) {
			final var randomDeployment = deploymentRef.get();
			if (randomDeployment != null) {
				appDeployment = randomDeployment.getContext(appDeploymentPath);
				if (appDeployment != null) break;
			}
		}
		if (appDeployment == null || appDeploymentPath.equals(appDeployment.getContextPath())) {
			final var deploymentNotFoundMessage = String.format(
				DEPLOYMENT_NOT_FOUND_MESSAGE,
				requestPath,
				appDeploymentPath.isEmpty() ? "\"\" (root-app)" : '"' + appDeploymentPath + '"'
			);
			log.severe(deploymentNotFoundMessage);
			System.err.println(deploymentNotFoundMessage);
			if (appDeployment == null) throw new NoSuchElementException(deploymentNotFoundMessage);
		}
		return appDeployment;
	}

	static final String DEPLOYMENT_NOT_FOUND_MESSAGE = "could not find a deployment for the "
			+ "request path \"%s\" (calculated app deployment path: \"%s\")";



	static final Logger log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
}
