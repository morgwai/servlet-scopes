// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpSession;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
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
		try {
			final T endPoint = INJECTOR.getInstance(endpointClass);
			final var endpointProxyClass = getProxyClass(endpointClass);
			final T endpointProxy = super.getEndpointInstance(getProxyClass(endpointClass));
			endpointProxyClass.getField(PROXY_DECORATOR_FIELD_NAME).set(
					endpointProxy, new EndpointDecorator(endPoint));
			return endpointProxy;
		} catch (Exception e) {
			throw new InstantiationException(e.toString());
		}
	}

	static final String PROXY_DECORATOR_FIELD_NAME = "decorator";



	@SuppressWarnings("unchecked")
	<T> Class<? extends T> getProxyClass(Class<T> endpointClass) {
		return (Class<? extends T>) endpointProxyClassCache.computeIfAbsent(
			endpointClass,
			(theSameEndpointClass) -> {
				final DynamicType.Builder<T> subclassBuilder = new ByteBuddy()
						.subclass(endpointClass)
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
		);
	}

	static final ConcurrentMap<Class<?>, Class<?>> endpointProxyClassCache =
			new ConcurrentHashMap<>();



	@Inject ContextTracker<RequestContext> eventCtxTracker;
	@Inject ContextTracker<WebsocketConnectionContext> connectionCtxTracker;

	public GuiceServerEndpointConfigurator() {
		INJECTOR.injectMembers(this);
	}



	class EndpointDecorator implements InvocationHandler {

		final InvocationHandler additionalDecorator;
		WebsocketConnectionContext connectionCtx;
		HttpSession httpSession;



		/**
		 * Decorates each call to {@link #endpointInvocationHandler} with setting up
		 * {@link RequestContext} and {@link WebsocketConnectionContext}.
		 */
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			final var wrappedConnection = wrapConnection(args);

			if (isOnOpen(method)) {
				// retrieve HttpSession from userProps, create connCtx,
				// store both in endpointCtx, store connCtx in userProps
				if (wrappedConnection == null) {
					throw new RuntimeException("method annotated with @OnOpen must have a "
							+ "javax.websocket.Session param");
				}
				final var userProperties = wrappedConnection.getUserProperties();
				httpSession = (HttpSession)
						userProperties.get(HTTP_SESSION_PROPERTY_NAME);
				connectionCtx = new WebsocketConnectionContext(
						wrappedConnection, connectionCtxTracker);
				userProperties.put(CONNECTION_CTX_PROPERTY_NAME, connectionCtx);
			}

			// run user code inside contexts
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



		/**
		 * Digs through arguments of some method for wsConnection and replaces it with a wrapper.
		 * @return wrapped connection or {@code null} if there was no {@link Session} param.
		 */
		WebsocketConnectionWrapper wrapConnection(Object[] args) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					final var wrappedConnection =
							new WebsocketConnectionWrapper((Session) args[i], eventCtxTracker);
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
}
