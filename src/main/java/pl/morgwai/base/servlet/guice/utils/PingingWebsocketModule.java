// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.util.Set;

import com.google.inject.*;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * {@link WebsocketModule} that additionally binds {@link #clientEndpointClasses} annotated
 * with @{@link PingingClientEndpoint} to {@link Provider}s based on
 * {@link PingingEndpointConfigurator}.
 */
public class PingingWebsocketModule extends WebsocketModule {



	final WebsocketPingerService pingerService;



	/**
	 * Constructs a new instance for use in standalone
	 * {@link javax.websocket.server.ServerContainer}s.
	 */
	public PingingWebsocketModule(
		String standaloneServerDeploymentPath,
		WebsocketPingerService pingerService,
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		super(
			standaloneServerDeploymentPath,
			requireTopLevelMethodAnnotations,
			clientEndpointClasses
		);
		this.pingerService = pingerService;
	}

	/**
	 * Calls {@link #PingingWebsocketModule(String, WebsocketPingerService, boolean, Set)
	 * this(standaloneServerDeploymentPath, pingerService, requireTopLevelMethodAnnotations,
	 * Set.of(clientEndpointClasses))}.
	 */
	public PingingWebsocketModule(
		String standaloneServerDeploymentPath,
		WebsocketPingerService pingerService,
		boolean requireTopLevelMethodAnnotations,
		Class<?>... clientEndpointClasses
	) {
		this(
			standaloneServerDeploymentPath,
			pingerService,
			requireTopLevelMethodAnnotations,
			Set.of(clientEndpointClasses)
		);
	}



	/**
	 * Constructs a new instance for use in websocket apps that are embedded in {@code Servlet}
	 * containers or are client-only.
	 */
	public PingingWebsocketModule(
		WebsocketPingerService pingerService,
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		super(requireTopLevelMethodAnnotations, clientEndpointClasses);
		this.pingerService = pingerService;
	}

	/**
	 * Calls {@link #PingingWebsocketModule(WebsocketPingerService, boolean, Set)
	 * this(pingerService, requireTopLevelMethodAnnotations, Set.of(clientEndpointClasses))}.
	 */
	public PingingWebsocketModule(
		WebsocketPingerService pingerService,
		boolean requireTopLevelMethodAnnotations,
		Class<?>... clientEndpointClasses
	) {
		this(pingerService, requireTopLevelMethodAnnotations, Set.of(clientEndpointClasses));
	}



	/**
	 * In addition to bindings from {@link WebsocketModule#configure(Binder) super}, binds
	 * {@link #clientEndpointClasses} annotated with {@link PingingClientEndpoint} to
	 * {@link Provider}s based on {@link PingingEndpointConfigurator}.
	 * Also binds {@link WebsocketPingerService} class to the instance from
	 * {@link #PingingWebsocketModule(WebsocketPingerService, boolean, Set) the constructor}'s
	 * param.
	 */
	@Override
	public void configure(Binder binder) {
		super.configure(binder);
		binder.bind(WebsocketPingerService.class).toInstance(pingerService);
		binder.bind(WebsocketPingerService.class)
			.annotatedWith(PingingClientEndpoint.class)
			.toInstance(pingerService);
			// this binding and PingingEndpointConfigurator's constructor param annotation prevent
			// unmanaged instances to be created with WebsocketPingerService's param-less
			// constructor if PingingWebsocketModule was not properly passed to the Injector
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
