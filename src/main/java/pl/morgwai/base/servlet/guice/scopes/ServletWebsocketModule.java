// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import javax.servlet.ServletContext;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;



/**
 * Embeds a {@link WebsocketModule} and adds functionality for {@code Servlet} and websocket server
 * containers.
 * Most notably defines {@link #httpSessionScope} and setups
 * {@link GuiceServerEndpointConfigurator}.
 * @see GuiceServletContextListener#servletModule
 */
public class ServletWebsocketModule implements Module {



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



	/**
	 * {@link WebsocketModule} from
	 * {@link #ServletWebsocketModule(ServletContext, WebsocketModule) the constructor} param.
	 */
	public final WebsocketModule websocketModule;

	/** Reference to {@link #websocketModule}'s {@link WebsocketModule#ctxTracker ctxTracker}. */
	public final ContextTracker<ContainerCallContext> ctxTracker;
	/**
	 * Reference to {@link #websocketModule}'s
	 * {@link WebsocketModule#containerCallScope containerCallScope}.
	 */
	public final Scope containerCallScope;
	/**
	 * Reference to {@link #websocketModule}'s
	 * {@link WebsocketModule#websocketConnectionScope websocketConnectionScope}.
	 */
	public final Scope websocketConnectionScope;



	/**
	 * {@link ServletContext} from
	 * {@link #ServletWebsocketModule(ServletContext, WebsocketModule) the constructor} param.
	 */
	public ServletContext getAppDeployment() { return appDeployment; }
	ServletContext appDeployment;

	/** For {@link GuiceServletContextListener}. */
	void setAppDeployment(ServletContext deployment) {
		if (appDeployment != null) throw new IllegalStateException("appDeployment already set");
		appDeployment = deployment;
	}



	public ServletWebsocketModule(ServletContext appDeployment, WebsocketModule websocketModule) {
		this(websocketModule);
		this.appDeployment = appDeployment;
	}

	/** For {@link GuiceServletContextListener}. */
	ServletWebsocketModule(WebsocketModule websocketModule) {
		this.websocketModule = websocketModule;
		httpSessionScope = new InducedContextScope<>(
			"ServletWebsocketModule.httpSessionScope",
			websocketModule.ctxTracker,
			ContainerCallContext::getHttpSessionContext
		);

		ctxTracker = websocketModule.ctxTracker;
		containerCallScope = websocketModule.containerCallScope;
		websocketConnectionScope = websocketModule.websocketConnectionScope;
		allTrackers = websocketModule.allTrackers;
		ctxBinder = websocketModule.ctxBinder;
	}



	/**
	 * {@link Binder#install(Module) Installs} {@link #websocketModule} and
	 * {@link Binder#requestStaticInjection(Class[]) injects static fields} of
	 * {@link GuiceServerEndpointConfigurator}.
	 * Additionally binds some infrastructure stuff:
	 * <ul>
	 *   <li>{@link ServletContext} to {@link #getAppDeployment() appDeployment}</li>
	 *   <li>{@link HttpSessionContext} to a {@link Provider} returning instance current for the
	 *       calling {@code Thread}</li>
	 * </ul>
	 */
	@Override
	public void configure(Binder binder) {
		if (appDeployment == null) throw new IllegalStateException("appDeployment not set");
		binder.install(websocketModule);
		binder.bind(ServletContext.class).toInstance(appDeployment);
		binder.bind(HttpSessionContext.class).toProvider(
				() -> ctxTracker.getCurrentContext().getHttpSessionContext());
		binder.requestStaticInjection(GuiceServerEndpointConfigurator.class);
	}



	/** Reference to {@link #websocketModule}'s {@link WebsocketModule#allTrackers allTrackers}. */
	public final List<ContextTracker<?>> allTrackers;
	/** Reference to {@link #websocketModule}'s {@link WebsocketModule#ctxBinder ctxBinder}. */
	public final ContextBinder ctxBinder;

	/** Calls {@link WebsocketModule#getActiveContexts()}. */
	public List<TrackableContext<?>> getActiveContexts() {
		return websocketModule.getActiveContexts();
	}



	/**
	 * Reference to {@link WebsocketModule}'s {@link WebsocketModule#ctxTrackerKey ctxTrackerKey}.
	 */
	public static final Key<ContextTracker<ContainerCallContext>> ctxTrackerKey =
			WebsocketModule.ctxTrackerKey;
	/**
	 * Reference to {@link WebsocketModule}'s {@link WebsocketModule#allTrackersKey allTrackersKey}.
	 */
	public static final Key<List<ContextTracker<?>>> allTrackersKey =
			WebsocketModule.allTrackersKey;
}
