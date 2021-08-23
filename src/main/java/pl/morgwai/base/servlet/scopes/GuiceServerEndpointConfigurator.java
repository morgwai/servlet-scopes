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
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import pl.morgwai.base.guice.scopes.ContextTracker;

import static pl.morgwai.base.servlet.scopes.GuiceServletContextListener.INJECTOR;



/**
 * Automatically injects dependencies of newly created endpoint instances and decorates their
 * methods with {@link WebsocketConnectionContext} and {@link RequestContext} setup.
 * <p>
 * For endpoints annotated with @{@link ServerEndpoint} add this class as
 * {@link ServerEndpoint#configurator() configurator} param:
 * </p><pre>
 *@ServerEndpoint(
 *    value = "/websocket/mySocket",
 *    configurator = GuiceServerEndpointConfigurator.class)
 *public class MyEndpoint {...}
 * </pre>
 * <p>
 * <b>NOTE:</b> methods annotated with @{@link OnOpen} <b>must</b> have {@link Session} param.
 * </p>
 * <p>
 * For endpoints added programmatically, build a {@link ServerEndpointConfig} similar to the below:
 * </p><pre>
 *websocketContainer.addEndpoint(ServerEndpointConfig.Builder
 *        .create(MyEndpoint.class, "/websocket/mySocket")
 *        .configurator(new GuiceServerEndpointConfigurator())
 *        .build());
 * </pre>
 */
public class GuiceServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	/**
	 * Name of the user property containing {@link HttpSession}
	 */
	public static final String HTTP_SESSION_PROPERTY_NAME =
			EndpointDecorator.class.getName() + ".httpSession";

	/**
	 * Name of the user property containing {@link WebsocketConnectionContext}.
	 */
	public static final String CONNECTION_CTX_PROPERTY_NAME =
			EndpointDecorator.class.getName() + ".connectionCtx";



	/**
	 * Store {@link HttpSession} in user properties.
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
	 * @return proxy that sets up {@link RequestContext} and {@link WebsocketConnectionContext} for
	 * a new instance of {@code endpointClass}.
	 */
	@Override
	public <EndpointT> EndpointT getEndpointInstance(Class<EndpointT> endpointClass)
			throws InstantiationException {
		try {
			final EndpointT endPoint = INJECTOR.getInstance(endpointClass);
			@SuppressWarnings("unchecked")
			final var proxyClass = (Class<? extends EndpointT>) proxyClasses.computeIfAbsent(
					endpointClass, (sameEndpointClass) -> createProxyClass(sameEndpointClass));
			final EndpointT endpointProxy = super.getEndpointInstance(proxyClass);
			proxyClass.getDeclaredField(PROXY_DECORATOR_FIELD_NAME).set(
					endpointProxy, new EndpointDecorator(endPoint));
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
		final DynamicType.Builder<EndpointT> subclassBuilder = new ByteBuddy()
				.subclass(endpointClass)
				.name(getClass().getPackageName() + ".ProxyFor_"
						+ endpointClass.getName().replace('.', '_'))
				.defineField(
						PROXY_DECORATOR_FIELD_NAME,
						InvocationHandler.class,
						Visibility.PUBLIC)
				.method(ElementMatchers.any())
				.intercept(InvocationHandlerAdapter.toField(PROXY_DECORATOR_FIELD_NAME));
		final ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (annotation != null) subclassBuilder.annotateType(annotation);
		return subclassBuilder.make().load(endpointClass.getClassLoader()).getLoaded();
	}



	@Inject ContextTracker<RequestContext> eventCtxTracker;
	@Inject ContextTracker<WebsocketConnectionContext> connectionCtxTracker;

	public GuiceServerEndpointConfigurator() {
		INJECTOR.injectMembers(this);
	}



	/**
	 * Decorates each call to supplied endpoint instance with setting up {@link RequestContext} and
	 * {@link WebsocketConnectionContext}.
	 */
	class EndpointDecorator implements InvocationHandler {

		final InvocationHandler additionalDecorator;
		WebsocketConnectionContext connectionCtx;
		HttpSession httpSession;



		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

			// replace wsConnection arg with a wrapper
			WebsocketConnectionWrapper wrappedConnection = null;
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

			// onOpen: create connectionCtx, retrieve HttpSession
			if (connectionCtx == null) {
				if (wrappedConnection == null) throw new RuntimeException(NO_SESSION_PARAM_MESSAGE);
				final var userProperties = wrappedConnection.getUserProperties();
				httpSession = (HttpSession) userProperties.get(HTTP_SESSION_PROPERTY_NAME);
				connectionCtx = new WebsocketConnectionContext(
						wrappedConnection, connectionCtxTracker);
				userProperties.put(CONNECTION_CTX_PROPERTY_NAME, connectionCtx);
			}

			// run original endpoint method within both contexts
			return connectionCtx.callWithinSelf(
				() -> new WebsocketEventContext(httpSession, eventCtxTracker).callWithinSelf(
					() -> {
						try {
							return additionalDecorator.invoke(proxy, method, args);
						} catch (Error | Exception e) {
							throw e;
						} catch (Throwable e) {
							throw new InvocationTargetException(e);  // dead code
						}
					}
				)
			);
		}



		EndpointDecorator(Object endpoint) {
			this.additionalDecorator = getAdditionalDecorator(endpoint);
		}
	}



	/**
	 * Subclasses may override this method to further customize endpoints. By default it returns
	 * handler that simply invokes a given method on <code>endpoint</code>.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	static final String NO_SESSION_PARAM_MESSAGE =
			"method with @OnOpen must have a javax.websocket.Session param";
}
