// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;

import pl.morgwai.base.guice.scopes.ContextTracker;

import static pl.morgwai.base.servlet.scopes.GuiceServletContextListener.INJECTOR;



/**
 * Automatically injects dependencies of newly created endpoint instances and decorates their
 * methods to automatically create context for websocket connections and events.<br/>
 * <b>NOTE:</b> methods annotated with <code>@OnOpen</code> of endpoints using this configurator
 * <b>must</b> have <code>javax.websocket.Session</code> param.<br/>
 * <br/>
 * For endpoints annotated with <code>@ServerEndpoint</code> add this class as a
 * <code>configurator</code> param of the annotation:
 * <pre>
 *@ServerEndpoint(
 *	value = "/websocket/mySocket",
 *	configurator = GuiceServerEndpointConfigurator.class)
 *public class MyEndpoint {...}
 * </pre>
 * For endpoints added programmatically build a
 * <code>ServerEndpointConfig</code> similar to the below:
 * <pre>
 *websocketContainer.addEndpoint(
 *	ServerEndpointConfig.Builder
 *		.create(MyEndpoint.class, "/websocket/mySocket")
 *		.configurator(new GuiceServerEndpointConfigurator())
 *		.build());
 * </pre>
 */
public class GuiceServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	/**
	 * Name of the property in user properties under which user's <code>HttpSession</code> is stored
	 * during the handshake.
	 */
	public static final String HTTP_SESSION_PROPERTY_NAME =
			GuiceServerEndpointConfigurator.class.getName() + ".httpSession";

	/**
	 * Name of the property in user properties under which connection context is stored.
	 */
	public static final String CONNECTION_CTX_PROPERTY_NAME =
			GuiceServerEndpointConfigurator.class.getName() + ".connectionCtx";



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



	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
		final T endPoint = INJECTOR.getInstance(endpointClass);
		final T endpointProxy = super.getEndpointInstance(getProxyClass(endpointClass));
		endpointContexts.put(
				System.identityHashCode(endpointProxy),
				new EndpointContext(newEndpointInvocationHandler(endPoint)));
		return endpointProxy;
	}



	@SuppressWarnings("unchecked")
	<T> Class<? extends T> getProxyClass(Class<T> endpointClass) {
		return (Class<? extends T>) endpointProxyClassCache.computeIfAbsent(
			endpointClass,
			(theSameEndpointClass) -> {
				final DynamicType.Builder<T> subclassBuilder = new ByteBuddy()
						.subclass(endpointClass)
						.method(ElementMatchers.any())
						.intercept(InvocationHandlerAdapter.of(
								INJECTOR.getInstance(EndpointDecorator.class)));
				final ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
				if (annotation != null) subclassBuilder.annotateType(annotation);
				return subclassBuilder.make().load(endpointClass.getClassLoader()).getLoaded();
			}
		);
	}

	static final ConcurrentMap<Class<?>, Class<?>> endpointProxyClassCache =
			new ConcurrentHashMap<>();



	/**
	 * Subclasses may override this method to further customize endpoints. By default it returns
	 * handler that simply invokes a given method on <code>endpoint</code>.
	 */
	protected InvocationHandler newEndpointInvocationHandler(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	static final ConcurrentMap<Object, EndpointContext> endpointContexts =
			new ConcurrentHashMap<>();

	static class EndpointContext {

		final InvocationHandler handler;
		WebsocketConnectionContext connectionCtx;
		HttpSession httpSession;

		EndpointContext(InvocationHandler handler) { this.handler = handler; }
	}



	static class EndpointDecorator implements InvocationHandler {

		final ContextTracker<RequestContext> eventCtxTracker;
		final ContextTracker<WebsocketConnectionContext> connectionCtxTracker;



		/**
		 * Decorates each call to {@link #endpointInvocationHandler} with setting up
		 * {@link RequestContext} and {@link WebsocketConnectionContext}.
		 */
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			final var wrappedConnection = wrapConnection(args);
			final var endpointCtx = endpointContexts.get(System.identityHashCode(proxy));


			if (isOnOpen(method)) {
				// retrieve HttpSession from userProps, create connCtx,
				// store both in endpointCtx, store connCtx in userProps
				if (wrappedConnection == null) throw new RuntimeException(
						"method annotated with @OnOpen must have a javax.websocket.Session param");
				final var userProperties = wrappedConnection.getUserProperties();
				endpointCtx.httpSession = (HttpSession)
						userProperties.get(HTTP_SESSION_PROPERTY_NAME);
				endpointCtx.connectionCtx = new WebsocketConnectionContext(
						wrappedConnection, connectionCtxTracker);
				userProperties.put(CONNECTION_CTX_PROPERTY_NAME, endpointCtx.connectionCtx);
			}

			// run user code inside contexts
			return endpointCtx.connectionCtx.callWithinSelf(() -> {
				final var eventCtx = new WebsocketEventContext(
						endpointCtx.httpSession, eventCtxTracker);
				return eventCtx.callWithinSelf(() -> {
					try {
						return endpointCtx.handler.invoke(proxy, method, args);
					} catch (Error | Exception e) {
						throw e;
					} catch (Throwable e) {
						throw new InvocationTargetException(e);  // dead code
					}
				});
			});
		}



		/**
		 * Digs through arguments of some method for wsConnection and replaces it with a wrapper
		 */
		WebsocketConnectionProxy wrapConnection(Object[] args) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					final var wrappedConnection =
							new WebsocketConnectionProxy((Session) args[i], eventCtxTracker);
					args[i] = wrappedConnection;
					return wrappedConnection;
				}
			}
			return null;
		}



		/**
		 * @return if <code>method</code> is <code>onOpen</code>.
		 */
		boolean isOnOpen(Method method) {
			return (
					method.getAnnotation(OnOpen.class) != null
					&& ! Endpoint.class.isAssignableFrom(method.getDeclaringClass())
				) || (
					Endpoint.class.isAssignableFrom(method.getDeclaringClass())
					&& method.getName().equals("onOpen")
					&& method.getParameterCount() == 2
					&& method.getParameterTypes()[0] == Session.class
					&& method.getParameterTypes()[1] == EndpointConfig.class
				);
		}



		@Inject
		EndpointDecorator(
				ContextTracker<RequestContext> eventCtxTracker,
				ContextTracker<WebsocketConnectionContext> connectionCtxTracker) {
			this.eventCtxTracker = eventCtxTracker;
			this.connectionCtxTracker = connectionCtxTracker;
		}
	}
}
