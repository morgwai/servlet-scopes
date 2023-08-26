// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;



/**
 * Creates and configures {@link #injector app-wide Guice Injector} and a {@link ServletModule}.
 * Usually a single subclass of this class should be created in a given app and either annotated
 * with {@link javax.servlet.annotation.WebListener @WebListener} or enlisted in the app's
 * {@code web.xml} file in a {@code <listener>} element.
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/**
	 * Returns {@link Module}s that configure bindings for user-defined components.
	 * The result will be passed to {@link #createInjector(LinkedList)}. {@link #servletModule} and
	 * other internal {@code Modules} will be added automatically to the list.
	 * <p>
	 * Implementations may use {@link com.google.inject.Scope}s,
	 * {@link pl.morgwai.base.guice.scopes.ContextTracker}s and helper methods from
	 * {@link #servletModule} when defining {@link Module}s being added.</p>
	 */
	protected abstract LinkedList<Module> configureInjections() throws Exception;

	/** For use in {@link #configureInjections()}. */
	protected final ServletModule servletModule = new ServletModule();
	/** Same as in {@link #servletModule} for convenience. */
	protected final ContextTracker<ContainerCallContext> containerCallContextTracker =
			servletModule.containerCallContextTracker;
	/** Same as in {@link #servletModule} for use in {@link #configureInjections()}. */
	protected final Scope containerCallScope = servletModule.containerCallScope;
	/** Same as in {@link #servletModule} for use in {@link #configureInjections()}. */
	protected final Scope httpSessionScope = servletModule.httpSessionScope;
	/** Same as in {@link #servletModule} for use in {@link #configureInjections()}. */
	protected final Scope websocketConnectionScope = servletModule.websocketConnectionScope;

	/**
	 * The app-wide {@link Injector}. For use in {@link #configureServletsFiltersEndpoints()}.
	 * {@code Injector} is also stored as a
	 * {@link ServletContext#getAttribute(String) ServletContext attribute} named after
	 * {@link Injector}'s class {@link Class#getName() fully-qualified name}.
	 */
	protected Injector injector;

	/**
	 * Creates {@link #injector the app-wide Injector} when called by
	 * {@link #contextInitialized(ServletContextEvent)}. By default basically calls
	 * {@link Guice#createInjector(Iterable) Guice.createInjector(modules)}.
	 * May be overridden if any additional customizations are needed.
	 */
	protected Injector createInjector(LinkedList<Module> modules) {
		return Guice.createInjector(modules);
	}



	/**
	 * Adds {@code configurationHook} to be called in
	 * {@link #contextInitialized(ServletContextEvent)} right before
	 * {@link #configureServletsFiltersEndpoints()}. This is intended for abstract subclasses to
	 * hook in their stuff. Concrete app listeners should rather do all their setup in
	 * {@link #configureServletsFiltersEndpoints()}.
	 */
	protected void addConfigurationHook(Callable<Void> configurationHook) {
		configurationHooks.add(configurationHook);
	}

	private final List<Callable<Void>> configurationHooks = new LinkedList<>();



	/**
	 * Programmatically adds {@link Servlet}s, {@link Filter}s and {@code Endpoints} and performs
	 * any other setup required by the given app. Called at the end of
	 * {@link #contextInitialized(ServletContextEvent)}, may use {@link #injector} (as well as
	 * {@link #servletContainer} and {@link #endpointContainer}).
	 * <p>
	 * Convenience helper method families {@link #addServlet(String, Class, String...)},
	 * {@link #addFilter(String, Class, String...)}, {@link #addEndpoint(Class, String)} are
	 * provided for the most common cases for use in {@code configurationHook}.</p>
	 */
	protected abstract void configureServletsFiltersEndpoints()
			throws ServletException, DeploymentException;

	/** For use in {@link #configureServletsFiltersEndpoints()}. */
	protected ServletContext servletContainer;

	/** For use in {@link #configureServletsFiltersEndpoints()}. */
	protected ServerContainer endpointContainer;



	/**
	 * Adds a servlet with async support and injects its dependencies.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected ServletRegistration.Dynamic addServlet(
		String name,
		Class<? extends HttpServlet> servletClass,
		String... urlPatterns
	) throws ServletException {
		final var servlet = servletContainer.createServlet(servletClass);
		injector.injectMembers(servlet);
		final var registration = servletContainer.addServlet(name, servlet);
		registration.addMapping(urlPatterns);
		registration.setAsyncSupported(true);
		log.info("registered servlet " + name);
		return registration;
	}



	/**
	 * Adds {@code filter} with async support and injects its dependencies. Filter mappings should
	 * be added afterwards with
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)} or
	 * {@link FilterRegistration.Dynamic#addMappingForServletNames(EnumSet, boolean, String...)}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(String name, Filter filter) {
		injector.injectMembers(filter);
		final var registration = servletContainer.addFilter(name, filter);
		registration.setAsyncSupported(true);
		log.info("registered filter " + name);
		return registration;
	}

	/**
	 * Adds a {@code Filter} of {@code filterClass} with async support, injects its dependencies,
	 * then adds a mapping at the end of the chain for {@code urlPatterns} with
	 * {@code dispatcherTypes}. Additional mappings can be added afterwards with
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)} or
	 * {@link FilterRegistration.Dynamic#addMappingForServletNames(EnumSet, boolean, String...)}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(
		String name,
		Class<? extends Filter> filterClass,
		EnumSet<DispatcherType> dispatcherTypes,
		String... urlPatterns
	) throws ServletException {
		final var registration = addFilter(name, servletContainer.createFilter(filterClass));
		if (urlPatterns.length > 0) {
			registration.addMappingForUrlPatterns(dispatcherTypes, true, urlPatterns);
		}
		return registration;
	}

	/**
	 * Adds a {@code Filter} of {@code filterClass} with async support, injects its dependencies,
	 * then adds a mapping at the end of the chain for {@code urlPatterns} with
	 * {@link DispatcherType#REQUEST}. Additional mappings can be added afterwards with
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)} or
	 * {@link FilterRegistration.Dynamic#addMappingForServletNames(EnumSet, boolean, String...)}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(
		String name,
		Class<? extends Filter> filterClass,
		String... urlPatterns
	) throws ServletException {
		final var registration = addFilter(name, servletContainer.createFilter(filterClass));
		if (urlPatterns.length > 0) registration.addMappingForUrlPatterns(null, true, urlPatterns);
		return registration;
	}

	/**
	 * Installs at {@code urlPatterns} a filter that ensures each incoming request has an
	 * {@link javax.servlet.http.HttpSession} created. This is necessary for websocket
	 * {@code Endpoints} that use {@link ServletModule#httpSessionScope httpSessionScope}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected void addEnsureSessionFilter(String... urlPatterns) {
		addFilter("ensureSessionFilter", (request, response, chain) -> {
			((HttpServletRequest) request).getSession();
			chain.doFilter(request, response);
		}).addMappingForUrlPatterns(null, false, urlPatterns);
	}



	/**
	 * Stores the result of {@link #createEndpointConfigurator()} to be used by
	 * {@link #addEndpoint(Class, String)}.
	 */
	protected GuiceServerEndpointConfigurator endpointConfigurator;

	/**
	 * Creates configurator to be used by {@link #addEndpoint(Class, String)}.
	 * By default {@link GuiceServerEndpointConfigurator} that injects {@code Endpoints}'
	 * dependencies and sets up contexts around their lifecycle methods.
	 * <p>
	 * This method is called once in {@link #contextInitialized(ServletContextEvent)}, the result is
	 * stored as {@link #endpointConfigurator} and shared among all {@code Endpoint} instances
	 * created with {@link #addEndpoint(Class, String)}.</p>
	 * <p>
	 * Note that {@code Endpoints} annotated with {@link javax.websocket.server.ServerEndpoint}
	 * will have their separate instances of {@link Configurator} each.</p>
	 * <p>
	 * This method may be overridden by subclasses if a more specialized configurator needs to be
	 * used.</p>
	 */
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		return new GuiceServerEndpointConfigurator(injector, containerCallContextTracker);
	}



	/**
	 * Adds an endpoint using {@link #endpointConfigurator}. For use in
	 * {@link #configureServletsFiltersEndpoints()}.
	 * <p>
	 * Pre-builds dynamic proxy class for {@code endpointClass} in advance.</p>
	 * <p>
	 * Useful mostly for unannotated endpoints extending {@link javax.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path) throws DeploymentException {
		addEndpoint(endpointClass, path, endpointConfigurator);
		endpointConfigurator.getProxyClass(endpointClass);  // pre-build dynamic proxy class
	}

	/**
	 * Adds an endpoint using custom {@code configurator}.
	 * <p>
	 * Useful mostly for unannotated endpoints extending {@link javax.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path, Configurator configurator)
			throws DeploymentException {
		endpointContainer.addEndpoint(
			ServerEndpointConfig.Builder
				.create(endpointClass, path)
				.configurator(configurator)
				.build()
		);
		log.info("registered endpoint " + endpointClass.getSimpleName());
	}



	/**
	 * Calls {@link #configureInjections()}, {@link #createInjector(LinkedList) creates the
	 * app-wide Injector} and calls {@link #configureServletsFiltersEndpoints()}.
	 * Also creates and installs all other infrastructure elements such as
	 * {@link #servletContainer}, {@link #endpointContainer}, {@link RequestContextFilter} etc and
	 * stores {@link #injector} in a
	 * {@link ServletContext#setAttribute(String, Object) ServletContext attribute} named after
	 * {@link Injector}'s class {@link Class#getName() fully-qualified name}.
	 */
	@Override
	public final void contextInitialized(ServletContextEvent initialization) {
		try {
			servletContainer = initialization.getServletContext();
			servletModule.servletContext = servletContainer;
			endpointContainer = ((ServerContainer) servletContainer.getAttribute(
					"javax.websocket.server.ServerContainer"));
			servletContainer.addListener(new HttpSessionContext.SessionContextCreator());

			final var modules = configureInjections();
			modules.add(servletModule);
			injector = createInjector(modules);
			servletContainer.setAttribute(Injector.class.getName(), injector);
			log.info("Guice Injector created successfully");

			addFilter(RequestContextFilter.class.getSimpleName(), RequestContextFilter.class)
					.addMappingForUrlPatterns(
							EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*");
			GuiceServerEndpointConfigurator.registerInjector(injector, servletContainer);
			endpointConfigurator = createEndpointConfigurator();

			for (var configurationHook: configurationHooks) configurationHook.call();
			configureServletsFiltersEndpoints();
		} catch (Exception e) {
			final var message = "could not deploy the app at " + servletContainer.getContextPath();
			log.log(Level.SEVERE, message, e);
			e.printStackTrace();
			throw new RuntimeException(message, e);
		}
	}



	/**
	 * Shutdowns and awaits termination all executors created by {@link #servletModule}. If after
	 * the timeout specified by {@link #getExecutorsTerminationTimeoutSeconds()} not all executors
	 * are terminated, calls {@link #handleUnterminatedExecutors(List)}.
	 */
	@Override
	public final void contextDestroyed(ServletContextEvent destruction) {
		GuiceServerEndpointConfigurator.deregisterInjector(servletContainer);
		servletModule.shutdownAllExecutors();
		List<ServletContextTrackingExecutor> unterminatedExecutors;
		try {
			unterminatedExecutors = servletModule.awaitTerminationOfAllExecutors(
					getExecutorsTerminationTimeoutSeconds(), TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			unterminatedExecutors = servletModule.getExecutors().stream()
				.filter((executor) -> !executor.isTerminated())
				.collect(Collectors.toList());
		}
		if ( !unterminatedExecutors.isEmpty()) handleUnterminatedExecutors(unterminatedExecutors);
		for (var shutdownHook : shutdownHooks) shutdownHook.run();
	}



	/**
	 * Returns the timeout for termination of executors obtained from {@link #servletModule}.
	 * By default {@code 5} seconds. Called by {@link #contextDestroyed(ServletContextEvent)}.
	 * May be overridden if different value should be used.
	 */
	protected int getExecutorsTerminationTimeoutSeconds() { return 5; }

	/**
	 * Handles executors that failed to terminate in {@link #contextDestroyed(ServletContextEvent)}.
	 * By default calls {@link java.util.concurrent.ExecutorService#shutdownNow() shutdownNow()} for
	 * each executor and hopes for the best...
	 * Subclasses may override this method to handle unterminated executors in a more specialized
	 * way.
	 */
	protected void handleUnterminatedExecutors(
			List<ServletContextTrackingExecutor> unterminatedExecutors) {
		for (var executor: unterminatedExecutors) executor.shutdownNow();
	}



	/**
	 * Adds {@code shutdownHook} to be run at the end of
	 * {@link #contextDestroyed(ServletContextEvent)}.
	 */
	protected void addShutdownHook(Runnable shutdownHook) {
		shutdownHooks.add(shutdownHook);
	}

	private final List<Runnable> shutdownHooks = new LinkedList<>();



	protected static final Logger log =
			Logger.getLogger(GuiceServletContextListener.class.getName());
}
