// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.Set;
import java.util.function.Function;

import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;

import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;



/**
 * Defines {@link #containerCallScope} and {@link #websocketConnectionScope}, configures
 * {@link GuiceEndpointConfigurator}.
 * Usually a single instance is created at the startup and its member {@link Scope}s passed to
 * other {@link com.google.inject.Module}s to scope their bindings: see
 * <a href="https://github.com/morgwai/guice-context-scopes#developing-portable-modules">"Developing
 * portable Modules"</a> article.
 * <p>
 * Next, depending on the app type, different steps should be performed:</p>
 * <ul>
 *   <li>In case of standalone client-only websocket apps, the {@link WebsocketModule} instance
 *       should just be passed to {@link Guice#createInjector(com.google.inject.Module...)} method
 *       along with other {@link com.google.inject.Module}s to create the app-wide
 *       {@link Injector}.</li>
 *   <li>In case of standalone websocket server or mixed client-server apps, the
 *       {@link WebsocketModule} instance should be passed to
 *       {@link Guice#createInjector(com.google.inject.Module...)} together with a
 *       {@link StandaloneWebsocketServerModule} and other {@link com.google.inject.Module}s.</li>
 *   <li>In case of websocket apps embedded in {@code Servlet} containers, the
 *       {@link WebsocketModule} instance should be embedded by a
 *       {@link ServletWebsocketModule#ServletWebsocketModule(jakarta.servlet.ServletContext,
 *       WebsocketModule) ServletModule} instance.</li>
 * </ul>
 * @see pl.morgwai.base.servlet.guice.utils.PingingWebsocketModule
 */
public class WebsocketModule extends ScopeModule {



	/**
	 * Scopes objects to the {@code Context} of either an
	 * {@link ServletRequestContext HttpServletRequests} or a
	 * {@link WebsocketEventContext websocket event} depending which type is active at the moment of
	 * a given {@link Provider#get() provisioning}.
	 */
	public final ContextScope<ContainerCallContext> containerCallScope =
			newContextScope("WebsocketModule.containerCallScope", ContainerCallContext.class);

	/**
	 * Scopes objects to the {@link WebsocketConnectionContext Context of a websocket connections
	 * (jakarta.websocket.Session)}.
	 * This {@code Scope} is induced by and active <b>only</b> within
	 * {@link WebsocketEventContext}s.
	 */
	public final Scope websocketConnectionScope = newInducedContextScope(
		"WebsocketModule.websocketConnectionScope",
		WebsocketConnectionContext.class,
		containerCallScope.tracker,
		WebsocketModule::getWebsocketConnectionContext
	);

	static WebsocketConnectionContext getWebsocketConnectionContext(ContainerCallContext eventCtx) {
		try {
			return ((WebsocketEventContext) eventCtx).getConnectionContext();
		} catch (ClassCastException e) {
			throw new OutOfScopeException(WS_CONNECTION_CTX_WITHIN_SERVLET_CTX_MESSAGE);
		}
	}

	static final String WS_CONNECTION_CTX_WITHIN_SERVLET_CTX_MESSAGE =
			"cannot provide a websocketConnectionScope-d Object within a ServletRequestContext";

	public final ContextBinder ctxBinder = newContextBinder();



	/** See {@link GuiceEndpointConfigurator#checkIfRequiredEndpointMethodsPresent(Class)}. */
	protected final boolean requireTopLevelMethodAnnotations;

	/**
	 * Client {@code Endpoint} classes that will be {@link #configure(Binder) bound} to a
	 * {@link GuiceEndpointConfigurator} based {@link Provider} for use
	 * with @{@link GuiceClientEndpoint} annotation.
	 */
	protected final Set<Class<?>> clientEndpointClasses;



	public WebsocketModule(
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		this.requireTopLevelMethodAnnotations = requireTopLevelMethodAnnotations;
		this.clientEndpointClasses = Set.copyOf(clientEndpointClasses);
	}

	/**
	 * Calls {@link #WebsocketModule(boolean, Set)
	 * this(requireTopLevelMethodAnnotations, Set.of(clientEndpointClasses))}.
	 */
	public WebsocketModule(
		boolean requireTopLevelMethodAnnotations,
		Class<?>... clientEndpointClasses
	) {
		this(requireTopLevelMethodAnnotations, Set.of(clientEndpointClasses));
	}



