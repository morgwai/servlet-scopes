/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * blah
 */
public class GuicifiedServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	/**
	 * blah
	 */
	public static final String HTTP_SESSION_PROPERTY_NAME =
			GuicifiedServerEndpointConfigurator.class.getName() + ".httpSession";

	public static final String CONNECTION_CTXS_PROPERTY_NAME =
			GuicifiedServerEndpointConfigurator.class.getName() + ".connectionCtx";



	@Override
	public void modifyHandshake(
			ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
		var httpSession = request.getHttpSession();
		if (httpSession != null) {
			var userProperties = config.getUserProperties();
			synchronized (userProperties) {
				userProperties.put(HTTP_SESSION_PROPERTY_NAME, httpSession);
			}
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
			.intercept(InvocationHandlerAdapter.of(new EndpointDecorator(instance)));
		ServerEndpoint annotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (annotation != null) subclassBuilder.annotateType(annotation);
		customizeEndpointClass(subclassBuilder);
		Class<? extends T> dynamicSubclass =
				subclassBuilder.make().load(endpointClass.getClassLoader()).getLoaded();
		return super.getEndpointInstance(dynamicSubclass);
	}

	public <T> void customizeEndpointClass(DynamicType.Builder<T> subclassBuilder) {}



	static class EndpointDecorator implements InvocationHandler {



		Object endpoint;

		@Inject
		ContextTracker<RequestContext> eventCtxTracker;

		@Inject
		ContextTracker<WebsocketConnectionContext> connectionCtxTracker;
		WebsocketConnectionContext connectionCtx;

		HttpSession httpSession;



		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (isOnOpen(method)) {
				initialize(args);
			}
			return connectionCtx.callWithinSelf(
					() -> new WebsocketEventContext(httpSession, eventCtxTracker).callWithinSelf(
							() -> method.invoke(endpoint, args)));
		}

		boolean isOnOpen(Method method) {
			return method.getAnnotation(OnOpen.class) != null
				|| (
					Endpoint.class.isAssignableFrom(method.getDeclaringClass())
					&& method.getName().equals("onOpen")
					&& method.getParameterCount() == 2
					&& method.getParameterTypes()[0] == Session.class
					&& method.getParameterTypes()[1] == EndpointConfig.class
				);
		}

		@SuppressWarnings("unchecked")
		void initialize(Object[] args) {
			Session connection = (Session) args[0];
			WebsocketConnectionProxy wrappedConnection =
					new WebsocketConnectionProxy(connection, eventCtxTracker);
			connectionCtx = new WebsocketConnectionContext(
					wrappedConnection, connectionCtxTracker);
			args[0] = wrappedConnection;

			var userProperties = connection.getUserProperties();
			httpSession = (HttpSession) userProperties.get(HTTP_SESSION_PROPERTY_NAME);

			// jetty has separate properties for each connections,
			// but tomcat has 1 per http session, so we need a map
			Map<Session, WebsocketConnectionContext> ctxs;
			synchronized (userProperties) {
				ctxs = (Map<Session, WebsocketConnectionContext>)
						userProperties.get(CONNECTION_CTXS_PROPERTY_NAME);
				if (ctxs == null) {
					ctxs = new ConcurrentHashMap<>();
					userProperties.put(CONNECTION_CTXS_PROPERTY_NAME, ctxs);
				}
			}
			ctxs.put(connection, connectionCtx);
		}



		public EndpointDecorator(Object endpoint) {
			this.endpoint = endpoint;
			GuiceServletContextListener.INJECTOR.injectMembers(this);
		}
	}
}
