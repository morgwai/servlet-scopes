// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.server.*;

import com.google.inject.Injector;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnClose;
import static pl.morgwai.base.servlet.utils.EndpointUtils.isOnOpen;



/**
 * A {@link GuiceServerEndpointConfigurator} that automatically registers and deregisters
 * {@code Endpoints} to a {@link WebsocketPingerService}.
 * @see PingingServletContextListener
 */
public class PingingEndpointConfigurator extends GuiceServerEndpointConfigurator {



	static final ConcurrentMap<String, WebsocketPingerService> services =
			new ConcurrentHashMap<>(5);

	/**
	 * Registers {@code pingerService} to be used by container-created
	 * {@code PingingEndpointConfigurator} instances for {@code Endpoints} annotated with
	 * {@link ServerEndpoint} deployed in the {@code servletContext}.
	 * <p>
	 * This method is called automatically by
	 * {@link PingingServletContextListener#createInjector(LinkedList)}, it must be
	 * called manually in apps that don't use it.</p>
	 */
	public static void registerPingerService(
			WebsocketPingerService pingerService, ServletContext servletContext) {
		services.put(servletContext.getContextPath(), pingerService);
	}

	/**
	 * Removes the reference to the {@link WebsocketPingerService} associated with
	 * {@code servletContext}.
	 * <p>
	 * This method is called automatically by
	 * {@link PingingServletContextListener#contextDestroyed(ServletContextEvent)}, it must be
	 * called manually in apps that don't use it.</p>
	 */
	public static void deregisterPingerService(ServletContext servletContext) {
		services.remove(servletContext.getContextPath());
	}

	/**
	 * @deprecated if your {@link jakarta.servlet.ServletContextListener} does not extend
	 *     {@link PingingServletContextListener}, then use
	 *     {@link #registerPingerService(WebsocketPingerService, ServletContext)} instead of this
	 *     method in your
	 *     {@link jakarta.servlet.ServletContextListener#contextInitialized(ServletContextEvent)}.
	 */
	@Deprecated(forRemoval = true)
	public static void setPingerService(WebsocketPingerService pingerService) {
		services.put("<-;{ invalid path for fallback in modifyHandshake()", pingerService);
	}



	volatile WebsocketPingerService pingerService;



	public PingingEndpointConfigurator() {}

	public PingingEndpointConfigurator(
		Injector injector,
		ContextTracker<ContainerCallContext> containerCallContextTracker,
		WebsocketPingerService pingerService
	) {
		super(injector, containerCallContextTracker);
		this.pingerService = pingerService;
	}



	@Override
	public void modifyHandshake(
		ServerEndpointConfig config,
		HandshakeRequest request,
		HandshakeResponse response
	) {
		super.modifyHandshake(config, request, response);
		if (this.pingerService == null) {
			WebsocketPingerService pingerService;
			final var httpSession = request.getHttpSession();
			if (httpSession != null) {
				final var servletCtx = ((HttpSession) httpSession).getServletContext();
				pingerService = (WebsocketPingerService)
						servletCtx.getAttribute(WebsocketPingerService.class.getName());
			} else {
				final var requestPath = request.getRequestURI().getPath();
				final var servletContextPath = requestPath.substring(
						0, requestPath.lastIndexOf(config.getPath()));
				pingerService = services.get(servletContextPath);
				if (pingerService == null) {
					log.severe(
							"Could not find WebsocketPingerService for requestPath " + requestPath);
					System.err.println("Could not find WebsocketPingerService for requestPath "
							+ requestPath);
					// pick first and hope for the best...
					pingerService = services.values().iterator().next();
				}
			}
			this.pingerService = pingerService;
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
				pingerService.addConnection(connection);
			} else if (isOnClose(method)) {
				pingerService.removeConnection(connection);
			}
			return method.invoke(endpoint, args);
		}

		Session connection;
	}



	static final Logger log = Logger.getLogger(PingingEndpointConfigurator.class.getName());
}
