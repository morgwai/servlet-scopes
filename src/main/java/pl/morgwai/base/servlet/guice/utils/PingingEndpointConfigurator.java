// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashSet;
import javax.websocket.*;

import com.google.inject.Inject;
import com.google.inject.Injector;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.ContainerCallContext;
import pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



// todo: javadoc
public class PingingEndpointConfigurator extends GuiceEndpointConfigurator {



	final WebsocketPingerService pingerService;



	@Inject
	public PingingEndpointConfigurator(
		Injector injector,
		ContextTracker<ContainerCallContext> ctxTracker,
		WebsocketPingerService pingerService
	) {
		super(injector, ctxTracker);
		this.pingerService = pingerService;
	}



	@Override
	protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
		final var result = super.getRequiredEndpointMethodAnnotationTypes();
		result.add(OnClose.class);
		return result;
	}



	/**
	 * Returns a handler that additionally registers/deregisters {@code endpoint}'s
	 * {@link Session connection} to the associated {@link WebsocketPingerService}.
	 */
	@Override
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return new EndpointDecorator(endpoint);
	}



	class EndpointDecorator implements InvocationHandler {

		final Object endpoint;



		EndpointDecorator(Object endpoint) {
			this.endpoint = endpoint;
		}



		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (isOnOpen(method)) {
				for (var arg: args) {
					if (arg instanceof Session) {
						connection = (Session) arg;
						break;
					}
				}
				if (endpoint instanceof RttObserver) {
					pingerService.addConnection(
						connection,
						(connection, rttNanos) -> ((RttObserver) endpoint).onPong(rttNanos)
					);
				} else {
					pingerService.addConnection(connection);
				}
			} else if (isOnClose(method)) {
				pingerService.removeConnection(connection);
			}
			return method.invoke(endpoint, args);
		}

		Session connection;
	}



	/**
	 * Checks if {@code method} is either annotated with {@link OnOpen} or overrides
	 * {@link Endpoint#onOpen(Session, EndpointConfig)}.
	 */
	static boolean isOnOpen(Method method) {
		return isEndpointLifecycleMethod(method, OnOpen.class, "onOpen");
	}

	/**
	 * Checks if {@code method} is either annotated with {@link OnClose} or overrides
	 * {@link Endpoint#onClose(Session, CloseReason)}.
	 */
	static boolean isOnClose(Method method) {
		return isEndpointLifecycleMethod(method, OnClose.class, "onClose");
	}

	/**
	 * Checks if {@code method} either is annotated with {@code annotationClass} or overrides the
	 * {@link Endpoint} method given by {@code endpointMethodName}.
	 */
	private static boolean isEndpointLifecycleMethod(
		Method method,
		Class<? extends Annotation> annotationClass,
		String endpointMethodName
	) {
		if (
			method.isAnnotationPresent(annotationClass)
			&& !Endpoint.class.isAssignableFrom(method.getDeclaringClass())
		) {
			return true;
		}

		if ( !method.getName().equals(endpointMethodName)) return false;
		try {
			Endpoint.class.getMethod(endpointMethodName, method.getParameterTypes());
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}
}
