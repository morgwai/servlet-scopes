// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.reflect.InvocationTargetException;
import javax.servlet.ServletContext;
import javax.websocket.OnClose;

import com.google.inject.Injector;
import com.google.inject.Key;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * Subclass of {@link GuiceEndpointConfigurator} that additionally automatically registers and
 * deregisters {@code Endpoints} to its associated {@link WebsocketPingerService}.
 * In addition to usage instructions from the super class, annotated {@code Endpoints} <b>must</b>
 * have a method annotated with @{@link OnClose} to use this {@code Configurator}.
 */
public class PingingServerEndpointConfigurator extends GuiceServerEndpointConfigurator {



	public PingingServerEndpointConfigurator() {}

	public PingingServerEndpointConfigurator(ServletContext appDeployment) {
		super(appDeployment);
	}



	@Override
	protected GuiceEndpointConfigurator newGuiceEndpointConfigurator(Injector injector) {
		return new PingingEndpointConfigurator(
			injector,
			injector.getInstance(WebsocketModule.ctxTrackerKey),
			injector.getInstance(Key.get(WebsocketPingerService.class, PingingClientEndpoint.class))
		) {
			@Override
			protected <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
					throws InstantiationException, InvocationTargetException {
				return PingingServerEndpointConfigurator.this.createEndpointProxyInstance(
						proxyClass);
			}
		};
	}
}
