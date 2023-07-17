// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.server.*;

import com.google.inject.Inject;
import com.google.inject.Injector;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pl.morgwai.base.guice.scopes.ContextTracker;

import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnOpen;



/**
 * Automatically injects dependencies of endpoint instances and ensures that lifecycle methods of
 * the created endpoints are executed within {@link WebsocketConnectionContext} and
 * {@link ContainerCallContext}.
 * <p>
 * For endpoints annotated with @{@link ServerEndpoint}, this class must be used as
 * {@link ServerEndpoint#configurator() configurator} param of the annotation:</p>
 * <pre>
 * &commat;ServerEndpoint(
 *     value = "/websocket/mySocket",
 *     configurator = GuiceServerEndpointConfigurator.class)
 * public class MyEndpoint {...}</pre>
 * <p>
 * <b>NOTE:</b> methods annotated with @{@link OnOpen} <b>must</b> have a {@link Session} param.</p>
 * <p>
 * For endpoints added programmatically, a {@link ServerEndpointConfig} similar to the below should
 * be used:</p>
 * <pre>
 * websocketContainer.addEndpoint(ServerEndpointConfig.Builder
 *         .create(MyEndpoint.class, "/websocket/mySocket")
 *         .configurator(new GuiceServerEndpointConfigurator())
 *         .build());</pre>
 */
public class GuiceServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	volatile Injector injector;



	/**
	 * Creates a new {@code endpointClass} instance and a proxy for it. Injects dependencies of
	 * the newly created endpoint. The proxy ensures that endpoint's lifecycle methods are
	 * executed within {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 * @return a proxy for a new {@code endpointClass} instance.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		var injector = this.injector;
		if (injector == null) {
			// injector initialization cannot be done in constructor as annotated endpoints may
			// create configurator before injector is created in contextInitialized(...).
			// getEndpointInstance(...) however is never called before contextInitialized(...).
			// this method may be called by a big number of concurrent threads (for example when all
			// clients reconnect after a network glitch), so double-checked locking is used.
			synchronized (this) {
				injector = this.injector;
				if (injector == null) {
					injector = GuiceServletContextListener.getInjector();
					injector.injectMembers(this);
					this.injector = injector; //must be the last statement in the synchronized block
				}
			}
		}
		try {
			final var proxyClass = getProxyClass(endpointClass);
			final EndpointT endpointProxy = super.getEndpointInstance(proxyClass);
			final var endpointDecorator =
					new EndpointDecorator(injector.getInstance(endpointClass));
			proxyClass.getDeclaredField(PROXY_DECORATOR_FIELD_NAME)
					.set(endpointProxy, endpointDecorator);
			return endpointProxy;
		} catch (Exception e) {
			log.error("endpoint instantiation failed", e);
			throw new InstantiationException(e.toString());
		}
	}

	static final String PROXY_DECORATOR_FIELD_NAME = "pl_morgwai_decorator";

	@SuppressWarnings("unchecked")
	<EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
		return (Class<? extends EndpointT>)
				proxyClasses.computeIfAbsent(endpointClass, this::createProxyClass);
	}

	static final ConcurrentMap<Class<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();

	/**
	 * Creates a dynamic proxy class that delegates calls to the associated
	 * {@link EndpointDecorator}.
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
/*
// swap after https://github.com/raphw/byte-buddy/pull/1485 is merged
		try (
			final var unloadedClass = proxyClassBuilder.make()
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
				log.warn("exception while closing unloaded dynamic class", e);
			}
		}
//*/
	}

	private void checkIfRequiredEndpointMethodsPresent(Class<?> endpointClass) {
		var annotatedMethodPresent = new HashMap<Class<? extends Annotation>, Boolean>();
		var annotationTypes = getRequiredEndpointMethodAnnotationTypes();
		for (var annotationType: annotationTypes) annotatedMethodPresent.put(annotationType, false);
		for (var method: endpointClass.getDeclaredMethods()) {
			for (var annotationType: annotationTypes) {
				if (method.isAnnotationPresent(annotationType)) {
					annotatedMethodPresent.put(annotationType, true);
				}
			}
		}
		for (var entry: annotatedMethodPresent.entrySet()) {
			if ( !entry.getValue()) {
				throw new RuntimeException("Endpoint must have a method annotated with "
						+ entry.getKey().getSimpleName());
			}
		}
	}

	/**
	 * Returns a list of annotations of endpoint lifecycle methods that are required to be present.
	 * By default a singleton of {@link OnOpen}. Subclasses may override this method if needed.
	 */
	protected List<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
		return Collections.singletonList(OnOpen.class);
	}



	/**
	 * Stores into the user properties the {@link HttpSession} associated with the {@code request}.
	 */
	@Override
	public void modifyHandshake(
			ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
		final var httpSession = request.getHttpSession();
		if (httpSession != null) {
			config.getUserProperties().put(HttpSession.class.getName(), httpSession);
		}
	}



	@Inject ContextTracker<ContainerCallContext> eventCtxTracker;

	/**
	 * Executes each call to the supplied endpoint instance within
	 * {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 */
	class EndpointDecorator implements InvocationHandler {

		final InvocationHandler additionalEndpointDecorator;
		WebsocketConnectionContext connectionCtx;
		HttpSession httpSession;



		EndpointDecorator(Object endpoint) {
			additionalEndpointDecorator = getAdditionalDecorator(endpoint);
		}



		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

			// replace wsConnection arg with a wrapper
			WebsocketConnectionWrapper wrappedConnection = null;
			if (args != null) {
				for (int i = 0; i < args.length; i++) {
					if (args[i] instanceof Session) {
						if (connectionCtx == null) {
							args[i] = wrappedConnection = new WebsocketConnectionWrapper(
								(Session) args[i], eventCtxTracker);
						} else {
							args[i] = connectionCtx.getConnection();
						}
						break;
					}
				}
			}

			// onOpen: create connectionCtx, retrieve HttpSession
			if (connectionCtx == null) {
				if ( !isOnOpen(method)) {
					// this is most commonly toString() call from a debugger
					return additionalEndpointDecorator.invoke(proxy, method, args);
				}
				if (wrappedConnection == null) {
					throw new RuntimeException("method annotated with @OnOpen must have a"
							+ " javax.websocket.Session param");
				}
				final var userProperties = wrappedConnection.getUserProperties();
				httpSession = (HttpSession) userProperties.get(HttpSession.class.getName());
				connectionCtx = new WebsocketConnectionContext(wrappedConnection);
				userProperties.put(WebsocketConnectionContext.class.getName(), connectionCtx);
			}

			// execute the original endpoint method within contexts
			var eventCtx = new WebsocketEventContext(connectionCtx, httpSession, eventCtxTracker);
			return eventCtx.executeWithinSelf(
				() -> {
					try {
						return additionalEndpointDecorator.invoke(proxy, method, args);
					} catch (Error | Exception e) {
						throw e;
					} catch (Throwable e) {
						throw new Exception(e);  // dead code
					}
				}
			);
		}
	}



	/**
	 * Subclasses may override this method to further customize endpoints.
	 * {@link InvocationHandler#invoke(Object, Method, Object[])} method of the returned handler
	 * will be executed within {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 * By default it returns a handler that simply invokes a given method on {@code endpoint}.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	protected static final Logger log =
			LoggerFactory.getLogger(GuiceServerEndpointConfigurator.class.getName());
}
