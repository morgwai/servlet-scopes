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

import static com.google.inject.name.Names.named;
import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;



/**
 * {@link GuiceEndpointConfigurator#getProxiedEndpointInstance(Class)
 * Obtains server Endpoint instances} from a {@link GuiceEndpointConfigurator}.
 * To use this {@code Configurator}, first either a {@link ServletWebsocketModule} or a
 * {@link StandaloneWebsocketServerModule} must be passed to
 * {@link Guice#createInjector(com.google.inject.Module...)}.
 * <p>
 * To use this {@code Configurator} for programmatically added {@code Endpoints}, create an instance
 * using {@link #GuiceServerEndpointConfigurator(Injector)} and pass it to the
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
 * At the app shutdown {@link #deregisterInjector(Injector)} must be called to avoid resource leaks
 * (in apps that  uses {@link GuiceServletContextListener} this is done automatically).</p>
 */
public class GuiceServerEndpointConfigurator extends Configurator {



	/**
	 * {@link com.google.inject.name.Named Binding name} for a {@link String} containing the root
	 * path of a given server app.
	 */
	public static final String APP_DEPLOYMENT_PATH_BINDING_NAME = "appDeploymentPath";
	/** {@code Key} for a {@link String} containing the root path of a given server app. */
	public static final Key<String> APP_DEPLOYMENT_PATH_KEY =
			Key.get(String.class, named(APP_DEPLOYMENT_PATH_BINDING_NAME));

	/**
	 * Maps {@link ServletContext#getContextPath() app deployment paths} to their
	 * {@link Injector}s for container-created {@code Configurator} instances to
	 * obtain a reference to their respective {@link Injector} in
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} to call
	 * {@link #initialize(Injector)}.
	 * {@link WeakReference} wrapping prevents leaks of {@link Injector}s if
	 * {@link #deregisterInjector(Injector)} is not called. Empty {@link WeakReference}s
	 * objects together with their path keys will still be leaked though, but that's very little
	 * comparing to whole {@link Injector}s.
	 */
	static final ConcurrentMap<String, WeakReference<Injector>> deploymentInjectors =
			new ConcurrentHashMap<>(5);



	/**
	 * Stores {@code injector} in {@link #deploymentInjectors}.
	 * This method is called automatically during static injection requested by a
	 * {@link ServletWebsocketModule} or a {@link StandaloneWebsocketServerModule}.
	 */
	@Inject
	static void registerInjector(Injector injector) {
		deploymentInjectors.put(
			injector.getInstance(APP_DEPLOYMENT_PATH_KEY),
			new WeakReference<>(injector)
		);
	}



	/**
	 * Removes {@code injector} from the static structures of this class.
	 * <p>
	 * This method is called automatically by
	 * {@link GuiceServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)},
	 * apps that don't use it must call this method manually during their shutdowns to prevent
	 * resource leaks.</p>
	 */
	public static void deregisterInjector(Injector injector) {
		final var appDeploymentPath = injector.getInstance(APP_DEPLOYMENT_PATH_KEY);
		final var deregisteredDeployment = deploymentInjectors.remove(appDeploymentPath);
		if (deregisteredDeployment != null) {
			deregisteredDeployment.clear();
		} else {
			log.warning("attempting to deregister an unregistered Injector for the app at \""
					+ appDeploymentPath + '"');
		}
	}



	volatile Injector injector;
	GuiceEndpointConfigurator backingConfigurator;



	/**
	 * Used by websocket containers to create instances for @{@link ServerEndpoint} annotated
	 * {@code Endpoints} using this class as their
	 * {@link ServerEndpoint#configurator() configurator}.
	 * Such instances are not properly initialized until their first invocation of
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} when they
	 * obtain references to their respective {@link Injector}s from the static structures of this
	 * class.
	 */
	public GuiceServerEndpointConfigurator() {}



	/**
	 * Constructs and initializes an instance for
	 * {@link ServerEndpointConfig.Builder#configurator(Configurator) configuring} programmatic
	 * {@code Endpoints}.
	 */
	public GuiceServerEndpointConfigurator(Injector injector) {
		initialize(injector);
	}



	/**
	 * Initializes {@link #backingConfigurator} using {@code injector}.
	 * Called either by {@link #GuiceServerEndpointConfigurator(Injector)} or by
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} in case
	 * of container-created instances for {@code Endpoints} annotated with {@link ServerEndpoint}.
	 */
	void initialize(Injector injector) {
		backingConfigurator = newGuiceEndpointConfigurator(injector);
		// this.injector is used fot double-checked locking in modifyHandshake(...), so
		// the below assignment must be the last statement of this method
		this.injector = injector;
	}



