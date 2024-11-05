// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.Set;
import java.util.function.Function;

import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;

import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;
import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator
		.APP_DEPLOYMENT_PATH_KEY;



/**
 * Contains websocket Guice {@link Scope}s, {@link ContextTracker}s and some helper utils.
 * Usually a single instance is created at an app startup and passed to
 * {@link Guice#createInjector(com.google.inject.Module...) create the app-wide Injector}.
 * <p>
 * Appropriate constructor variant must be used depending whether the app is a
 * {@link #WebsocketModule(String, boolean, Set) standalone websocket ServerContainer} or if it is
 * {@link #WebsocketModule(boolean, Set) embeded in a Servlet container or client-only}.</p>
 * <p>
 * In case of websocket apps embedded in {@code Servlet} containers, an instance should be then
 * embedded by a {@link ServletWebsocketModule}.</p>
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



	/** See {@link GuiceEndpointConfigurator#checkIfRequiredEndpointMethodsPresent(Class)}. */
	protected final boolean requireTopLevelMethodAnnotations;

	/**
	 * Client {@code Endpoint} classes that will be {@link #configure(Binder) bound} to a
	 * {@link GuiceEndpointConfigurator} based {@link Provider} for use
	 * with @{@link GuiceClientEndpoint} annotation.
	 */
	protected final Set<Class<?>> clientEndpointClasses;

	final String standaloneServerDeploymentPath;



	/**
	 * Constructs a new instance for use in standalone
	 * {@link javax.websocket.server.ServerContainer}s.
	 */
	public WebsocketModule(
		String standaloneServerDeploymentPath,
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		this.requireTopLevelMethodAnnotations = requireTopLevelMethodAnnotations;
		this.clientEndpointClasses = Set.copyOf(clientEndpointClasses);
		this.standaloneServerDeploymentPath = standaloneServerDeploymentPath;
	}

	/**
	 * Calls {@link #WebsocketModule(String, boolean, Set)
	 * this(serverDeploymentPath, requireTopLevelMethodAnnotations, Set.of(clientEndpointClasses))}.
	 */
	public WebsocketModule(
		String serverDeploymentPath,
		boolean requireTopLevelMethodAnnotations,
		Class<?>... clientEndpointClasses
	) {
		this(serverDeploymentPath, requireTopLevelMethodAnnotations, Set.of(clientEndpointClasses));
	}



	/**
	 * Constructs a new instance for use in websocket apps that are embedded in {@code Servlet}
	 * containers or are client-only.
	 */
	public WebsocketModule(
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		this(null, requireTopLevelMethodAnnotations, clientEndpointClasses);
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
	 * Calls {@link ContextScopesModule#configure(Binder) super} and binds
	 * {@link #clientEndpointClasses} annotated with {@link GuiceClientEndpoint} to
	 * {@link Provider}s based on {@link GuiceEndpointConfigurator}.
	 * Additionally binds {@link GuiceEndpointConfigurator#REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY}
	 * to {@link #requireTopLevelMethodAnnotations}.
	 * <p>
	 * If this {@code WebsocketModule} was created using {@link
	 * #WebsocketModule(String, boolean, Set) the standalone websocket server constructor variant},
	 * then this method also {@link Binder#requestStaticInjection(Class[]) injects static fields} of
	 * {@link GuiceServerEndpointConfigurator}. This is in order for
	 * {@link GuiceServerEndpointConfigurator} instances created by the container (for
	 * {@code Endpoint}s annotated with @{@link javax.websocket.server.ServerEndpoint} using
	 * {@link GuiceServerEndpointConfigurator}) to get a reference to the {@link Injector}.</p>
	 */
	@Override
	public void configure(Binder binder) {
		super.configure(binder);
		binder.bind(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY)
			.toInstance(requireTopLevelMethodAnnotations);
		if (standaloneServerDeploymentPath != null) {
			binder.bind(APP_DEPLOYMENT_PATH_KEY)
				.toInstance(standaloneServerDeploymentPath);
			binder.requestStaticInjection(GuiceServerEndpointConfigurator.class);
					// calls GuiceServerEndpointConfigurator.registerInjector(...)
		}
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
