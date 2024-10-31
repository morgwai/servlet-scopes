// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.ServletContext;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;

import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator
		.APP_DEPLOYMENT_PATH_KEY;



/**
 * {@link Module} for mixed Servlet-websocket apps.
 * Embeds a {@link WebsocketModule} and defines {@link #httpSessionScope}.
 * <p>
 * Usually a single instance is created in {@link javax.servlet.ServletContextListener} and passed
 * to {@link Guice#createInjector(Module...)} together with other {@link Module}s.</p>
 * @see pl.morgwai.base.servlet.guice.utils.PingingWebsocketModule
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
	public final ServletContext appDeployment;



	public ServletWebsocketModule(ServletContext appDeployment, WebsocketModule websocketModule) {
		this.appDeployment = appDeployment;
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
	 * {@link ServletContext} type to {@link #appDeployment}, stores the resulting {@link Injector}
	 * in {@link #appDeployment} and static structures of {@link GuiceServerEndpointConfigurator}
	 * class.
	 * This allows {@link GuiceServerEndpointConfigurator} instances created by the container (for
	 * {@code Endpoint}s annotated with @{@link javax.websocket.server.ServerEndpoint}) to get a
	 * reference to the {@link Injector}.
	 * <p>
	 * The resulting {@link Injector} is stored in {@link #appDeployment}'s
	 * {@link ServletContext#getAttribute(String) attribute} named after
	 * {@link Class#getName() fully-qualified name} of {@link Injector} class.</p>
	 */
	@Override
	public void configure(Binder binder) {
		binder.install(websocketModule);
		binder.bind(ServletContext.class)
			.toInstance(appDeployment);
		binder.bind(APP_DEPLOYMENT_PATH_KEY)
			.toInstance(appDeployment.getContextPath());
		binder.requestStaticInjection(ServletWebsocketModule.class);
				// calls storeInjectorInDeployment(...)
		binder.requestStaticInjection(GuiceServerEndpointConfigurator.class);
				// calls GuiceServerEndpointConfigurator.registerInjector(...)
	}

	/** Called by {@link #configure(Binder)}. */
	@Inject
	static void storeInjectorInDeployment(Injector injector, ServletContext appDeployment) {
		appDeployment.setAttribute(Injector.class.getName(), injector);
	}
}
