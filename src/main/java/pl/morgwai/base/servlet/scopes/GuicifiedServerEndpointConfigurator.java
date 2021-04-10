/*
 * Copyright (c) Piotr Morgwai Kotarbinski
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
 * Automatically injects dependencies of newly created endpoint instances and decorates their
 * methods to automatically create context for websocket connections and events.<br/>
 * <br/>
 * For endpoints annotated with <code>@ServerEndpoint</code> add this class as a
 * <code>configurator</code> param of the annotation:
 * <pre>
 *@ServerEndpoint(
 *	value = "/websocket/mySocket",
 *	configurator = GuicifiedServerEndpointConfigurator.class)
 *public class MyEndpoint {...}
 * </pre>
 * For endpoints added programmatically build a
 * <code>ServerEndpointConfig</code> similar to the below:
 * <pre>
 *websocketContainer.addEndpoint(
 *	ServerEndpointConfig.Builder
 *		.create(MyEndpoint.class, "/websocket/mySocket")
 *		.configurator(new GuicifiedServerEndpointConfigurator())
 *		.build());
 * </pre>
 */
public class GuicifiedServerEndpointConfigurator extends ServerEndpointConfig.Configurator {



	/**
	 * Name of the property in user properties under which user's <code>HttpSession</code> is stored
	 * during the handshake.
	 */
	public static final String HTTP_SESSION_PROPERTY_NAME =
			GuicifiedServerEndpointConfigurator.class.getName() + ".httpSession";

	/**
	 * Name of the property in user properties under which
	 * Map&lt;Session, WebsocketConnectionContext&gt; is stored. Jetty has a separate properties
	 * instance for each websocket connection, so it will contain just 1 entry. Tomcat has 1
	 * instance per HTTP session, so it will contain entries for all websocket connections of a
	 * given user.
	 */
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

	/**
	 * Allows to further customize decorated dynamic subclasses of endpoints in derived
	 * configurators. By default it does nothing.
	 */
	protected <T> void customizeEndpointClass(DynamicType.Builder<T> subclassBuilder) {}



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
