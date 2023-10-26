// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.annotation.Annotation;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
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
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Obtains {@code Endpoint} instances from {@link Injector#getInstance(Class) Guice} and decorates
 * methods to run within websocket contexts. This way, all dependencies are injected and
 * {@link Scope}s from {@link ServletModule} ({@link ServletModule#containerCallScope},
 * {@link ServletModule#websocketConnectionScope} and {@link ServletModule#httpSessionScope}) work
 * properly.
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
 * To use this {@code Configurator} for @{@link ServerEndpoint} annotated {@code Endpoints}, first
 * the app-wide {@link Injector} must be
 * {@link ServletContext#setAttribute(String, Object) stored as a deployment attribute} under the
 * {@link Class#getName() fully-qualified name} of {@link Injector} class in
 * {@link javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
 * contextInitialized(event)} method of app's {@link javax.servlet.ServletContextListener}.<br/>
 * Secondly, {@link #registerDeployment(ServletContext)} and
 * {@link #deregisterDeployment(ServletContext)} static methods must be called respectively in
 * {@link javax.servlet.ServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
 * contextInitialized(event)} and
 * {@link javax.servlet.ServletContextListener#contextDestroyed(javax.servlet.ServletContextEvent)
 * contextDestroyed(event)} methods of app's {@link javax.servlet.ServletContextListener}.<br/>
 * This way container-created instances (with
 * {@link #GuiceServerEndpointConfigurator() the param-less constructor}) of this
 * {@code Configurator} can obtain a reference to the {@link Injector}. Note that if app's
 * {@code Listener} extends {@link GuiceServletContextListener}, the whole above setup is
 * automatically taken care of.<br/>
 * Finally, {@code Endpoint} methods annotated with @{@link OnOpen} <b>must</b> have a
 * {@link Session} param.<br/>
 * After the above conditions are met, simply pass this class as
 * {@link ServerEndpoint#configurator() configurator} param of the annotation:</p>
 * <pre>
 * &#64;ServerEndpoint(
 *         value = "/websocket/annotated",
 *         configurator = GuiceServerEndpointConfigurator.class)
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
 * @see GuiceServletContextListener
 */
public class GuiceServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	/**
	 * {@link WeakReference} wrapping prevents leaks of {@link ServletContext appDeployments} if
	 * {@link #deregisterDeployment(ServletContext)} is not called. Empty {@link WeakReference}
	 * objects together with their path keys will still be leaked though, but that's very little
	 * comparing to whole {@link ServletContext appDeployments}.
	 */
	static final ConcurrentMap<String, WeakReference<ServletContext>> appDeployments =
			new ConcurrentHashMap<>(5);

	/**
	 * Registers {@code appDeployment} to be used by container-created
	 * {@code GuiceServerEndpointConfigurator} instances.
	 * <p>
	 * This method is called automatically by
	 * {@link GuiceServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)},
	 * it must be called manually in apps that don't use it.</p>
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
		appDeployments.remove(appDeployment.getContextPath()).clear();
	}



	protected volatile ServletContext appDeployment;
	protected Injector injector;
	protected ContextTracker<ContainerCallContext> containerCallContextTracker;



	/** Necessary for {@link ServerEndpoint} annotated {@code Endpoints}. */
	public GuiceServerEndpointConfigurator() {}

	/** For {@link GuiceServletContextListener} managed instance. */
	public GuiceServerEndpointConfigurator(ServletContext appDeployment) {
		this.appDeployment = appDeployment;
		initialize(appDeployment);
	}

	/**
	 * Initializes this instance member fields with references from {@code appDeployment}
	 * {@link ServletContext#getAttribute(String) attributes}. Called either by
	 * {@link #GuiceServerEndpointConfigurator(ServletContext)} or by
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} in case
	 * of container-created instances for {@code Endpoints} annotated with {@link ServerEndpoint}.
	 */
	protected void initialize(ServletContext appDeployment) {
		injector = (Injector) appDeployment.getAttribute(Injector.class.getName());
		try {
			containerCallContextTracker =
					injector.getInstance(ServletModule.containerCallContextTrackerKey);
		} catch (NullPointerException e) {
			throw new RuntimeException(
					"no \"" + Injector.class.getName() + "\" deployment attribute");
		}
	}



	/**
	 * Obtains an instance of {@code endpointClass} from {@link Injector#getInstance(Class) Guice}
	 * and creates a context-aware proxy for it, so that {@code Endpoint} lifecycle methods are
	 * executed within {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 * @return a proxy for the newly created {@code endpointClass} instance.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		try {
			final var proxyClass = getProxyClass(endpointClass);
			final EndpointT endpointProxy = super.getEndpointInstance(proxyClass);
			final var endpointDecorator = new EndpointProxyHandler(
				getAdditionalDecorator(injector.getInstance(endpointClass)),
				containerCallContextTracker
			);
			proxyClass.getDeclaredField(PROXY_DECORATOR_FIELD_NAME)
					.set(endpointProxy, endpointDecorator);
			return endpointProxy;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Endpoint instantiation failed", e);
			throw new InstantiationException(e.toString());
		}
	}

	static final String PROXY_DECORATOR_FIELD_NAME =
			GuiceServerEndpointConfigurator.class.getPackageName().replace('.', '_')
					+ "_invocationHandler";

	/**
	 * Exposed for proxy class pre-building in
	 * {@link GuiceServletContextListener#addEndpoint(Class, String)}.
	 */
	<EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
		@SuppressWarnings("unchecked")
		final Class<? extends EndpointT> proxyClass = (Class<? extends EndpointT>)
				proxyClasses.computeIfAbsent(endpointClass, this::createProxyClass);
		return proxyClass;
	}

	static final ConcurrentMap<Class<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();

	/**
	 * Creates a dynamic proxy class that delegates calls to the associated
	 * {@link EndpointProxyHandler} instance.
	 */
	<EndpointT> Class<? extends EndpointT> createProxyClass(Class<EndpointT> endpointClass) {
		if ( !Endpoint.class.isAssignableFrom(endpointClass)) {
			checkIfRequiredEndpointMethodsPresent(endpointClass);
		}
		DynamicType.Builder<EndpointT> proxyClassBuilder = new ByteBuddy()
			.subclass(endpointClass)
			.name(GuiceServerEndpointConfigurator.class.getPackageName() + ".ProxyFor_"
					+ endpointClass.getName().replace('.', '_'))
			.defineField(
					PROXY_DECORATOR_FIELD_NAME,
					EndpointProxyHandler.class,
					Visibility.PACKAGE_PRIVATE)
			.method(ElementMatchers.any())
					.intercept(InvocationHandlerAdapter.toField(PROXY_DECORATOR_FIELD_NAME));
		final ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (annotation != null) proxyClassBuilder = proxyClassBuilder.annotateType(annotation);
		try (
			final var unloadedClass = proxyClassBuilder.make();
		) {
			return unloadedClass
				.load(
					GuiceServerEndpointConfigurator.class.getClassLoader(),
					ClassLoadingStrategy.Default.INJECTION
				).getLoaded();
		}
	}

	/**
	 * Checks if {@code endpointClass} has all the required methods with appropriate
	 * {@code Endpoint} lifecycle annotations as specified by
	 * {@link #getRequiredEndpointMethodAnnotationTypes()}.
	 * @throws RuntimeException if the check fails.
	 */
	private void checkIfRequiredEndpointMethodsPresent(Class<?> endpointClass) {
		final var fugitiveMethodAnnotationTypes = getRequiredEndpointMethodAnnotationTypes();
		final var fugitiveMethodAnnotationTypesIterator = fugitiveMethodAnnotationTypes.iterator();
		while (fugitiveMethodAnnotationTypesIterator.hasNext()) {
			final var fugitiveAnnotationType = fugitiveMethodAnnotationTypesIterator.next();
			for (var method: endpointClass.getMethods()) {
				if (method.isAnnotationPresent(fugitiveAnnotationType)) {
					fugitiveMethodAnnotationTypesIterator.remove();
					if (
						fugitiveAnnotationType.equals(OnOpen.class)
						&& !Arrays.asList(method.getParameterTypes()).contains(Session.class)
					) {
						throw new RuntimeException("method annotated with @OnOpen must have a"
								+ " javax.websocket.Session param");
					}
					break;
				}
			}
		}
		if ( !fugitiveMethodAnnotationTypes.isEmpty()) {
			throw new RuntimeException("endpoint class must have a method annotated with @"
					+ fugitiveMethodAnnotationTypes.iterator().next().getSimpleName());
		}
	}

	/**
	 * Returns a set of annotations of {@code Endpoint} lifecycle methods that are required to be
	 * present in {@code Endpoint} classes using this configurator. By default a singleton of
	 * {@link OnOpen}. Subclasses may override this method if needed by calling {@code super} and
	 * adding their required annotations to the obtained set before returning it.
	 */
	protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
		final var result = new HashSet<Class<? extends Annotation>>(5);
		result.add(OnOpen.class);
		return result;
	}



	/**
	 * Stores into {@link ServerEndpointConfig#getUserProperties() user properties} the
	 * {@link HttpSession} associated with {@code request}.
	 * <p>
	 * For {@code Configurator} instances created by the container (as a result of providing this
	 * class as a {@link ServerEndpoint#configurator()} argument of some annotated {@code Endpoint}
	 * class), this method will also call {@link #initialize(ServletContext)} on its first
	 * invocation.</p>
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

		if (this.appDeployment == null) {
			// multiple threads initializing appDeployment reference concurrently in the same
			// Configurator instance, will set exactly the same values
			ServletContext appDeployment = null;
			if (httpSession != null) {
				appDeployment = ((HttpSession) httpSession).getServletContext();
			} else {
				final var requestPath = request.getRequestURI().getPath();
				final var appDeploymentPath = requestPath.substring(
						0, requestPath.lastIndexOf(config.getPath()));
				final var appDeploymentRef = appDeployments.get(appDeploymentPath);
				if (appDeploymentRef != null) appDeployment = appDeploymentRef.get();
				if (appDeployment == null) {
					final var message = String.format(
						NO_DEPLOYMENT_FOR_PATH,
						requestPath,
						(appDeploymentPath.isBlank() ? "[rootApp]" : appDeploymentPath)
					);
					log.severe(message);
					System.err.println(message);
					try {
						// pick first and hope for the best: this is guaranteed to work correctly
						// only if this is the only app using this Configurator in the given
						// ClassLoader (which is the default for standard war file deployments)
						appDeployment = appDeployments.values().iterator().next().get();
					} catch (NoSuchElementException e) {
						final var message2 = String.format(NO_DEPLOYMENTS, requestPath);
						log.severe(message2);
						System.err.println(message2);
						throw new RuntimeException(message2);
					}
				}
			}
			initialize(appDeployment);
			this.appDeployment = appDeployment;
		}
	}

	static final String NO_DEPLOYMENT_FOR_PATH = "could not find deployment for request path %s "
			+ "(calculated app deployment path: %s ), "
			+ "GuiceServerEndpointConfigurator.registerDeployment(...) probably wasn't called";
	static final String NO_DEPLOYMENTS = "could not find *ANY* deployment when configuring "
			+ "Endpoint for request path %s, "
			+ "GuiceServerEndpointConfigurator.registerDeployment(...) probably wasn't called";



	/**
	 * Subclasses may override this method to further customize {@code Endpoints}.
	 * {@link InvocationHandler#invoke(Object, Method, Object[])} method of the returned handler
	 * will be executed within {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 * By default it returns a handler that simply invokes the given method on {@code endpoint}.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	static final Logger log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
}



/**
 * Executes each call to the wrapped {@code Endpoint} instance within the current
 * {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
 */
class EndpointProxyHandler implements InvocationHandler {



	final InvocationHandler wrappedEndpoint;
	final ContextTracker<ContainerCallContext> containerCallContextTracker;



	EndpointProxyHandler(
		InvocationHandler endpointToWrap,
		ContextTracker<ContainerCallContext> containerCallContextTracker
	) {
		wrappedEndpoint = endpointToWrap;
		this.containerCallContextTracker = containerCallContextTracker;
	}



	// the below 2 are created/retrieved when onOpen(...) call is intercepted
	WebsocketConnectionContext connectionCtx;
	HttpSession httpSession;



	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		// replace the original wsConnection (Session) arg with a decorating wrapper
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					if (connectionCtx == null) {
						// the first call to this Endpoint instance that has a Session param (most
						// commonly onOpen(...)) : decorate the intercepted Session, create a new
						// connectionCtx, retrieve the HttpSession from userProperties
						final var decoratedConnection = new WebsocketConnectionDecorator(
								(Session) args[i], containerCallContextTracker);
						final var userProperties = decoratedConnection.getUserProperties();
						httpSession = (HttpSession) userProperties.get(HttpSession.class.getName());
						connectionCtx = new WebsocketConnectionContext(decoratedConnection);
						userProperties.put(
								WebsocketConnectionContext.class.getName(), connectionCtx);
					}
					args[i] = connectionCtx.getConnection();
					break;
				}
			}
		}

		// the first call to this Endpoint instance and it is NOT onOpen(...) : this is usually a
		// call from a debugger, most usually toString(). Session has not been intercepted yet, so
		// contexts couldn't have been created: just call the method outside of contexts and hope
		// for the best...
		if (connectionCtx == null) {
			log.warning(proxy.getClass().getSimpleName() + '.' + method.getName()
					+ MANUAL_CALL_WARNING);
			System.err.println(proxy.getClass().getSimpleName() + '.' + method.getName()
					+ MANUAL_CALL_WARNING);
			return wrappedEndpoint.invoke(proxy, method, args);
		}

		// execute the method within contexts
		final var eventCtx =
				new WebsocketEventContext(connectionCtx, httpSession, containerCallContextTracker);
		return eventCtx.executeWithinSelf(
			() -> {
				try {
					return wrappedEndpoint.invoke(proxy, method, args);
				} catch (Error | Exception e) {
					throw e;
				} catch (Throwable neverHappens) {
					throw new Exception(neverHappens);  // result mis-designed invoke() signature
				}
			}
		);
	}



	static final Logger log = Logger.getLogger(EndpointProxyHandler.class.getName());
	static final String MANUAL_CALL_WARNING = ": calling manually methods of Endpoints that were "
			+ "designed to run within contexts, may lead to an OutOfScopeException";
}
