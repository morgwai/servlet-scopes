// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import jakarta.websocket.server.ServerEndpointConfig.Configurator;

import com.google.inject.Module;
import com.google.inject.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toList;

import static pl.morgwai.base.servlet.guice.scopes.HttpSessionContext.CUSTOM_SERIALIZATION_PARAM;



/**
 * Base class for app {@link ServletContextListener}s, creates and configures the app-wide
 * {@link Injector} and {@link ServletModule}, performs {@link GuiceServerEndpointConfigurator}
 * initialization. Also performs cleanup of {@link ServletContextTrackingExecutor}s adn provides
 * helper methods for programmatically adding {@link Servlet}s, {@link Filter}s and websocket
 * {@code Endpoints}.
 * <p>
 * Usually a single subclass of this class should be created in a given app and either annotated
 * with {@link jakarta.servlet.annotation.WebListener @WebListener} or enlisted in the app's
 * {@code web.xml} file in a {@code <listener>} element.</p>
 * <p>
 * Note that it is not mandatory for app {@link ServletContextListener}s to extend this class: all
 * the setup is done using public APIs and can be done manually as well.
 * See the code of {@code ManualServletContextListener} class in the sample app for an example.</p>
 * @see pl.morgwai.base.servlet.guice.utils.PingingServletContextListener
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/**
	 * Creates {@link Module}s that configure bindings for user-defined components.
	 * The result will be passed to {@link #createInjector(LinkedList)}. {@link #servletModule} and
	 * other internal {@code Modules} will be added automatically to the list.
	 * <p>
	 * Implementations may use {@link com.google.inject.Scope}s,
	 * {@link pl.morgwai.base.guice.scopes.ContextTracker}s and helper methods from
	 * {@link #servletModule} and config data from {@link #appDeployment} when defining
	 * {@link Module}s.</p>
	 */
	protected abstract LinkedList<Module> configureInjections() throws Exception;



	/**
	 * Programmatically adds {@link Servlet}s, {@link Filter}s, websocket {@code Endpoints} and
	 * performs any other setup required by the given app. Called at the end of
	 * {@link #contextInitialized(ServletContextEvent)}, may use {@link #injector} (as well as
	 * {@link #appDeployment} and {@link #endpointContainer}).
	 * <p>
	 * Convenience helper method families {@link #addServlet(String, Class, String...)},
	 * {@link #addFilter(String, Class, String...)}, {@link #addEndpoint(Class, String)} are
	 * provided for the most common cases.</p>
	 */
	protected abstract void configureServletsFiltersEndpoints() throws Exception;



	/**
	 * Deployment reference for use in {@link #configureInjections()} and
	 * {@link #configureServletsFiltersEndpoints()}.
	 */
	protected ServletContext appDeployment;
	/**
	 * Name of the deployment for logging purposes. Obtained via
	 * {@link ServletContext#getServletContextName()} if present, otherwise constructed using
	 * {@link ServletContext#getContextPath()}.
	 */
	protected String deploymentName;

	/** The app-wide {@link ServletModule}. For use in {@link #configureInjections()}. */
	protected final ServletModule servletModule = new ServletModule();
	/** Reference to {@link #servletModule}'s field, for use in {@link #configureInjections()}. */
	protected final Scope containerCallScope = servletModule.containerCallScope;
	/** Reference to {@link #servletModule}'s field, for use in {@link #configureInjections()}. */
	protected final Scope httpSessionScope = servletModule.httpSessionScope;
	/** Reference to {@link #servletModule}'s field, for use in {@link #configureInjections()}. */
	protected final Scope websocketConnectionScope = servletModule.websocketConnectionScope;



	/**
	 * The app-wide {@link Injector}. For use in {@link #configureServletsFiltersEndpoints()}.
	 * Initialized with the result of {@link #createInjector(LinkedList)}.
	 * <p>
	 * The app-wide {@code Injector} is also stored as a
	 * {@link ServletContext#getAttribute(String) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link Injector} class.</p>
	 */
	protected Injector injector;

	/**
	 * Creates {@link #injector the app-wide Injector}. This method is called once by
	 * {@link #contextInitialized(ServletContextEvent)}. By default it calls
	 * {@link Guice#createInjector(Iterable) Guice.createInjector(modules)}. May be overridden if
	 * any additional customizations are required.
	 */
	protected Injector createInjector(LinkedList<Module> modules) {
		return Guice.createInjector(modules);
	}



	/**
	 * {@link ServletContext#createServlet(Class) Creates a Servet} of {@code servletClass} class,
	 * {@link ServletContext#addServlet(String, Servlet) adds it} with {@code name},
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} and
	 * {@link Injector#injectMembers(Object) injected dependencies} at
	 * {@link ServletRegistration#addMapping(String...) urlPatterns}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
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
	 * {@link ServletContext#addFilter(String, Filter) Adds} {@code filter} with {@code name},
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} and
	 * {@link Injector#injectMembers(Object) injected dependencies}.
	 * Mappings should be added afterwards with
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)} or
	 * {@link FilterRegistration.Dynamic#addMappingForServletNames(EnumSet, boolean, String...)}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
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
	 * {@link ServletContext#addFilter(String, Filter) Adds it} with {@code name},
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} and
	 * {@link Injector#injectMembers(Object) injected dependencies} at
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)
	 * urlPatterns} with {@code dispatcherTypes}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
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
	 * {@link ServletContext#addFilter(String, Filter) Adds it} with {@code name},
	 * {@link Registration.Dynamic#setAsyncSupported(boolean) async support} and
	 * {@link Injector#injectMembers(Object) injected dependencies} at
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)
	 * urlPatterns} with {@link DispatcherType#REQUEST}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
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
	 * {@link jakarta.servlet.http.HttpSession} created. This is necessary for websocket
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
	 * The {@link Configurator} instance used by {@link #addEndpoint(Class, String)} to create
	 * {@code Endpoint} instances. Initialized with the result of
	 * {@link #createEndpointConfigurator()}.
	 * <p>
	 * Note that this instance will be shared among all {@code Endpoints} created by
	 * {@link #addEndpoint(Class, String)}, but {@code Endpoints} annotated with
	 * {@link jakarta.websocket.server.ServerEndpoint} will have their separate instances even if they
	 * use the same {@link Configurator} class as this one.</p>
	 */
	protected GuiceServerEndpointConfigurator endpointConfigurator;

	/**
	 * Creates the {@link Configurator} that will be used by {@link #addEndpoint(Class, String)}.
	 * This method is called once in {@link #contextInitialized(ServletContextEvent)} to initialize
	 * {@link #endpointConfigurator}.
	 * <p>
	 * By default a new {@link GuiceServerEndpointConfigurator} is returned. This method may be
	 * overridden if a more specialized configurator needs to be used.</p>
	 */
	protected GuiceServerEndpointConfigurator createEndpointConfigurator() {
		return new GuiceServerEndpointConfigurator(appDeployment);
	}



	/**
	 * {@code Endpoint} container reference for use in {@link #configureServletsFiltersEndpoints()}.
	 */
	protected ServerContainer endpointContainer;

	/**
	 * Adds an {@code Endpoint} using {@link #endpointConfigurator}.
	 * Pre-builds a dynamic proxy class for {@code endpointClass} in advance.
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 * </p>
	 * Useful mostly for unannotated endpoints extending {@link jakarta.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path) throws DeploymentException {
		addEndpoint(endpointClass, path, endpointConfigurator);
		endpointConfigurator.getProxyClass(endpointClass);  // pre-build dynamic proxy class
	}

	/**
	 * Adds an {@code Endpoint} using {@code configurator}.
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 * <p>
	 * Useful mostly for unannotated endpoints extending {@link jakarta.websocket.Endpoint}.</p>
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
	 * Adds {@code configurationHook} to be called by
	 * {@link #contextInitialized(ServletContextEvent)} right before
	 * {@link #configureServletsFiltersEndpoints()}. This is intended for abstract subclasses to
	 * hook in their stuff. Concrete {@code Listeners} should rather perform all their setup in
	 * {@link #configureInjections()} and {@link #configureServletsFiltersEndpoints()}.
	 */
	protected void addConfigurationHook(Callable<Void> configurationHook) {
		configurationHooks.add(configurationHook);
	}

	private final List<Callable<Void>> configurationHooks = new LinkedList<>();



	/**
	 * Calls {@link #configureInjections()}, {@link #createInjector(LinkedList) creates the
	 * app-wide Injector} and calls {@link #configureServletsFiltersEndpoints()}.
	 * Also creates and installs all other infrastructure elements such as
	 * {@link #appDeployment}, {@link #endpointContainer}, {@link RequestContextFilter} etc and
	 * stores {@link #injector} as a
	 * {@link ServletContext#setAttribute(String, Object) deployment attribute} under
	 * {@link Class#getName() fully-qualified name} of {@link Injector} class.
	 */
	@Override
	public final void contextInitialized(ServletContextEvent initialization) {
		try {
			appDeployment = initialization.getServletContext();
			final String nameFromDescriptor = appDeployment.getServletContextName();
			deploymentName = (nameFromDescriptor != null && !nameFromDescriptor.isBlank())
					? nameFromDescriptor
					: appDeployment.getContextPath().isEmpty()
							? "rootApp" : "app at " + appDeployment.getContextPath();
			log.info(deploymentName + " is being deployed");
			servletModule.appDeployment = appDeployment;
			endpointContainer = (ServerContainer)
					appDeployment.getAttribute(ServerContainer.class.getName());
			appDeployment.addListener(new HttpSessionContext.SessionContextCreator());

			final var modules = configureInjections();
			modules.add(servletModule);
			injector = createInjector(modules);
			appDeployment.setAttribute(Injector.class.getName(), injector);
			endpointConfigurator = createEndpointConfigurator();
			GuiceServerEndpointConfigurator.registerDeployment(appDeployment);

			addFilter(RequestContextFilter.class.getSimpleName(), RequestContextFilter.class)
				.addMappingForUrlPatterns(
					EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC),
					false,
					"/*"
				);

			for (var configurationHook: configurationHooks) configurationHook.call();
			configureServletsFiltersEndpoints();
			if (appDeployment.getAttribute(CUSTOM_SERIALIZATION_PARAM) == null) {
				appDeployment.setAttribute(
					CUSTOM_SERIALIZATION_PARAM,
					Boolean.parseBoolean(appDeployment.getInitParameter(CUSTOM_SERIALIZATION_PARAM))
				);
			}
			log.info(deploymentName + " deployed successfully");
		} catch (Throwable e) {
			final var message = deploymentName + " failed to deploy";
			log.log(Level.SEVERE, message, e);
			e.printStackTrace();
			if (e instanceof Error) throw (Error) e;
			throw new RuntimeException(message, e);
		}
	}



	/**
	 * Returns the timeout for {@link ServletModule#awaitTerminationOfAllExecutors(long, TimeUnit)
	 * termination of all Executors} obtained from {@link #servletModule}.
	 * By default {@code 5} seconds.
	 * <p>
	 * This method is called by {@link #contextDestroyed(ServletContextEvent)} and may be overridden
	 * if a different value needs to be used.</p>
	 */
	protected Duration getExecutorsTerminationTimeout() { return Duration.ofSeconds(5); }



	/**
	 * Handles executors that failed to terminate in {@link #contextDestroyed(ServletContextEvent)}.
	 * By default calls {@link java.util.concurrent.ExecutorService#shutdownNow() shutdownNow()} for
	 * each executor and hopes for the best...
	 * Subclasses may override this method to handle unterminated executors in a more specialized
	 * way.
	 */
	protected void handleUnterminatedExecutors(
		List<ServletContextTrackingExecutor> unterminatedExecutors
	) {
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



	/**
	 * {@link ServletModule#shutdownAllExecutors() Shutdowns} and
	 * {@link ServletModule#awaitTerminationOfAllExecutors(long, TimeUnit) awaits termination all
	 * Executors} created by {@link #servletModule}. If after the timeout returned by
	 * {@link #getExecutorsTerminationTimeout()} not all {@code Executors} are terminated,
	 * calls {@link #handleUnterminatedExecutors(List)}.
	 */
	@Override
	public final void contextDestroyed(ServletContextEvent destruction) {
		log.info(deploymentName + " is shutting down");
		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
		servletModule.shutdownAllExecutors();
		List<ServletContextTrackingExecutor> unterminatedExecutors;
		try {
			unterminatedExecutors = servletModule.awaitTerminationOfAllExecutors(
					getExecutorsTerminationTimeout().toNanos(), NANOSECONDS);
		} catch (InterruptedException e) {
			unterminatedExecutors = servletModule.getExecutors().stream()
				.filter(not(ServletContextTrackingExecutor::isTerminated))
				.collect(toList());
		}
		if ( !unterminatedExecutors.isEmpty()) handleUnterminatedExecutors(unterminatedExecutors);
		for (var shutdownHook : shutdownHooks) shutdownHook.run();
	}



	static final Logger log = Logger.getLogger(GuiceServletContextListener.class.getName());
}