	/**
	 * Creates a new {@link GuiceEndpointConfigurator} with
	 * {@link GuiceEndpointConfigurator#createEndpointProxyInstance(Class)
	 * createEndpointProxyInstance(Class)} method overridden to call
	 * {@link #createEndpointProxyInstance(Class)} of this {@code GuiceServerEndpointConfigurator}.
	 * Obtains all arguments required by {@link
	 * GuiceEndpointConfigurator#GuiceEndpointConfigurator(Injector,
	 * pl.morgwai.base.guice.scopes.ContextTracker, boolean) the constructor of
	 * GuiceEndpointConfigurator} from {@code injector}.
	 * @return by default an instance of anonymous subclass of {@link GuiceEndpointConfigurator},
	 *     may be overridden if more specialized implementation is needed.
	 * @throws NullPointerException if {@code injector} is {@code null}.
	 */
	protected GuiceEndpointConfigurator newGuiceEndpointConfigurator(Injector injector) {
		return new GuiceEndpointConfigurator(
			injector,
			injector.getInstance(WebsocketModule.CTX_TRACKER_KEY),
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
	 * first invocation will also initialize the reference to the app {@link Injector}.</p>
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
		if (this.injector != null) return;

		// uninitialized container-created Configurator instance using param-less constructor:
		// retrieve the Injector and call initialize(...)
		synchronized (this) {
			if (this.injector != null) return;
			final var injector = getInjector(config, request);
			initialize(injector);
		}
	}

	static Injector getInjector(ServerEndpointConfig config, HandshakeRequest request) {
		final var httpSession = request.getHttpSession();
		if (httpSession != null) {
			return getInjectorFromDeployment(((HttpSession) httpSession).getServletContext());
		}

		// try retrieving from deploymentInjectors Map (appDeploymentPath -> Injector)
		final var requestPath = request.getRequestURI().getPath();
		final var appDeploymentPath = requestPath.substring(
				0, requestPath.lastIndexOf(config.getPath()));
		final var injectorRef = deploymentInjectors.get(appDeploymentPath);
		if (injectorRef != null) {
			System.gc();  // flush WeakReferences from deploymentInjectors
			final var injector = injectorRef.get();
			if (injector == null) throw new IllegalStateException(INJECTOR_REF_LOST_MESSAGE);
			return injector;
		}

		// pick first non-null from deploymentInjectors, get its ServletContext, ask it for a
		// reference to the ServletContext of this app and get the Injector from its attribute
		// (for cases when the desired deployment is matched by more than 1 path (as described in
		// ServletContext.getContextPath() javadoc) and request comes to a non-primary path)
		ServletContext appDeployment = null;
		for (var randomInjectorRef: deploymentInjectors.values()) {
			final var randomInjector = randomInjectorRef.get();
			if (randomInjector != null) {
				final var randomDeployment = randomInjector.getInstance(ServletContext.class);
				appDeployment = randomDeployment.getContext(appDeploymentPath);
				if (appDeployment != null) break;
			}
		}
		if (appDeployment == null || appDeploymentPath.equals(appDeployment.getContextPath())) {
			final var deploymentNotFoundMessage = String.format(
				INJECTOR_NOT_FOUND_MESSAGE,
				requestPath,
				appDeploymentPath.isEmpty() ? "\"\" (root-app)" : '"' + appDeploymentPath + '"'
			);
			log.severe(deploymentNotFoundMessage);
			System.err.println(deploymentNotFoundMessage);
			if (appDeployment == null) throw new NoSuchElementException(deploymentNotFoundMessage);
		}
		return getInjectorFromDeployment(appDeployment);
	}

	static Injector getInjectorFromDeployment(ServletContext appDeployment) {
		return (Injector) appDeployment.getAttribute(Injector.class.getName());
	}

	static final String INJECTOR_REF_LOST_MESSAGE = "lost a reference to the Injector, the app "
			+  "probably does not call GuiceServerEndpointConfigurator.deregisterInjector(injector)"
			+ " at its shutdown";
	static final String INJECTOR_NOT_FOUND_MESSAGE = "could not find an Injector for the "
			+ "request path \"%s\" (calculated app deployment path: %s), either a "
			+ "ServletWebsocketModule or a StandaloneWebsocketServerModule must be passed to "
			+ "Guice.createInjector(...)";



	static final Logger log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
}
