// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guiced.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.stream.IntStream;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * Decorator for websocket {@link Endpoint}s that automatically adds and removes their connections
 * to {@link WebsocketPingerService}.
 * For use with {@link pl.morgwai.base.servlet.scopes.GuiceServerEndpointConfigurator
 * #getAdditionalDecorator(Object)}.
 *
 * @see SimplePingingEndpointServletContextListener
 */
public class EndpointPingerDecorator implements InvocationHandler {



	public EndpointPingerDecorator(Object endpoint, WebsocketPingerService pingerService) {
		this.endpoint = endpoint;
		this.pingerService = pingerService;
	}

	Object endpoint;
	WebsocketPingerService pingerService;



	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		if (isOnOpen(method)) {
			for (var arg: args) {
				if (arg instanceof Session) {
					connection = (Session) arg;
					break;
				}
			}
			pingerService.addConnection(connection);
		}
		if (isOnClose(method)) {
			pingerService.removeConnection(connection);
		}
		return method.invoke(endpoint, args);
	}

	Session connection;



	public static boolean isOnOpen(Method method) {
		final Class<?>[] paramTypes = {Session.class, EndpointConfig.class};
		return isEndpointLifecycleMethod(method, OnOpen.class, "onOpen", paramTypes);
	}



	public static boolean isOnClose(Method method) {
		final Class<?>[] paramTypes = {Session.class, CloseReason.class};
		return isEndpointLifecycleMethod(method, OnClose.class, "onClose", paramTypes);
	}



	public static boolean isEndpointLifecycleMethod(
		Method method,
		Class<? extends Annotation> annotationClass,
		String name,
		Class<?>[] paramTypes
	) {
		if (
			method.getAnnotation(annotationClass) != null
			&& ! Endpoint.class.isAssignableFrom(method.getDeclaringClass())
		) {
			return true;
		}

		final var actualParamTypes = method.getParameterTypes();
		return (
			Endpoint.class.isAssignableFrom(method.getDeclaringClass())
			&& method.getName().equals(name)
			&& actualParamTypes.length == paramTypes.length
			&& IntStream.range(0, paramTypes.length).allMatch(
				(i) -> actualParamTypes[i] == paramTypes[i]
			)
		);
	}
}
