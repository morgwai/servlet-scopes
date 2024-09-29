// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;

import com.google.inject.Module;
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
public class WebsocketModule implements Module {



	/** Allows tracking of {@link ServletRequestContext}s and {@link WebsocketEventContext}s. */
	public final ContextTracker<ContainerCallContext> ctxTracker = new ContextTracker<>();

	/**
	 * Scopes objects to the {@code Context} of either an
	 * {@link ServletRequestContext HttpServletRequests} or a
	 * {@link WebsocketEventContext websocket event} depending which type is active at the moment of
	 * a given {@link Provider#get() provisioning}.
	 */
	public final Scope containerCallScope =
			new ContextScope<>("WebsocketModule.containerCallScope", ctxTracker);



	/**
	 * Scopes objects to the {@link WebsocketConnectionContext Context of a websocket connections
	 * (javax.websocket.Session)}.
	 * This {@code Scope} is induced by and active <b>only</b> within
	 * {@link WebsocketEventContext}s.
	 */
	public final Scope websocketConnectionScope = new InducedContextScope<>(
		"WebsocketModule.websocketConnectionScope",
		ctxTracker,
		WebsocketModule::getWebsocketConnectionContext
	);

	static WebsocketConnectionContext getWebsocketConnectionContext(ContainerCallContext eventCtx) {
		try {
			return ((WebsocketEventContext) eventCtx).getConnectionContext();
		} catch (ClassCastException e) {
			throw new OutOfScopeException(
				"cannot provide a websocketConnectionScope-d Object within a ServletRequestContext"
			);
		}
	}



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
	 * Binds {@link #clientEndpointClasses} annotated with {@link GuiceClientEndpoint} to
	 * {@link Provider}s based on {@link GuiceEndpointConfigurator}.
	 * Additionally binds some infrastructure stuff:
	 * <ul>
	 *   <li>{@link GuiceEndpointConfigurator#REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY} to
	 *       {@link #setRequireTopLevelMethodAnnotations(boolean) requireTopLevelMethodAnnotations
	 *       flag value} (if unset, then {@code false} is assumed)</li>
	 *   <li>{@link #allTrackersKey} to {@link #allTrackers}</li>
	 *   <li>{@link ContextBinder} to {@link #ctxBinder}</li>
	 *   <li>{@link #ctxTrackerKey} to {@link #ctxTracker}</li>
	 *   <li>{@link ContainerCallContext} and {@link WebsocketConnectionContext} to
	 *       {@link Provider}s returning instances current for the calling {@code Thread}</li>
	 * </ul>
	 */
	@Override
	public void configure(Binder binder) {
		if (requireTopLevelMethodAnnotations == null) requireTopLevelMethodAnnotations = false;
		binder.bind(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY)
				.toInstance(requireTopLevelMethodAnnotations);
		binder.bind(allTrackersKey).toInstance(allTrackers);
		binder.bind(ContextBinder.class).toInstance(ctxBinder);
		binder.bind(ctxTrackerKey).toInstance(ctxTracker);
		binder.bind(ContainerCallContext.class).toProvider(ctxTracker::getCurrentContext);
		binder.bind(WebsocketConnectionContext.class).toProvider(
				() -> getWebsocketConnectionContext(ctxTracker.getCurrentContext()));
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



	/**
	 * Singleton of {@link #ctxTracker}.
	 * {@link #allTrackersKey} is bound to this {@code List} in {@link #configure(Binder)} method.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(ctxTracker);
	/** {@code ContextBinder} created using {@link #allTrackers}. */
	public final ContextBinder ctxBinder = new ContextBinder(allTrackers);

	/** Calls {@link ContextTracker#getActiveContexts(List) getActiveContexts(allTrackers)}. */
	public List<TrackableContext<?>> getActiveContexts() {
		return ContextTracker.getActiveContexts(allTrackers);
	}



	static final TypeLiteral<ContextTracker<ContainerCallContext>> ctxTrackerType =
			new TypeLiteral<>() {};
	static final TypeLiteral<List<ContextTracker<?>>> allTrackersType = new TypeLiteral<>() {};
	/** {@code Key} of {@link #ctxTracker}. */
	public static final Key<ContextTracker<ContainerCallContext>> ctxTrackerKey =
			Key.get(ctxTrackerType);
	/** {@code Key} of {@link #allTrackers}. */
	public static final Key<List<ContextTracker<?>>> allTrackersKey = Key.get(allTrackersType);
}
