// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import com.google.inject.Inject;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guiced.utils.EndpointPingerDecorator;


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



	/**
	 * Name of the user property containing {@link HttpSession}.
	 */
	public static final String HTTP_SESSION_PROPERTY_NAME =
			EndpointDecorator.class.getName() + ".httpSession";

	/**
	 * Name of the user property containing {@link WebsocketConnectionContext}.
	 */
	public static final String CONNECTION_CTX_PROPERTY_NAME =
			EndpointDecorator.class.getName() + ".connectionCtx";



	/**
	 * Stores {@link HttpSession} in user properties.
	 */
	@Override
	public void modifyHandshake(
			ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
		final var httpSession = request.getHttpSession();
		if (httpSession != null) {
			config.getUserProperties().put(HTTP_SESSION_PROPERTY_NAME, httpSession);
		}
	}



	/**
	 * Creates an {@code endpointClass} instance and a proxy for it.
	 * @return proxy that sets up {@link ContainerCallContext} and
	 * {@link WebsocketConnectionContext} for a new underlying instance of {@code endpointClass}.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		try {
			final var injector = GuiceServletContextListener.getInjector();
			final EndpointT endpoint = injector.getInstance(endpointClass);
			@SuppressWarnings("unchecked")
			final var proxyClass = (Class<? extends EndpointT>) proxyClasses.computeIfAbsent(
					endpointClass, this::createProxyClass);
			final EndpointT endpointProxy = super.getEndpointInstance(proxyClass);
			final var endpointDecorator = new EndpointDecorator(getAdditionalDecorator(endpoint));
			injector.injectMembers(endpointDecorator);
			proxyClass.getDeclaredField(PROXY_DECORATOR_FIELD_NAME)
					.set(endpointProxy, endpointDecorator);
			return endpointProxy;
		} catch (Exception e) {
			throw new InstantiationException(e.toString());
		}
	}

	static final ConcurrentMap<Class<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();
	static final String PROXY_DECORATOR_FIELD_NAME = "pl_morgwai_decorator";



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
	 * Decorates each call to the supplied endpoint instance with setting up
	 * {@link ContainerCallContext} and {@link WebsocketConnectionContext}.
	 */
	static class EndpointDecorator implements InvocationHandler {

		final InvocationHandler additionalEndpointDecorator;
		WebsocketConnectionContext connectionCtx;
		HttpSession httpSession;

		@Inject ContextTracker<ContainerCallContext> eventCtxTracker;
		@Inject ContextTracker<WebsocketConnectionContext> connectionCtxTracker;



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
				// TODO: move static websocket helpers to servlet-utils project
				if ( ! EndpointPingerDecorator.isOnOpen(method)) {
					// this is most commonly toString() call from a debugger
					return additionalEndpointDecorator.invoke(proxy, method, args);
				}
				if (wrappedConnection == null) throw new RuntimeException(NO_SESSION_PARAM_MESSAGE);
				final var userProperties = wrappedConnection.getUserProperties();
				httpSession = (HttpSession) userProperties.get(HTTP_SESSION_PROPERTY_NAME);
				connectionCtx = new WebsocketConnectionContext(
						wrappedConnection, connectionCtxTracker);
				userProperties.put(CONNECTION_CTX_PROPERTY_NAME, connectionCtx);
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
							throw new InvocationTargetException(e);  // dead code
						}
					}
				)
			);
		}



		EndpointDecorator(InvocationHandler additionalEndpointDecorator) {
			this.additionalEndpointDecorator = additionalEndpointDecorator;
		}
	}



	/**
	 * Subclasses may override this method to further customize endpoints. By default it returns a
	 * handler that simply invokes a given method on <code>endpoint</code>.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	static final String NO_SESSION_PARAM_MESSAGE =
			"method annotated with @OnOpen must have a javax.websocket.Session param";
}
