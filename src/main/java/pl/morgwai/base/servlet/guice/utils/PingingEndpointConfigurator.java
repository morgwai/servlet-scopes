// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashSet;

import javax.servlet.ServletContext;
import javax.websocket.*;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * Subclass of {@link GuiceServerEndpointConfigurator} that additionally automatically registers and
 * deregisters {@code Endpoints} to its associated {@link WebsocketPingerService}.
 * In addition to usage instructions from the super class, annotated {@code Endpoints} <b>must</b>
 * have a method annotated with @{@link OnClose} and the app-wide {@link WebsocketPingerService}
 * must be {@link ServletContext#setAttribute(String, Object) stored as a deployment attribute}
 * under {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.
 * @see PingingServletContextListener
 */
public class PingingEndpointConfigurator extends GuiceServerEndpointConfigurator {



	/** An interface for {@code Endpoints} to get round-trip time reports upon receiving pongs. */
	public interface RttObserver {

		/**
		 * Called by {@link javax.websocket.PongMessage}
		 * {@link javax.websocket.MessageHandler handler} to report round-trip time in nanoseconds.
		 */
		void onPong(long rttNanos);
	}



	WebsocketPingerService pingerService;



	public PingingEndpointConfigurator() {}

	public PingingEndpointConfigurator(ServletContext appDeployment) {
		super(appDeployment);
	}



	@Override
	protected void initialize(ServletContext appDeployment) {
		super.initialize(appDeployment);
		pingerService = (WebsocketPingerService)
				appDeployment.getAttribute(WebsocketPingerService.class.getName());
		if (pingerService == null) {
			throw new RuntimeException(
					"no \"" + WebsocketPingerService.class.getName() + "\" deployment attribute");
		}
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
