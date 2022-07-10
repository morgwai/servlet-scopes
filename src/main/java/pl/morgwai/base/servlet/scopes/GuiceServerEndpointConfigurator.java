// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
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
 * Automatically sets up {@link WebsocketConnectionContext} &amp; {@link ContainerCallContext} and
 * injects dependencies of endpoint instances.
 * <p>
 * For endpoints annotated with @{@link ServerEndpoint} add this class as
 * {@link ServerEndpoint#configurator() configurator} param:</p>
 * <pre>
 * &commat;ServerEndpoint(
 *     value = "/websocket/mySocket",
 *     configurator = GuiceServerEndpointConfigurator.class)
 * public class MyEndpoint {...}</pre>
 * <p>
 * <b>NOTE:</b> methods annotated with @{@link OnOpen} <b>must</b> have a {@link Session} param.</p>
 * <p>
 * For endpoints added programmatically, build a {@link ServerEndpointConfig} similar to the below:
 * </p><pre>
 * websocketContainer.addEndpoint(ServerEndpointConfig.Builder
 *         .create(MyEndpoint.class, "/websocket/mySocket")
 *         .configurator(new GuiceServerEndpointConfigurator())
 *         .build());</pre>
 */
public class GuiceServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	volatile Injector injector;



	/**
	 * Creates an {@code endpointClass} instance and a proxy for it.
	 * @return proxy that sets up {@link ContainerCallContext} and
	 * {@link WebsocketConnectionContext} for a new underlying instance of {@code endpointClass}.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		if (injector == null) {
			// not in constructor as annotated endpoints create configurator before injector
			synchronized (this) {
				if (injector == null) {
					injector = GuiceServletContextListener.getInjector();
					injector.injectMembers(this);
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
	 * Creates dynamic proxy class that delegates calls to the associated {@link EndpointDecorator}.
	 */
	<EndpointT> Class<? extends EndpointT> createProxyClass(Class<EndpointT> endpointClass) {
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
		return proxyClassBuilder
			.make()
			.load(GuiceServerEndpointConfigurator.class.getClassLoader(),
					ClassLoadingStrategy.Default.INJECTION)
			.getLoaded();
	}



	/**
	 * Stores {@link HttpSession} in user properties.
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
	@Inject ContextTracker<WebsocketConnectionContext> connectionCtxTracker;

	/**
	 * Decorates each call to the supplied endpoint instance with setting up
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
				if ( ! isOnOpen(method)) {
					// this is most commonly toString() call from a debugger
					return additionalEndpointDecorator.invoke(proxy, method, args);
				}
				if (wrappedConnection == null) {
					throw new RuntimeException("method annotated with @OnOpen must have a"
							+ " javax.websocket.Session param");
				}
				final var userProperties = wrappedConnection.getUserProperties();
				httpSession = (HttpSession) userProperties.get(HttpSession.class.getName());
				connectionCtx = new WebsocketConnectionContext(
						wrappedConnection, connectionCtxTracker);
				userProperties.put(WebsocketConnectionContext.class.getName(), connectionCtx);
			}

			// run original endpoint method within both contexts
			return connectionCtx.executeWithinSelf(
				() -> new WebsocketEventContext(httpSession, eventCtxTracker).executeWithinSelf(
					() -> {
						try {
							return additionalEndpointDecorator.invoke(proxy, method, args);
						} catch (Error | Exception e) {
							throw e;
						} catch (Throwable e) {
							throw new Exception(e);  // dead code
						}
					}
				)
			);
		}
	}



	/**
	 * Subclasses may override this method to further customize endpoints. By default it returns a
	 * handler that simply invokes a given method on <code>endpoint</code>.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	protected static final Logger log =
			LoggerFactory.getLogger(GuiceServerEndpointConfigurator.class.getName());
}
