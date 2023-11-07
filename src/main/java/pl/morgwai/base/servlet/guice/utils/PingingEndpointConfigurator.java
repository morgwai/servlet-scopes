// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashSet;

import jakarta.servlet.ServletContext;
import jakarta.websocket.OnClose;
import jakarta.websocket.Session;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnClose;
import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnOpen;



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
		 * Called by {@link jakarta.websocket.PongMessage}
		 * {@link jakarta.websocket.MessageHandler handler} to report round-trip time in nanoseconds.
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
}
