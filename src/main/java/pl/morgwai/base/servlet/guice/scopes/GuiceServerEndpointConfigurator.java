// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
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

import com.google.inject.Injector;
import com.google.inject.Scope;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
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
 * <p>
 * <b>NOTE:</b> due to the way many debuggers work, it is <b>strongly</b> recommended for
 * {@link Object#toString() toString()} methods of {@code Endpoints} to work properly even when
 * called outside of any {@code Context}.</p>
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
	protected Injector injector;
	protected ContextTracker<ContainerCallContext> ctxTracker;



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
	 * {@link ServletContext#getAttribute(String) attributes}. Called either by
	 * {@link #GuiceServerEndpointConfigurator(ServletContext)} or by
	 * {@link #modifyHandshake(ServerEndpointConfig, HandshakeRequest, HandshakeResponse)} in case
	 * of container-created instances for {@code Endpoints} annotated with {@link ServerEndpoint}.
	 */
	protected void initialize(ServletContext appDeployment) {
		injector = (Injector) appDeployment.getAttribute(Injector.class.getName());
		try {
			ctxTracker = injector.getInstance(ServletModule.containerCallContextTrackerKey);
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



	/**
	 * Obtains an instance of {@code endpointClass} from {@link Injector#getInstance(Class) Guice}
	 * and creates a context-aware proxy for it.
	 * The proxy ensures that {@code Endpoint} lifecycle methods are executed within
	 * {@link WebsocketEventContext}, {@link WebsocketConnectionContext} and if an
	 * {@link HttpSession} is present, then also {@link HttpSessionContext}.
	 * @return a proxy for the newly created {@code endpointClass} instance.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		try {
			final var proxyClass = getProxyClass(endpointClass);
			final EndpointT endpointProxy = super.getEndpointInstance(proxyClass);
			final var endpointProxyHandler = new EndpointProxyHandler(
				getAdditionalDecorator(injector.getInstance(endpointClass)),
				ctxTracker
			);
			proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
					.set(endpointProxy, endpointProxyHandler);
			return endpointProxy;
		} catch (Exception e) {
			log.log(Level.SEVERE, "Endpoint instantiation failed", e);
			throw new InstantiationException(e.toString());
		}
	}



	static final String INVOCATION_HANDLER_FIELD_NAME =
			GuiceServerEndpointConfigurator.class.getPackageName().replace('.', '_')
					+ "_invocationHandler";



	/**
	 * Returns a dynamic class of a context-aware proxy for {@code endpointClass}.
	 * Exposed for proxy class pre-building in {@link javax.servlet.ServletContextListener}s.
	 */
	public <EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
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
			.name(
				GuiceServerEndpointConfigurator.class.getPackageName() + ".ProxyFor_"
						+ endpointClass.getName().replace('.', '_')
			)
			.defineField(
				INVOCATION_HANDLER_FIELD_NAME,
				EndpointProxyHandler.class,
				Visibility.PACKAGE_PRIVATE
			)
			.method(ElementMatchers.any())
				.intercept(InvocationHandlerAdapter.toField(INVOCATION_HANDLER_FIELD_NAME));
		final ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (annotation != null) proxyClassBuilder = proxyClassBuilder.annotateType(annotation);
		try (
			final var unloadedClass = proxyClassBuilder.make();
		) {
			return unloadedClass
				.load(
					GuiceServerEndpointConfigurator.class.getClassLoader(),
					ClassLoadingStrategy.Default.INJECTION
				)
				.getLoaded();
		}
	}

	/**
	 * Checks if {@code endpointClass} has all the required methods with appropriate
	 * {@code Endpoint} lifecycle annotations as specified by
	 * {@link #getRequiredEndpointMethodAnnotationTypes()} and if {@link OnOpen} annotated method
	 * has a {@link Session} param.
	 * @throws RuntimeException if the check fails.
	 */
	private void checkIfRequiredEndpointMethodsPresent(Class<?> endpointClass) {
		final var wantedMethodAnnotationTypes = getRequiredEndpointMethodAnnotationTypes();
		final var wantedAnnotationTypeIterator = wantedMethodAnnotationTypes.iterator();
		while (wantedAnnotationTypeIterator.hasNext()) {
			// remove annotation from wantedMethodAnnotationTypes each time a corresponding method
			// is found within endpointClass
			final var wantedAnnotationType = wantedAnnotationTypeIterator.next();
			for (var method: endpointClass.getMethods()) {
				if (method.isAnnotationPresent(wantedAnnotationType)) {
					wantedAnnotationTypeIterator.remove();
					if (
						wantedAnnotationType.equals(OnOpen.class)
						&& !Arrays.asList(method.getParameterTypes()).contains(Session.class)
					) {
						throw new RuntimeException("method annotated with @OnOpen must have a "
								+ Session.class.getName() + " param");
					}
					break;
				}
			}
		}
		if ( !wantedMethodAnnotationTypes.isEmpty()) {
			throw new RuntimeException("endpoint class must have a method annotated with @"
					+ wantedMethodAnnotationTypes.iterator().next().getSimpleName());
		}
	}



	/**
	 * Returns a set of annotations of {@code Endpoint} lifecycle methods that are required to be
	 * present in {@code Endpoint} classes using this configurator.
	 * By default a singleton of {@link OnOpen}. Subclasses may override this method if needed.
	 * Overriding methods should call {@code super} and add their required annotations to the
	 * obtained {@code Set} before returning it.
	 */
	protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
		final var requiredAnnotationTypes = new HashSet<Class<? extends Annotation>>(5);
		requiredAnnotationTypes.add(OnOpen.class);
		return requiredAnnotationTypes;
	}



	/**
	 * Stores into {@link ServerEndpointConfig#getUserProperties() user properties} the
	 * {@link HttpSession} associated with {@code request}.
	 * <p>
	 * For container-created {@code Configurator} instances using
	 * {@link #GuiceServerEndpointConfigurator() the param-less constructor} (as a result of
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
		ServletContext appDeployment = null;
		if (httpSession != null) {
			appDeployment = ((HttpSession) httpSession).getServletContext();
		} else {
			// try retrieving from appDeployments Map (appDeploymentPath -> appDeployment)
			final var requestPath = request.getRequestURI().getPath();
			final var appDeploymentPath = requestPath.substring(
					0, requestPath.lastIndexOf(config.getPath()));
			final var appDeploymentRef = appDeployments.get(appDeploymentPath);
			if (appDeploymentRef != null) appDeployment = appDeploymentRef.get();

			if (appDeployment == null) {
				// pick first non-null deployment and ask it for a reference to the desired one
				// (this should also cover cases when the desired appDeployment is matched by more
				// than 1 path (as described in ServletContext.getContextPath() javadoc) and request
				// comes to a non-primary path)
				try {
					ServletContext randomDeployment;
					final var deploymentIterator = appDeployments.values().iterator();
					do {
						randomDeployment = deploymentIterator.next().get();
					} while (randomDeployment == null);
					appDeployment = randomDeployment.getContext(appDeploymentPath);
					if (
						appDeployment == null  // access restricted by container
						|| appDeploymentPath.equals(appDeployment.getContextPath())
					) {
						final var noDeploymentForPathWarning = String.format(
							NO_DEPLOYMENT_FOR_PATH_WARNING,
							requestPath,
							appDeploymentPath.isBlank() ? "[rootApp]" : appDeploymentPath
						);
						log.severe(noDeploymentForPathWarning);
						System.err.println(noDeploymentForPathWarning);
						if (appDeployment == null) {
							throw new RuntimeException(noDeploymentForPathWarning);
						}
					}  // else: request to a non-primary path
				} catch (NoSuchElementException e) {
					final var noDeploymentsError = String.format(NO_DEPLOYMENTS_ERROR, requestPath);
					log.severe(noDeploymentsError);
					System.err.println(noDeploymentsError);
					throw new RuntimeException(noDeploymentsError);
				}
			}
		}
		// multiple Threads initializing the same Configurator, will set exactly the same values
		initialize(appDeployment);
		this.appDeployment = appDeployment;
	}

	static final String NO_DEPLOYMENT_FOR_PATH_WARNING = "could not find a deployment for the "
			+ "request path %s (calculated app deployment path: %s ), "
			+ "GuiceServerEndpointConfigurator.registerDeployment(...) probably wasn't called";
	static final String NO_DEPLOYMENTS_ERROR = "could not find *ANY* deployment when configuring "
			+ "Endpoint for the request path %s, "
			+ "GuiceServerEndpointConfigurator.registerDeployment(...) probably wasn't called";



	/**
	 * Subclasses may override this method to further customize {@code Endpoints}.
	 * {@link InvocationHandler#invoke(Object, Method, Object[])} method of the returned handler
	 * will be executed within websocket {@code  Contexts}. By default this method returns a handler
	 * that simply invokes the given method on {@code endpoint}.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	static final Logger log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
}



/**
 * Executes each call to its wrapped {@code Endpoint} within websocket {@code Contexts}.
 * Creates a new separate {@link WebsocketEventContext} for method invocation with links to the
 * current {@link WebsocketConnectionContext} and {@link HttpSessionContext} if it's present.
 */
class EndpointProxyHandler implements InvocationHandler {



	final InvocationHandler wrappedEndpoint;
	final ContextTracker<ContainerCallContext> ctxTracker;



	EndpointProxyHandler(
		InvocationHandler endpointToWrap,
		ContextTracker<ContainerCallContext> containerCallContextTracker
	) {
		this.wrappedEndpoint = endpointToWrap;
		this.ctxTracker = containerCallContextTracker;
	}



	// the below 3 are created/retrieved when onOpen(...) call is intercepted
	WebsocketConnectionProxy connectionProxy;
	WebsocketConnectionContext connectionCtx;
	HttpSession httpSession;



	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		// replace the original wsConnection (Session) arg with a ctx-aware proxy
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					if (connectionProxy == null) {
						// the first call to this Endpoint instance that has a Session param (most
						// commonly onOpen(...)) : proxy the intercepted Session, create a new
						// connectionCtx, retrieve the HttpSession from userProperties
						final var connection = (Session) args[i];
						final var userProperties = connection.getUserProperties();
						httpSession = (HttpSession) userProperties.get(HttpSession.class.getName());
						connectionProxy = WebsocketConnectionProxy.newProxy(connection, ctxTracker);
						connectionCtx = new WebsocketConnectionContext(connectionProxy);
					}
					args[i] = connectionProxy;
					break;
				}
			}
		}

		if (connectionCtx == null) {
			// the first call to this Endpoint instance and it is NOT onOpen(...) : this is usually
			// a call from a debugger, most usually toString(). Session has not been intercepted
			// yet, so contexts couldn't have been created: just call the method outside of contexts
			// and hope for the best...
			final var manualCallWarningMessage =
					proxy.getClass().getSimpleName() + '.' + method.getName() + MANUAL_CALL_WARNING;
			log.warning(manualCallWarningMessage);
			System.err.println(manualCallWarningMessage);
			return wrappedEndpoint.invoke(proxy, method, args);
		}

		// execute the method within contexts
		return new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
			() -> {
				try {
					return wrappedEndpoint.invoke(proxy, method, args);
				} catch (Error | Exception e) {
					throw e;
				} catch (Throwable neverHappens) {
					throw new Exception(neverHappens);  // result of mis-designed invoke() signature
				}
			}
		);
	}



	static final Logger log = Logger.getLogger(EndpointProxyHandler.class.getName());
	static final String MANUAL_CALL_WARNING = ": calling manually methods of Endpoints, that were "
			+ "designed to run within contexts, may lead to an OutOfScopeException";
}
