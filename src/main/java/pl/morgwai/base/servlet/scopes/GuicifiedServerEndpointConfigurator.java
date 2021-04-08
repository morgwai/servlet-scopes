/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import net.bytebuddy.ByteBuddy;
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
		T instance = super.getEndpointInstance(endpointClass);
		GuiceServletContextListener.INJECTOR.injectMembers(instance);
		var subclassBuilder = new ByteBuddy()
			.subclass(endpointClass)
			.annotateType(endpointClass.getAnnotation(ServerEndpoint.class))
			.method(ElementMatchers.any())
			.intercept(InvocationHandlerAdapter.of(new EndpointDecorator(instance)));
		Class<? extends T> dynamicSubclass =
				subclassBuilder.make().load(endpointClass.getClassLoader()).getLoaded();
		try {
			return dynamicSubclass.getConstructor().newInstance();
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new InstantiationException(e.toString());
		}
	}



	static class EndpointDecorator implements InvocationHandler {



		Object endpoint;

		@Inject
		ContextTracker<RequestContext> eventCtxTracker;

		@Inject
		ContextTracker<WebsocketConnectionContext> connectionCtxTracker;
		WebsocketConnectionContext connectionCtx;



		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			if (method.getAnnotation(OnOpen.class) != null) {
				connectionCtx = new WebsocketConnectionContext(
						(Session) args[0], connectionCtxTracker);
			}
			var eventCtx = new WebsocketEventContext(
					(HttpSession) connectionCtx.getConnection().getUserProperties().get(
							HTTP_SESSION_PROPERTY_NAME),
					eventCtxTracker);
			return connectionCtx.callWithinSelf(
					() -> eventCtx.callWithinSelf(() -> method.invoke(endpoint, args)));
		}



		public EndpointDecorator(Object endpoint) {
			this.endpoint = endpoint;
			GuiceServletContextListener.INJECTOR.injectMembers(this);
		}
	}
}
