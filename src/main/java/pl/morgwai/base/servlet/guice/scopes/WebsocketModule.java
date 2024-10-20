// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.function.Function;

import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;

import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;



/**
 * Contains websocket Guice {@link Scope}s, {@link ContextTracker}s and some helper utils.
 * For standalone client {@link javax.websocket.WebSocketContainer}s, usually a single app-wide
 * instance is created at the app startup. In case of servers, it should be embedded by a
 * {@link ServletWebsocketModule}.
 * @see pl.morgwai.base.servlet.guice.utils.PingingWebsocketModule
 */
public class WebsocketModule extends ContextScopesModule {



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
	 * (javax.websocket.Session)}.
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



	/**
	 * Client {@code Endpoint} classes that will be {@link #configure(Binder) bound} to a
	 * {@link GuiceEndpointConfigurator} based {@link Provider} for use
	 * with @{@link GuiceClientEndpoint} annotation.
	 */
	protected final Set<Class<?>> clientEndpointClasses;
	Boolean requireTopLevelMethodAnnotations;



	/**
	 * Initializes {@link #clientEndpointClasses} and leaves
	 * {@link #setRequireTopLevelMethodAnnotations(boolean) requireTopLevelMethodAnnotations} flag
	 * unset.
	 */
	public WebsocketModule(Set<Class<?>> clientEndpointClasses) {
		this.clientEndpointClasses = clientEndpointClasses;
	}

	/**
	 * Calls {@link #WebsocketModule(Set) this(clientEndpointClasses)} and sets
	 * {@link #setRequireTopLevelMethodAnnotations(boolean) requireTopLevelMethodAnnotations} flag.
	 */
	public WebsocketModule(
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		this(clientEndpointClasses);
		this.requireTopLevelMethodAnnotations = requireTopLevelMethodAnnotations;
	}

	/** Calls {@link #WebsocketModule(Set) this(Set.of(clientEndpointClasses))}. */
	public WebsocketModule(Class<?>... clientEndpointClasses) {
		this(Set.of(clientEndpointClasses));
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
	 * Value to be injected as {@link GuiceEndpointConfigurator#requireTopLevelMethodAnnotations}.
	 * @throws IllegalStateException if a value for the flag is already set.
	 */
	public void setRequireTopLevelMethodAnnotations(boolean requireTopLevelMethodAnnotations) {
		if (this.requireTopLevelMethodAnnotations != null) {
			throw new IllegalStateException("requireTopLevelMethodAnnotations already set");
		}
		this.requireTopLevelMethodAnnotations = requireTopLevelMethodAnnotations;
	}



	/**
	 * Calls {@link ContextScopesModule#configure(Binder) super} and binds
	 * {@link #clientEndpointClasses} annotated with {@link GuiceClientEndpoint} to
	 * {@link Provider}s based on {@link GuiceEndpointConfigurator}.
	 * Additionally binds {@link GuiceEndpointConfigurator#REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY}
	 * to {@link #setRequireTopLevelMethodAnnotations(boolean)
	 * requireTopLevelMethodAnnotations flag value} (if unset, then {@code false} is assumed).
	 */
	@Override
	public void configure(Binder binder) {
		super.configure(binder);
		if (requireTopLevelMethodAnnotations == null) requireTopLevelMethodAnnotations = false;
		binder.bind(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY)
			.toInstance(requireTopLevelMethodAnnotations);
		for (var clientEndpointClass: clientEndpointClasses) {
			bindClientEndpoint(binder, clientEndpointClass);
		}
	}

	<EndpointT> void bindClientEndpoint(Binder binder, Class<EndpointT> clientEndpointClass) {
		binder.bind(clientEndpointClass).annotatedWith(GuiceClientEndpoint.class).toProvider(
			new Provider<>() {
				@Inject GuiceEndpointConfigurator endpointConfigurator;

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
