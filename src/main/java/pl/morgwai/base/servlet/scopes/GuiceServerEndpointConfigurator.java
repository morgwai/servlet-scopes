/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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



	@Override
	public void modifyHandshake(
			ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
		var httpSession = request.getHttpSession();
		if (httpSession != null) {
			config.getUserProperties().put(HTTP_SESSION_PROPERTY_NAME, httpSession);
		}
	}



	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
		T instance;
		try {
			instance = endpointClass.getConstructor().newInstance();
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new InstantiationException(e.toString());
		}
		GuiceServletContextListener.INJECTOR.injectMembers(instance);
		DynamicType.Builder<T> subclassBuilder = new ByteBuddy()
			.subclass(endpointClass)
			.method(ElementMatchers.any())
			.intercept(InvocationHandlerAdapter.of(
					new EndpointDecorator(getEndpointInvocationHandler(instance))));
		ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (annotation != null) subclassBuilder.annotateType(annotation);
		Class<? extends T> dynamicSubclass =
				subclassBuilder.make().load(endpointClass.getClassLoader()).getLoaded();
		return super.getEndpointInstance(dynamicSubclass);
	}

	/**
	 * Subclasses may override this method to further customize endpoints. By default it returns
	 * handler that simply invokes a given method on <code>endpoint</code>.
	 */
	protected <T> InvocationHandler getEndpointInvocationHandler(T endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}



	static class EndpointDecorator implements InvocationHandler {



		InvocationHandler endpointInvocationHandler;

		@Inject
		ContextTracker<RequestContext> eventCtxTracker;

		@Inject
		ContextTracker<WebsocketConnectionContext> connectionCtxTracker;
		WebsocketConnectionContext connectionCtx;

		HttpSession httpSession;



		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			var wrappedConnection = wrapConnection(args);
			if (isOnOpen(method)) {
				initialize(wrappedConnection);
			}
			return connectionCtx.callWithinSelf(
				() -> new WebsocketEventContext(httpSession, eventCtxTracker).callWithinSelf(
					() -> {
						try {
							return endpointInvocationHandler.invoke(proxy, method, args);
						} catch (Throwable e) {
							throw new InvocationTargetException(e);
						}}));
		}

		WebsocketConnectionProxy wrapConnection(Object[] args) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					var wrappedConnection =
							new WebsocketConnectionProxy((Session) args[i], eventCtxTracker);
					args[i] = wrappedConnection;
					return wrappedConnection;
				}
			}
			return null;
		}

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

		void initialize(WebsocketConnectionProxy wrappedConnection) {
			if (wrappedConnection == null) throw new RuntimeException(
					"method annotated with @OnOpen must have a javax.websocket.Session param");
			var userProperties = wrappedConnection.getUserProperties();
			httpSession = (HttpSession) userProperties.get(HTTP_SESSION_PROPERTY_NAME);
			connectionCtx = new WebsocketConnectionContext(
					wrappedConnection, connectionCtxTracker);
			userProperties.put(CONNECTION_CTX_PROPERTY_NAME, connectionCtx);
		}



		public EndpointDecorator(InvocationHandler endpointInvocationHandler) {
			this.endpointInvocationHandler = endpointInvocationHandler;
			GuiceServletContextListener.INJECTOR.injectMembers(this);
		}
	}
}
