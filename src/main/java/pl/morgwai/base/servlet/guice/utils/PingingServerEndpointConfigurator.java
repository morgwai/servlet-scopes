// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.reflect.InvocationTargetException;
import javax.servlet.ServletContext;
import javax.websocket.OnClose;

import com.google.inject.Injector;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * Subclass of {@link GuiceEndpointConfigurator} that additionally automatically registers and
 * deregisters {@code Endpoints} to its associated {@link WebsocketPingerService}.
 * In addition to usage instructions from the super class, annotated {@code Endpoints} <b>must</b>
 * have a method annotated with @{@link OnClose} and the app-wide {@link WebsocketPingerService}
 * must be {@link ServletContext#setAttribute(String, Object) stored as a deployment attribute}
 * under {@link Class#getName() fully-qualified name} of {@link WebsocketPingerService} class.
 * @see PingingServletContextListener
 */
public class PingingServerEndpointConfigurator extends GuiceServerEndpointConfigurator {



	WebsocketPingerService pingerService;



	public PingingServerEndpointConfigurator() {}

	public PingingServerEndpointConfigurator(ServletContext appDeployment) {
		super(appDeployment);
	}



	@Override
	protected void initialize(ServletContext appDeployment) {
		pingerService = (WebsocketPingerService)
				appDeployment.getAttribute(WebsocketPingerService.class.getName());
		if (pingerService == null) {
			throw new RuntimeException(
					"no \"" + WebsocketPingerService.class.getName() + "\" deployment attribute");
		}
		super.initialize(appDeployment);
	}



	@Override
	protected GuiceEndpointConfigurator newGuiceEndpointConfigurator(
		Injector injector,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		return new PingingEndpointConfigurator(injector, ctxTracker, pingerService) {
			@Override
			protected <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
					throws InstantiationException, InvocationTargetException {
				return PingingServerEndpointConfigurator.this.createEndpointProxyInstance(
						proxyClass);
			}
		};
	}
}
