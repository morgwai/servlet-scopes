// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import javax.servlet.ServletContext;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;



/**
 * Contains {@code Servlet} and websocket Guice {@link Scope}s, {@link ContextTracker}s and some
 * helper methods.
 * Usually a single app-wide instance is created at the app startup.
 * @see GuiceServletContextListener#servletModule
 */
public class ServletModule implements Module {



	/**
	 * Scopes objects to the {@link HttpSessionContext Context of an HttpSessions}.
	 * This {@code Scope} is induced by {@link ContainerCallContext}s obtained from
	 * {@link #ctxTracker}, so it may be active both within
	 * {@link ServletRequestContext}s and {@link WebsocketEventContext}s.
	 * <p>
	 * <b>NOTE:</b> it is not possible to create an {@link javax.servlet.http.HttpSession} from the
	 * websocket {@code Endpoint} layer if it doesn't already exist. To safely use this
	 * {@code Scope} in websocket {@code Endpoints}, other layers must ensure that a {@code Session}
	 * exists (for example a {@link javax.servlet.Filter} targeting URL patterns of websockets can
	 * be used: see {@link GuiceServletContextListener#addEnsureSessionFilter(String...)}).</p>
	 * <p>
	 * <b>NOTE:</b> similarly as with
	 * {@link javax.servlet.http.HttpSession#setAttribute(String, Object) Session attributes},
	 * session-scoped objects must be {@link java.io.Serializable} if they need to be transferred
	 * between cluster nodes.</p>
	 */
	public final Scope httpSessionScope;



	public final WebsocketModule websocketModule;

	// todo: javadoc
	public final ContextTracker<ContainerCallContext> ctxTracker;
	public final Scope containerCallScope;
	public final Scope websocketConnectionScope;
	public final List<ContextTracker<?>> allTrackers;
	public final ContextBinder ctxBinder;



	/** {@code appDeployment} to be bound in {@link #configure(Binder)}. */
	public void setAppDeployment(ServletContext appDeployment) {
		if (this.appDeployment != null) {
			throw new IllegalStateException("appDeployment already set");
		}
		this.appDeployment = appDeployment;
	}

	protected ServletContext appDeployment;



	// todo: javadoc update
	public ServletModule(WebsocketModule websocketModule) {
		this.websocketModule = websocketModule;
		httpSessionScope = new InducedContextScope<>(
			"ServletModule.httpSessionScope",
			websocketModule.ctxTracker,
			ContainerCallContext::getHttpSessionContext
		);

		ctxTracker = websocketModule.ctxTracker;
		containerCallScope = websocketModule.containerCallScope;
		websocketConnectionScope = websocketModule.websocketConnectionScope;
		allTrackers = websocketModule.allTrackers;
		ctxBinder = websocketModule.ctxBinder;
	}

	public ServletModule(ServletContext appDeployment, WebsocketModule websocketModule) {
		this(websocketModule);
		this.appDeployment = appDeployment;
	}



	// todo: javadoc
	@Override
	public void configure(Binder binder) {
		if (appDeployment != null) binder.bind(ServletContext.class).toInstance(appDeployment);
		binder.install(websocketModule);
		binder.bind(HttpSessionContext.class).toProvider(
				() -> ctxTracker.getCurrentContext().getHttpSessionContext());
	}



	/** Calls {@link WebsocketModule#getActiveContexts()}. */
	public List<TrackableContext<?>> getActiveContexts() {
		return websocketModule.getActiveContexts();
	}



	public static final Key<ContextTracker<ContainerCallContext>> ctxTrackerKey =
			WebsocketModule.ctxTrackerKey;
	public static final Key<List<ContextTracker<?>>> allTrackersKey =
			WebsocketModule.allTrackersKey;
}
