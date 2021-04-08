/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.implementation.SuperMethodCall;
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
		// TODO: ensure userProperties aren't shared for all connections in case of programmatic
		// endpoints
		System.out.println("config: " + config.hashCode()
				+ ", properties: " + config.getUserProperties().hashCode());
	}



	@Override
	public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
		var decorators = new EndpointDecorators();
		var subclassBuilder = new ByteBuddy()
			.subclass(endpointClass)
			.annotateType(endpointClass.getAnnotation(ServerEndpoint.class))
			.method(
				ElementMatchers.isPublic()
				.and(
					ElementMatchers.isAnnotatedWith(OnOpen.class)
					.or(ElementMatchers.isAnnotatedWith(OnMessage.class))
					.or(ElementMatchers.isAnnotatedWith(OnError.class))
					.or(ElementMatchers.isAnnotatedWith(OnClose.class))
				)
			).intercept(
				InvocationHandlerAdapter.of(
						decorators.getBeginningDecorator(), "beginningDecorator")
				.andThen(SuperMethodCall.INSTANCE)  // TODO excp
				.andThen(InvocationHandlerAdapter.of(
						decorators.getEndDecorator(), "endDecorator"))
			);

		Class<? extends T> dynamicSubclass =
				subclassBuilder.make().load(endpointClass.getClassLoader()).getLoaded();
		T instance = super.getEndpointInstance(dynamicSubclass);
		GuiceServletContextListener.INJECTOR.injectMembers(instance);
		return instance;
	}



	static class EndpointDecorators {



		@Inject
		ContextTracker<RequestContext> eventCtxTracker;

		@Inject
		ContextTracker<WebsocketConnectionContext> connectionCtxTracker;
		WebsocketConnectionContext connectionCtx;



		public InvocationHandler getBeginningDecorator() {
			return (proxy, method, args) -> invokeAtBeginning(proxy, method, args);
		}

		public Object invokeAtBeginning(Object proxy, Method method, Object[] args)
				throws Throwable {
			if (method.getAnnotation(OnOpen.class) != null) {
				connectionCtx = new WebsocketConnectionContext(
						(Session) args[0], connectionCtxTracker);
			}
			((InternalContextTracker<WebsocketConnectionContext>) connectionCtxTracker)
					.setCurrentContext(connectionCtx);
			var eventCtx = new WebsocketEventContext(
					(HttpSession) connectionCtx.getConnection().getUserProperties().get(
							HTTP_SESSION_PROPERTY_NAME),
					eventCtxTracker);
			((InternalContextTracker<RequestContext>) eventCtxTracker).setCurrentContext(eventCtx);
			return null;
		}



		public InvocationHandler getEndDecorator() {
			return (proxy, method, args) -> invokeAtEnd(proxy, method, args);
		}

		public Object invokeAtEnd(Object proxy, Method method, Object[] args) throws Throwable {
			((InternalContextTracker<RequestContext>) eventCtxTracker).clearCurrentContext();
			((InternalContextTracker<WebsocketConnectionContext>) connectionCtxTracker)
					.clearCurrentContext();
			return null;
		}



		public EndpointDecorators() {
			GuiceServletContextListener.INJECTOR.injectMembers(this);
		}
	}
}
