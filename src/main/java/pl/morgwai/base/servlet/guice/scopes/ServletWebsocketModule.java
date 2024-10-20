// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

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
 * @see pl.morgwai.base.servlet.guice.utils.PingingWebsocketModule
 * @see pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext
 */
public class ServletWebsocketModule implements Module {



	/**
	 * Scopes objects to the {@link HttpSessionContext Contexts of HttpSessions}.
	 * As {@link HttpSessionContext} is induced by {@link ContainerCallContext}, this
	 * {@link Scope} may be active both within {@link ServletRequestContext}s and
	 * {@link WebsocketEventContext}s.
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

	/**
	 * Reference to {@link WebsocketModule#containerCallScope websocketModule.containerCallScope}.
	 */
	public final ContextScope<ContainerCallContext> containerCallScope;
	/**
	 * Reference to
	 * {@link WebsocketModule#websocketConnectionScope websocketModule.websocketConnectionScope}.
	 */
	public final Scope websocketConnectionScope;
	/** Reference to {@link WebsocketModule#ctxBinder websocketModule.ctxBinder}. */
	public final ContextBinder ctxBinder;



	/**
	 * {@link ServletContext} from
	 * {@link #ServletWebsocketModule(ServletContext, WebsocketModule) the constructor} param.
	 */
	public ServletContext getAppDeployment() { return appDeployment; }
	ServletContext appDeployment;

	/** For {@link GuiceServletContextListener}. */
	void setAppDeployment(ServletContext appDeployment) {
		assert this.appDeployment == null : "appDeployment already set";
		this.appDeployment = appDeployment;
	}



	public ServletWebsocketModule(ServletContext appDeployment, WebsocketModule websocketModule) {
		this(websocketModule);
		this.appDeployment = appDeployment;
	}

	/** For {@link GuiceServletContextListener}. */
	ServletWebsocketModule(WebsocketModule websocketModule) {
		this.websocketModule = websocketModule;
		containerCallScope = websocketModule.containerCallScope;
		websocketConnectionScope = websocketModule.websocketConnectionScope;
		ctxBinder = websocketModule.ctxBinder;
		httpSessionScope = websocketModule.packageExposedNewInducedContextScope(
			"ServletWebsocketModule.httpSessionScope",
			HttpSessionContext.class,
			containerCallScope.tracker,
			ContainerCallContext::getHttpSessionContext
		);
	}



	/**
	 * {@link Binder#install(Module) Installs} {@link #websocketModule}, binds
	 * {@link ServletContext} type to {@link #appDeployment} and
	 * {@link Binder#requestStaticInjection(Class[]) injects static fields} of
	 * {@link GuiceServerEndpointConfigurator}.
	 * This is in order for {@link GuiceServerEndpointConfigurator} instances created by the
	 * container (for {@code Endpoint}s annotated
	 * with @{@link javax.websocket.server.ServerEndpoint} using
	 * {@link GuiceServerEndpointConfigurator}) to get a reference to the {@link Injector}.</p>
	 */
	@Override
	public void configure(Binder binder) {
		if (appDeployment == null) throw new IllegalStateException("appDeployment not set");
		binder.install(websocketModule);
		binder.bind(ServletContext.class).toInstance(appDeployment);
		binder.requestStaticInjection(GuiceServerEndpointConfigurator.class);
	}
}
