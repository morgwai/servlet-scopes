// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.logging.Logger;
import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.ContextBinder;

import static java.util.logging.Level.SEVERE;
import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_PARAM;



/**
 * Base class for app {@link ServletContextListener}s, creates and configures the app-wide
 * {@link Injector} and {@link ServletWebsocketModule}, performs
 * {@link GuiceServerEndpointConfigurator} initialization.
 * Also provides helper methods for programmatically adding {@link Servlet}s, {@link Filter}s and
 * websocket {@code Endpoints}.
 * <p>
 * Usually a single subclass of this class should be created in a given app and either annotated
 * with {@link javax.servlet.annotation.WebListener @WebListener} or enlisted in the app's
 * {@code web.xml} file in a {@code <listener>} element.</p>
 * <p>
 * Note that it is not mandatory for app {@link ServletContextListener}s to extend this class: all
 * the setup is done using public APIs and can be done manually as well.
 * See the code of {@code ManualServletContextListener} class in the sample app for an example.</p>
 * @see pl.morgwai.base.servlet.guice.utils.PingingServletContextListener
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/** Action to be performed at an app shutdown. See {@link #addShutdownHook(ShutdownHook)}. */
	public interface ShutdownHook {
		void shutdown() throws InterruptedException;
	}

	/** Adds {@code shutdownHook} to be run by {@link #contextDestroyed(ServletContextEvent)}. */
	protected void addShutdownHook(ShutdownHook shutdownHook) {
		shutdownHooks.add(shutdownHook);
	}

	private final List<ShutdownHook> shutdownHooks = new LinkedList<>();



	/**
	 * Deployment reference for use in {@link #configureInjections()} and
	 * {@link #addServletsFiltersEndpoints()}.
	 */
	protected ServletContext appDeployment;
	/**
	 * Name of the deployment for logging purposes.
	 * Constructed using {@link ServletContext#getServletContextName()} and
	 * {@link ServletContext#getContextPath()} : {@code "app name" at "/deployment/path"}. If
	 * {@link ServletContext#getServletContextName() getServletContextName()} is empty, then
	 * {@code app at "/deployment/path"}.
	 */
	protected String deploymentName;



	/**
	 * Returns client {@code Endpoint} {@code Class}es that will be bound for injection in
	 * {@link #createWebsocketModule(boolean, Set) the app-wide WebsocketModule}.
	 * @return by default an empty {@code Set}, may be overridden in subclasses.
	 */
	protected Set<Class<?>> getClientEndpointClasses() { return Set.of(); }

	/**
	 * Creates the app-wide {@link WebsocketModule} passed to {@link #servletModule}.
	 * @return by default a new {@link WebsocketModule}, may be overridden if more specialized
	 *     implementation is needed.
	 * @see pl.morgwai.base.servlet.guice.utils.PingingWebsocketModule
	 */
	protected WebsocketModule createWebsocketModule(
		boolean requireTopLevelMethodAnnotations,
		Set<Class<?>> clientEndpointClasses
	) {
		return new WebsocketModule(requireTopLevelMethodAnnotations, clientEndpointClasses);
	}



	/** The app-wide {@link ServletWebsocketModule} for use in {@link #configureInjections()}. */
	protected ServletWebsocketModule servletModule;
	/**
	 * Reference to {@link #servletModule}'s
	 * {@link ServletWebsocketModule#containerCallScope containerCallScope}, for use in
	 * {@link #configureInjections()}.
	 */
	protected Scope containerCallScope;
	/**
	 * Reference to {@link #servletModule}'s
	 * {@link ServletWebsocketModule#httpSessionScope httpSessionScope}, for use in
	 * {@link #configureInjections()}.
	 */
	protected Scope httpSessionScope;
	/**
	 * Reference to {@link #servletModule}'s
	 * {@link ServletWebsocketModule#websocketConnectionScope websocketConnectionScope}, for use in
	 * {@link #configureInjections()}.
	 */
	protected Scope websocketConnectionScope;
	/**
	 * Reference to {@link #servletModule}'s {@link ServletWebsocketModule#ctxBinder ctxBinder}, for
	 * use in {@link #configureInjections()}.
	 */
	protected ContextBinder ctxBinder;



	/**
	 * Creates {@link Module}s with bindings for user-defined components.
	 * The result will be passed to {@link #createInjector(LinkedList)}. {@link #servletModule} and
	 * other internal {@code Modules} will be added automatically to the result.
	 * <p>
	 * Implementations may use {@link com.google.inject.Scope}s,
	 * {@link pl.morgwai.base.guice.scopes.ContextTracker}s and helpers from {@link #servletModule}
	 * and {@link #appDeployment} data when defining {@link Module}s.</p>
	 */
	protected abstract LinkedList<Module> configureInjections() throws Exception;

	/**
	 * The app-wide {@link Injector}.
	 * For use in {@link #addServletsFiltersEndpoints()}. Initialized with the result of
	 * {@link #createInjector(LinkedList)}.
	 * <p>
	 * The app-wide {@code Injector} is also stored as a
	 * {@link ServletContext#getAttribute(String) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link Injector} class.</p>
	 */
	protected Injector injector;

	/**
	 * Creates {@link #injector the app-wide Injector}.
	 * @param modules the result of {@link #configureInjections()} plus {@link #servletModule} and
	 *      some other infrastructure {@link Module}s.
	 * @return by default the result of
	 *     {@link Guice#createInjector(Iterable) Guice.createInjector(modules)}. May be overridden
	 *     if any additional customizations are needed.
	 */
	protected Injector createInjector(LinkedList<Module> modules) {
		return Guice.createInjector(modules);
	}



	/** {@code Endpoint} container reference for use in {@link #addServletsFiltersEndpoints()}. */
	protected ServerContainer endpointContainer;

	/**
	 * The {@link Configurator} instance used by {@link #addEndpoint(Class, String)} to create
	 * {@code Endpoint} instances.
	 * Initialized with the result of {@link #createEndpointConfigurator(Injector)}.
	 * <p>
	 * Note that this instance will be shared among all {@code Endpoints} created by
	 * {@link #addEndpoint(Class, String)}, but {@code Endpoints} annotated with
	 * {@link javax.websocket.server.ServerEndpoint} will have their separate instances even if they
	 * use the same {@link Configurator} class as this one.</p>
	 */
	protected GuiceServerEndpointConfigurator endpointConfigurator;

	/**
	 * Creates the {@link Configurator} that will be used by {@link #addEndpoint(Class, String)}
	 * ({@link #endpointConfigurator}).
	 * @return by default a new {@link GuiceServerEndpointConfigurator}. May be
	 *     overridden if a more specialized implementation is needed.
	 * @see pl.morgwai.base.servlet.guice.utils.PingingServerEndpointConfigurator
	 */
	protected GuiceServerEndpointConfigurator createEndpointConfigurator(Injector injector) {
		return new GuiceServerEndpointConfigurator(injector);
	}

	/**
	 * Adds an {@code Endpoint} using {@link #endpointConfigurator}.
	 * Pre-builds a dynamic proxy class for {@code endpointClass} in advance.
	 * </p>
	 * For use in {@link #addServletsFiltersEndpoints()}. Useful mostly for unannotated
	 * {@code Endpoint}s extending {@link javax.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path) throws DeploymentException {
		addEndpoint(endpointClass, path, endpointConfigurator);
		endpointConfigurator.getProxyClass(endpointClass);  // pre-build dynamic proxy class
	}

	/**
	 * Adds an {@code Endpoint} using {@code configurator}.
	 * </p>
	 * For use in {@link #addServletsFiltersEndpoints()}. Useful mostly for unannotated
	 * {@code Endpoint}s extending {@link javax.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path, Configurator configurator)
		throws DeploymentException {
		endpointContainer.addEndpoint(
			ServerEndpointConfig.Builder
				.create(endpointClass, path)
				.configurator(configurator)
				.build()
		);
		log.info(deploymentName + ": added Endpoint " + endpointClass.getSimpleName());
	}



	/**
	 * {@link ServletContext#createServlet(Class) Creates a Servet} of {@code servletClass} class,
	 * {@link Injector#injectMembers(Object) injects its dependencies} and
	 * {@link ServletContext#addServlet(String, Servlet) adds it} with
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} under {@code name} at
	 * {@link ServletRegistration#addMapping(String...) urlPatterns}.
	 * <p>
	 * For use in {@link #addServletsFiltersEndpoints()}.</p>
	 */
	protected ServletRegistration.Dynamic addServlet(
		String name,
		Class<? extends HttpServlet> servletClass,
		String... urlPatterns
	) throws ServletException {
		final var servlet = appDeployment.createServlet(servletClass);
		injector.injectMembers(servlet);
		final var registration = appDeployment.addServlet(name, servlet);
		registration.addMapping(urlPatterns);
		registration.setAsyncSupported(true);
		log.info(deploymentName + ": added servlet " + name);
		return registration;
	}



	/**
	 * {@link ServletContext#addFilter(String, Filter) Adds} {@code filter} under {@code name} with
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} and
	 * {@link Injector#injectMembers(Object) injected dependencies}.
	 * Mappings should be added afterwards with
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)} or
	 * {@link FilterRegistration.Dynamic#addMappingForServletNames(EnumSet, boolean, String...)}.
	 * <p>
	 * For use in {@link #addServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(String name, Filter filter) {
		injector.injectMembers(filter);
		final var registration = appDeployment.addFilter(name, filter);
		registration.setAsyncSupported(true);
		log.info(deploymentName + ": added filter " + name);
		return registration;
	}

	/**
	 * {@link ServletContext#createFilter(Class) Creates a Filter} of {@code filterClass} class,
	 * {@link Injector#injectMembers(Object) injects its dependencies} and
	 * {@link ServletContext#addFilter(String, Filter) adds it} with,
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} under {@code name} at
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)
	 * urlPatterns} with {@code dispatcherTypes}.
	 * <p>
	 * For use in {@link #addServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(
		String name,
		Class<? extends Filter> filterClass,
		EnumSet<DispatcherType> dispatcherTypes,
		String... urlPatterns
	) throws ServletException {
		final var registration = addFilter(name, appDeployment.createFilter(filterClass));
		if (urlPatterns.length > 0) {
			registration.addMappingForUrlPatterns(dispatcherTypes, true, urlPatterns);
		}
		return registration;
	}

	/**
	 * {@link ServletContext#createFilter(Class) Creates a Filter} of {@code filterClass} class,
	 * {@link Injector#injectMembers(Object) injects its dependencies} and
	 * {@link ServletContext#addFilter(String, Filter) adds it} with,
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} under {@code name} at
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)
	 * urlPatterns} with {@link DispatcherType#REQUEST}.
	 * <p>
	 * For use in {@link #addServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(
		String name,
		Class<? extends Filter> filterClass,
		String... urlPatterns
	) throws ServletException {
		final var registration = addFilter(name, appDeployment.createFilter(filterClass));
		if (urlPatterns.length > 0) registration.addMappingForUrlPatterns(null, true, urlPatterns);
		return registration;
	}

	/**
	 * Adds at {@code urlPatterns} a {@link Filter} that ensures each incoming request has an
	 * {@link javax.servlet.http.HttpSession} created.
	 * This is necessary for websocket {@code Endpoints} that use
	 * {@link ServletWebsocketModule#httpSessionScope httpSessionScope}.
	 * <p>
	 * For use in {@link #addServletsFiltersEndpoints()}.</p>
	 */
	protected void addEnsureSessionFilter(String... urlPatterns) {
		addFilter("ensureSessionFilter", (request, response, chain) -> {
			((HttpServletRequest) request).getSession();
			chain.doFilter(request, response);
		}).addMappingForUrlPatterns(null, false, urlPatterns);
	}



	/**
	 * Programmatically adds {@link Servlet}s, {@link Filter}s, websocket {@code Endpoints} and
	 * performs any other setup required by the given app.
	 * Called at the end of {@link #contextInitialized(ServletContextEvent)}, may use
	 * {@link #injector} (as well as {@link #appDeployment} and {@link #endpointContainer}).
	 * <p>
	 * Convenience helper method families {@link #addServlet(String, Class, String...)},
	 * {@link #addFilter(String, Class, String...)}, {@link #addEndpoint(Class, String)} are
	 * provided for the most common cases.</p>
	 */
	protected abstract void addServletsFiltersEndpoints() throws Exception;



	/**
	 * Initializes the app.
	 * The exact sequence of events is as follows:
	 * <ol>
	 *   <li>Initializes {@link #appDeployment} with a reference from {@code initialization} event,
	 *       then initializes {@link #deploymentName} and {@link #endpointContainer}.</li>
	 *   <li>Obtains
	 *       {@link GuiceEndpointConfigurator#REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_PARAM} from
	 *       {@link #appDeployment}, calls {@link #getClientEndpointClasses()} and
	 *       {@link #createWebsocketModule(boolean, Set)} to initialize {@link #servletModule}.</li>
	 *   <li>Calls {@link #configureInjections()}.</li>
	 *   <li>Initializes {@link #injector} by passing {@link Module}s from the previous point and
	 *       {@link #servletModule} to {@link #createInjector(LinkedList)}.</li>
	 *   <li>Initializes {@link #endpointConfigurator} with
	 *       {@link #createEndpointConfigurator(Injector)}.</li>
	 *   <li>Installs {@link RequestContextFilter} and calls
	 *       {@link #addServletsFiltersEndpoints()}.</li>
	 * </ol>
	 */
	@Override
	public final void contextInitialized(ServletContextEvent initialization) {
		try {
			// 1
			appDeployment = initialization.getServletContext();
			final var nameFromDescriptor = appDeployment.getServletContextName();
			final var appName = (nameFromDescriptor == null || nameFromDescriptor.isBlank())
					? "app" : '"' + nameFromDescriptor + '"';
			deploymentName = appName + " at \"" + appDeployment.getContextPath() + '"';
			log.info(deploymentName + " is being deployed");
			endpointContainer = (ServerContainer)
					appDeployment.getAttribute(ServerContainer.class.getName());
			appDeployment.addListener(new HttpSessionContext.SessionContextCreator());

			// 2
			final var requireTopLevelMethodAnnotations = Boolean.parseBoolean(
					appDeployment.getInitParameter(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_PARAM));
			servletModule = new ServletWebsocketModule(
				appDeployment,
				createWebsocketModule(
					requireTopLevelMethodAnnotations,
					getClientEndpointClasses()
				)
			);
			containerCallScope = servletModule.containerCallScope;
			httpSessionScope = servletModule.httpSessionScope;
			websocketConnectionScope = servletModule.websocketConnectionScope;
			ctxBinder = servletModule.ctxBinder;

			// 3
			final var modules = configureInjections();

			// 4
			modules.add(servletModule);
			injector = createInjector(modules);

			// 5
			endpointConfigurator = createEndpointConfigurator(injector);

			// 6
			addFilter(RequestContextFilter.class.getSimpleName(), RequestContextFilter.class)
				.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
			addServletsFiltersEndpoints();
			log.info(deploymentName + " deployed successfully");
		} catch (Throwable e) {
			final var message = deploymentName + " failed to deploy";
			log.log(SEVERE, message, e);
			e.printStackTrace();
			if (e instanceof Error) throw (Error) e;
			throw new RuntimeException(message, e);
		}
	}



	/**
	 * {@link GuiceServerEndpointConfigurator#deregisterInjector(Injector) Deregisters}
	 * {@link #injector} from {@link GuiceServerEndpointConfigurator} and executes all
	 * {@link #addShutdownHook(ShutdownHook) registered} {@link ShutdownHook}s.
	 * If a {@link ShutdownHook} being executed throws an {@link InterruptedException}, the
	 * {@link Thread#currentThread() current Thread} will be
	 * {@link Thread#interrupt() marked as interrupted} and the execution of the remaining ones will
	 * continue.
	 */
	@Override
	public final void contextDestroyed(ServletContextEvent destruction) {
		log.info(deploymentName + " is shutting down");
		GuiceServerEndpointConfigurator.deregisterInjector(injector);
		final var currentThread = Thread.currentThread();
		for (var shutdownHook: shutdownHooks) {
			try {
				shutdownHook.shutdown();
			} catch (InterruptedException e) {
				currentThread.interrupt();
			}
		}
		final var ignored = Thread.interrupted();
	}



	static final Logger log = Logger.getLogger(GuiceServletContextListener.class.getName());
}
