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

import com.google.inject.Injector;
import com.google.inject.Scope;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Obtains {@code Endpoint} instances from {@link Injector#getInstance(Class) Guice} and ensures
 * their methods
 * {@link WebsocketEventContext#executeWithinSelf(Runnable) run within websocket Contexts}.
 * This way, all dependencies are injected and {@link Scope}s from {@link ServletModule}
 * ({@link ServletModule#containerCallScope}, {@link ServletModule#websocketConnectionScope} and
 * {@link ServletModule#httpSessionScope}) work properly.
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
 * <p>(additionally
 * {@link #getProxyClass(Class) configurator.getProxyClass(MyProgrammaticEndpoint.class)} may be
 * called to pre-build a dynamic class of a context-aware proxy for {@code MyProgrammaticEndpoint})
 * </p>
 * <p>
 * To use this {@code Configurator} for @{@link ServerEndpoint} annotated {@code Endpoints}, the
 * following setup must be performed:</p>
 * <ol>
 *   <li>
 *     the app-wide {@link Injector} must be
 *     {@link ServletContext#setAttribute(String, Object) stored as a deployment attribute} under
 *     the {@link Class#getName() fully-qualified name} of {@link Injector} class in {@link
 *     javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
 *     contextInitialized(event)} method of app's {@link javax.servlet.ServletContextListener}.
 *   </li>
 *   <li>
 *     {@link #registerDeployment(ServletContext)} and {@link #deregisterDeployment(ServletContext)}
 *     static methods must be called respectively in {@link
 *     javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
 *     contextInitialized(event)} and {@link
 *     javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
 *     contextDestroyed(event)} methods of app's {@link javax.servlet.ServletContextListener}.
 *   </li>
 * </ol>
 * <p>
 * This allows container-created instances of this {@code Configurator} to obtain a reference to the
 * {@link Injector}. Note that if app's {@code Listener} extends
 * {@link GuiceServletContextListener}, the above setup is performed automatically.<br/>
 * Finally, {@code Endpoint} methods annotated with @{@link OnOpen} <b>must</b> have a
 * {@link Session} param.<br/>
 * After the above conditions are met, simply pass this class as
 * {@link ServerEndpoint#configurator() configurator} param of the annotation:</p>
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
 * <b>NOTE:</b> due to the way many debuggers work, it is <b>strongly</b> recommended for
 * {@link Object#toString() toString()} methods of {@code Endpoints} to work properly even when
 * called outside of any {@code Context}.</p>
 * @see GuiceServletContextListener
 */
public class GuiceServerEndpointConfigurator extends Configurator {



	/**
	 * {@link WeakReference} wrapping prevents leaks of {@link ServletContext appDeployments} if
	 * {@link #deregisterDeployment(ServletContext)} is not called. Empty {@link WeakReference}
	 * objects together with their path keys will still be leaked though, but that's very little
	 * comparing to whole {@link ServletContext appDeployments}.
	 */
	static final ConcurrentMap<String, WeakReference<ServletContext>> appDeployments =
			new ConcurrentHashMap<>(5);



	/**
	 * Registers {@code appDeployment} for container-created
	 * {@code Configurator} instances to call {@link #initialize(ServletContext)}.
	 * <p>
	 * This method is called automatically by
	 * {@link GuiceServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)},
	 * it must be called manually in apps that don't use it.</p>
	 * @see #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)
	 */
	public static void registerDeployment(ServletContext appDeployment) {
		appDeployments.put(appDeployment.getContextPath(), new WeakReference<>(appDeployment));
	}



	/**
	 * Removes a reference to {@code appDeployment}.
	 * <p>
	 * This method is called automatically by
	 * {@link GuiceServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)},
	 * it must be called manually in apps that don't use it.</p>
	 */
	public static void deregisterDeployment(ServletContext appDeployment) {
		final var deregisteredDeployment = appDeployments.remove(appDeployment.getContextPath());
		if (deregisteredDeployment != null) {
			deregisteredDeployment.clear();
		} else {
			log.warning("attempting to deregister unregistered deployment with path "
					+ appDeployment.getContextPath());
		}
	}



	protected volatile ServletContext appDeployment;
	protected GuiceEndpointConfigurator backingConfigurator;



	/**
	 * Used by the container to create {@code Configurators} for {@link ServerEndpoint} annotated
	 * {@code Endpoints}.
	 * {@link #initialize(ServletContext)} will be called by the 1st invocation of
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)}.
	 */
	public GuiceServerEndpointConfigurator() {}



	/**
	 * Creates {@link GuiceServletContextListener} managed instance for
	 * {@link GuiceServletContextListener#addEndpoint(Class, String) programmatic Endpoints}.
	 * Calls {@link #initialize(ServletContext)} right away.
	 */
	public GuiceServerEndpointConfigurator(ServletContext appDeployment) {
		this.appDeployment = appDeployment;
		initialize(appDeployment);
	}



	/**
	 * Initializes this instance member fields with references from {@code appDeployment}
	 * {@link ServletContext#getAttribute(String) attributes}.
	 * Called either by {@link #GuiceServerEndpointConfigurator(ServletContext)} or by
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} in case
	 * of container-created instances for {@code Endpoints} annotated with {@link ServerEndpoint}.
	 */
	protected void initialize(ServletContext appDeployment) {
		final var injector = (Injector) appDeployment.getAttribute(Injector.class.getName());
		try {
			backingConfigurator = newGuiceEndpointConfigurator(
				injector,
				injector.getInstance(ServletModule.containerCallContextTrackerKey)
			);
		} catch (NullPointerException e) {
			throw new RuntimeException(
					"deployment attribute \"" + Injector.class.getName() + "\" not present");
		}
		// In case of container-created Configurator instances `this.appDeployment` is used as a
		// marker indicating whether initialize(...) has been called by modifyHandshake(...) (see
		// below) and as subclasses may override initialize(...) adding additional steps *after*
		// `super.initialize(...)`, we cannot do `this.appDeployment = appDeployment` here as then
		// some Threads could see half-initialized instances as fully initialized.
	}

	// todo: javadoc
	protected GuiceEndpointConfigurator newGuiceEndpointConfigurator(
		Injector injector,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		return new GuiceEndpointConfigurator(injector, ctxTracker) {
			@Override
			protected <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
					throws InstantiationException, InvocationTargetException {
				return GuiceServerEndpointConfigurator.this.createEndpointProxyInstance(proxyClass);
			}
		};
	}

	// todo: javadoc
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
	 * Obtains a server instance of {@code endpointClass} from
	 * {@link Injector#getInstance(Class) Guice} and creates a context-aware proxy for it.
	 * The proxy ensures that {@code Endpoint} lifecycle methods are executed within
	 * {@link WebsocketEventContext}, {@link WebsocketConnectionContext} and if an
	 * {@link HttpSession} is present, then also {@link HttpSessionContext}.
	 * @return a proxy for the newly created {@code endpointClass} instance.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		try {
			return backingConfigurator.getProxiedEndpointInstance(endpointClass);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Endpoint instantiation failed", e);
			if (e instanceof IllegalArgumentException) e.printStackTrace(); // signal obvious bug
			throw new InstantiationException(e.toString());
		}
	}



	/**
	 * Returns a dynamic class of a context-aware proxy for {@code endpointClass}.
	 * Exposed for proxy class pre-building in {@link javax.servlet.ServletContextListener}s.
	 */
	public <EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
		return backingConfigurator.getProxyClass(endpointClass);
	}



	/**
	 * Stores into {@link ServerEndpointConfig#getUserProperties() user properties} the
	 * {@link HttpSession} associated with {@code request}.
	 * <p>
	 * For container-created {@code Configurator} instances using
	 * {@link #GuiceServerEndpointConfigurator() the paramless constructor} (as a result of
	 * providing this class as a {@link ServerEndpoint#configurator()} argument for some annotated
	 * {@code Endpoint} class), this method will also call {@link #initialize(ServletContext)} on
	 * its first invocation.</p>
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
			this.appDeployment = appDeployment;
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
		// (this should also cover cases when the desired deployment is matched by more than 1 path
		// (as described in ServletContext.getContextPath() javadoc) and request comes to a
		// non-primary path)
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
				appDeploymentPath.isBlank() ? "[rootApp]" : appDeploymentPath
			);
			log.severe(deploymentNotFoundMessage);
			System.err.println(deploymentNotFoundMessage);
			if (appDeployment == null) throw new NoSuchElementException(deploymentNotFoundMessage);
		}
		return appDeployment;
	}

	static final String DEPLOYMENT_NOT_FOUND_MESSAGE = "could not find a deployment for the "
			+ "request path %s (calculated app deployment path: %s ), "
			+ "GuiceServerEndpointConfigurator.registerDeployment(...) probably wasn't called";



	static final Logger log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
}
