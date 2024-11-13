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
		binder.bind(WebsocketPingerService.class)
			.toInstance(pingerService);
		binder.bind(WebsocketPingerService.class)
			.annotatedWith(PingingClientEndpoint.class)
			.toInstance(pingerService);
			// this binding and PingingEndpointConfigurator's constructor param annotation prevent
			// unmanaged instances to be created with WebsocketPingerService's param-less
			// constructor if PingingWebsocketModule was not properly passed to the Injector
		for (var clientEndpointClass: clientEndpointClasses) {
			bindPingingClientEndpoint(binder, clientEndpointClass);
		}
	}

	<EndpointT> void bindPingingClientEndpoint(Binder binder, Class<EndpointT> clientEndpointClass)
	{
		binder.bind(clientEndpointClass).annotatedWith(NO_NESTING)
			.toProvider(new PingingEndpointProvider<>(clientEndpointClass, false, false));
		binder.bind(clientEndpointClass).annotatedWith(NEST_CONNECTION_CTX)
			.toProvider(new PingingEndpointProvider<>(clientEndpointClass, true, false));
		binder.bind(clientEndpointClass).annotatedWith(NEST_HTTP_SESSION_CTX)
			.toProvider(new PingingEndpointProvider<>(clientEndpointClass, false, true));
		binder.bind(clientEndpointClass).annotatedWith(NEST_BOTH)
			.toProvider(new PingingEndpointProvider<>(clientEndpointClass, true, true));
	}

	@PingingClientEndpoint(nestConnectionContext = false, nestHttpSessionContext = false)
	static final PingingClientEndpoint NO_NESTING;
	@PingingClientEndpoint(nestHttpSessionContext = false)
	static final PingingClientEndpoint NEST_CONNECTION_CTX;
	@PingingClientEndpoint(nestConnectionContext = false)
	static final PingingClientEndpoint NEST_HTTP_SESSION_CTX;
	@PingingClientEndpoint
	static final PingingClientEndpoint NEST_BOTH;

	static {
		try {
			NO_NESTING = PingingWebsocketModule.class
				.getDeclaredField("NO_NESTING")
				.getAnnotation(PingingClientEndpoint.class);
			NEST_CONNECTION_CTX = PingingWebsocketModule.class
				.getDeclaredField("NEST_CONNECTION_CTX")
				.getAnnotation(PingingClientEndpoint.class);
			NEST_HTTP_SESSION_CTX = PingingWebsocketModule.class
				.getDeclaredField("NEST_HTTP_SESSION_CTX")
				.getAnnotation(PingingClientEndpoint.class);
			NEST_BOTH = PingingWebsocketModule.class
				.getDeclaredField("NEST_BOTH")
				.getAnnotation(PingingClientEndpoint.class);
		} catch (NoSuchFieldException neverHappens) {
			throw new AssertionError("unreachable code", neverHappens);
		}
	}



	protected static class PingingEndpointProvider<EndpointT>
			extends ClientEndpointProvider<EndpointT> {

		@Override
		protected GuiceEndpointConfigurator getConfigurator() { return pingingConfigurator; }
		@Inject PingingEndpointConfigurator pingingConfigurator;

		protected PingingEndpointProvider(
			Class<EndpointT> clientEndpointClass,
			boolean nestConnectionCtx,
			boolean nestHttpSessionCtx
		) {
			super(clientEndpointClass, nestConnectionCtx, nestHttpSessionCtx);
		}
	}
}
