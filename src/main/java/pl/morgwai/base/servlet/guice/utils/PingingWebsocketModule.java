// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.Set;

import com.google.inject.*;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



// todo: javadoc
public class PingingWebsocketModule extends WebsocketModule {



	final WebsocketPingerService pingerService;



	// todo: javadoc
	public PingingWebsocketModule(
		WebsocketPingerService pingerService, Set<Class<?>> clientEndpointClasses
	) {
		super(clientEndpointClasses);
		this.pingerService = pingerService;
	}



	/** Calls {@link #PingingWebsocketModule(WebsocketPingerService, Set)}. */
	public PingingWebsocketModule(
		WebsocketPingerService pingerService, Class<?>... clientEndpointClasses
	) {
		this(pingerService, Set.of(clientEndpointClasses));
	}



	@Override
	public void configure(Binder binder) {
		super.configure(binder);
		binder.bind(WebsocketPingerService.class).toInstance(pingerService);
		for (var clientEndpointClass: clientEndpointClasses) {
			bindClientEndpoint(binder, clientEndpointClass);
		}
	}

	<EndpointT> void bindClientEndpoint(Binder binder, Class<EndpointT> clientEndpointClass) {
		binder.bind(clientEndpointClass).annotatedWith(PingingClientEndpoint.class).toProvider(
			new Provider<>() {
				@Inject PingingEndpointConfigurator endpointConfigurator;

				@Override public EndpointT get() {
					try {
						return endpointConfigurator.getProxiedEndpointInstance(clientEndpointClass);
					} catch (Exception e) {
						throw new ProvisionException(e.getMessage(), e);
					}
				}
			}
		);
	}
}