	/**
	 * Calls {@link ScopeModule#configure(Binder) super} and binds {@link #clientEndpointClasses}
	 * annotated with {@link GuiceClientEndpoint} to {@link Provider}s based on
	 * {@link GuiceEndpointConfigurator}.
	 * Additionally binds {@link GuiceEndpointConfigurator#REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY}
	 * to {@link #requireTopLevelMethodAnnotations}.
	 */
	@Override
	public void configure(Binder binder) {
		super.configure(binder);
		binder.bind(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY)
			.toInstance(requireTopLevelMethodAnnotations);
		for (var clientEndpointClass: clientEndpointClasses) {
			bindClientEndpoint(binder, clientEndpointClass);
		}
	}

	<EndpointT> void bindClientEndpoint(Binder binder, Class<EndpointT> clientEndpointClass) {
		binder.bind(clientEndpointClass).annotatedWith(NO_NESTING)
			.toProvider(new ClientEndpointProvider<>(clientEndpointClass, false, false));
		binder.bind(clientEndpointClass).annotatedWith(NEST_CONNECTION_CTX)
			.toProvider(new ClientEndpointProvider<>(clientEndpointClass, true, false));
		binder.bind(clientEndpointClass).annotatedWith(NEST_HTTP_SESSION_CTX)
			.toProvider(new ClientEndpointProvider<>(clientEndpointClass, false, true));
		binder.bind(clientEndpointClass).annotatedWith(NEST_BOTH)
			.toProvider(new ClientEndpointProvider<>(clientEndpointClass, true, true));
	}

	@GuiceClientEndpoint(nestConnectionContext = false, nestHttpSessionContext = false)
	static final GuiceClientEndpoint NO_NESTING;
	@GuiceClientEndpoint(nestHttpSessionContext = false)
	static final GuiceClientEndpoint NEST_CONNECTION_CTX;
	@GuiceClientEndpoint(nestConnectionContext = false)
	static final GuiceClientEndpoint NEST_HTTP_SESSION_CTX;
	@GuiceClientEndpoint
	static final GuiceClientEndpoint NEST_BOTH;

	static {
		try {
			NO_NESTING = WebsocketModule.class.getDeclaredField("NO_NESTING")
					.getAnnotation(GuiceClientEndpoint.class);
			NEST_CONNECTION_CTX = WebsocketModule.class.getDeclaredField("NEST_CONNECTION_CTX")
					.getAnnotation(GuiceClientEndpoint.class);
			NEST_HTTP_SESSION_CTX = WebsocketModule.class.getDeclaredField("NEST_HTTP_SESSION_CTX")
					.getAnnotation(GuiceClientEndpoint.class);
			NEST_BOTH = WebsocketModule.class.getDeclaredField("NEST_BOTH")
					.getAnnotation(GuiceClientEndpoint.class);
		} catch (NoSuchFieldException neverHappens) {
			throw new AssertionError("unreachable code", neverHappens);
		}
	}



	protected static class ClientEndpointProvider<EndpointT> implements Provider<EndpointT> {

		protected GuiceEndpointConfigurator getConfigurator() { return configurator; }
		@Inject GuiceEndpointConfigurator configurator;

		final Class<EndpointT> clientEndpointClass;
		final boolean nestConnectionCtx;
		final boolean nestHttpSessionCtx;



		protected ClientEndpointProvider(
			Class<EndpointT> clientEndpointClass,
			boolean nestConnectionCtx,
			boolean nestHttpSessionCtx
		) {
			this.clientEndpointClass = clientEndpointClass;
			this.nestConnectionCtx = nestConnectionCtx;
			this.nestHttpSessionCtx = nestHttpSessionCtx;
		}



		@Override public EndpointT get() {
			try {
				return getConfigurator().getProxiedEndpointInstance(
					clientEndpointClass,
					nestConnectionCtx,
					nestHttpSessionCtx
				);
			} catch (Exception e) {
				throw new ProvisionException(e.getMessage(), e);
			}
		}
	}



	static final TypeLiteral<ContextTracker<ContainerCallContext>> CTX_TRACKER_TYPE =
			new TypeLiteral<>() {};
	/** {@code Key} for the {@link ContextTracker} of {@link #containerCallScope}. */
	public static final Key<ContextTracker<ContainerCallContext>> CTX_TRACKER_KEY =
			Key.get(CTX_TRACKER_TYPE);



	/** For {@link ServletWebsocketModule}. */
	<
		BaseContextT extends TrackableContext<? super BaseContextT>,
		InducedContextT extends InjectionContext
	> InducedContextScope<BaseContextT, InducedContextT> packageExposedNewInducedContextScope(
		String name,
		Class<InducedContextT> inducedCtxClass,
		ContextTracker<BaseContextT> baseCtxTracker,
		Function<BaseContextT, InducedContextT> inducedCtxRetriever
	) {
		return newInducedContextScope(name, inducedCtxClass, baseCtxTracker, inducedCtxRetriever);
	}
}
