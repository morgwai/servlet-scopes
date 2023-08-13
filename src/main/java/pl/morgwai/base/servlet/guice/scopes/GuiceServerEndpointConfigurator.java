// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

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
 * Obtains {@code Endpoint} instances from {@link Injector#getInstance(Class) Guice} (so that
 * all dependencies are injected) and decorates them, so that lifecycle methods are executed within
 * {@link WebsocketConnectionContext} and {@link ContainerCallContext}.
 * <p>
 * In case of {@code Endpoints} annotated with @{@link ServerEndpoint}, this class should be used as
 * {@link ServerEndpoint#configurator() configurator} param of the annotation:</p>
 * <pre>
 * &#64;ServerEndpoint(
 *     value = "/websocket/mySocket",
 *     configurator = GuiceServerEndpointConfigurator.class)
 * public class MyEndpoint {...}</pre>
 * <p>
 * <b>NOTE:</b> methods annotated with @{@link OnOpen} <b>must</b> have a {@link Session} param.</p>
 * <p>
 * In case of {@code Endpoints} added programmatically, an instance of this configurator should be
 * supplied as an argument to {@link ServerEndpointConfig.Builder#configurator(Configurator)}
 * method:</p>
 * <pre>
 * websocketContainer.addEndpoint(ServerEndpointConfig.Builder
 *         .create(MyEndpoint.class, "/websocket/mySocket")
 *         .configurator(new GuiceServerEndpointConfigurator())
 *         .build());</pre>
 */
public class GuiceServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	volatile Injector injector;
	ContextTracker<ContainerCallContext> containerCallContextTracker;



	/**
	 * Obtains an instance of {@code endpointClass} from {@link Injector#getInstance(Class) Guice}
	 * and creates a context-aware proxy for it, so that {@code Endpoint} lifecycle methods are
	 * executed within {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 * @return a proxy for the newly created {@code endpointClass} instance.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		var injector = this.injector;
		if (injector == null) {
			// injector initialization cannot be done in constructor as annotated Endpoints may
			// create configurator before injector is created in contextInitialized(...).
			// getEndpointInstance(...) however is never called before contextInitialized(...).
			// this method may be called by a big number of concurrent threads (for example when all
			// clients reconnect after a network glitch), so double-checked locking is used.
			synchronized (this) {
				injector = this.injector;
				if (injector == null) {
					injector = GuiceServletContextListener.getInjector();
					TypeLiteral<ContextTracker<ContainerCallContext>> trackerType =
							new TypeLiteral<>() {};
					containerCallContextTracker = injector.getInstance(Key.get(trackerType));
					this.injector = injector; //must be the last statement in the synchronized block
				}
			}
		}

		try {
			final var proxyClass = getProxyClass(endpointClass);
			final EndpointT endpointProxy = super.getEndpointInstance(proxyClass);
			final var endpointDecorator = new EndpointDecorator(
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
					+ "_endpoint_decorator";

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
	 * {@link EndpointDecorator} instance.
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
					EndpointDecorator.class,
					Visibility.PACKAGE_PRIVATE)
			.method(ElementMatchers.any())
					.intercept(InvocationHandlerAdapter.toField(PROXY_DECORATOR_FIELD_NAME));
		final ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (annotation != null) proxyClassBuilder = proxyClassBuilder.annotateType(annotation);
		// todo: swap the below after https://github.com/raphw/byte-buddy/pull/1485 is released
		/*
		try (
			final var unloadedClass = proxyClassBuilder.make();
		) {
			return unloadedClass
				.load(GuiceServerEndpointConfigurator.class.getClassLoader(),
						ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
		}
		/*/
		final var unloadedClass = proxyClassBuilder.make();
		try {
			return unloadedClass
				.load(GuiceServerEndpointConfigurator.class.getClassLoader(),
						ClassLoadingStrategy.Default.INJECTION)
				.getLoaded();
		} finally {
			try {
				unloadedClass.close();
			} catch (IOException e) {
				log.log(Level.WARNING, "exception while closing unloaded dynamic class", e);
			}
		}
		//*/
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
			for (var method: endpointClass.getDeclaredMethods()) {
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
	}



	/**
	 * Subclasses may override this method to further customize {@code Endpoints}.
	 * {@link InvocationHandler#invoke(Object, Method, Object[])} method of the returned handler
	 * will be executed within {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 * By default it returns a handler that simply invokes the given method on {@code endpoint}.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	protected static final Logger log =
			Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
}



/**
 * Executes each call to the wrapped {@code Endpoint} instance within the current
 * {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
 */
class EndpointDecorator implements InvocationHandler {



	final InvocationHandler wrappedEndpoint;
	final ContextTracker<ContainerCallContext> containerCallContextTracker;



	EndpointDecorator(
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
			log.warning("calling manually methods of endpoints that were designed to run within "
					+ "contexts, may lead to OutOfScopeException");
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



	static final Logger log = Logger.getLogger(EndpointDecorator.class.getName());
}
