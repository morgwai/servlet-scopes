// Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.reflect.InvocationTargetException;

import com.google.inject.Injector;
import com.google.inject.Key;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;

import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;
import static pl.morgwai.base.servlet.guice.scopes.WebsocketModule.CTX_TRACKER_KEY;



/** {@link GuiceServerEndpointConfigurator} that uses {@link PingingEndpointConfigurator}. */
public class PingingServerEndpointConfigurator extends GuiceServerEndpointConfigurator {



	public PingingServerEndpointConfigurator() {}

	public PingingServerEndpointConfigurator(Injector injector) {
		super(injector);
	}



	@Override
	protected PingingEndpointConfigurator newGuiceEndpointConfigurator(Injector injector) {
		return new PingingEndpointConfigurator(
			injector,
			injector.getInstance(CTX_TRACKER_KEY),
			injector.getInstance(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY),
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
