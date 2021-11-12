// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.EnumSet;
import java.util.LinkedList;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServlet;
import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.server.ServerEndpointConfig.Configurator;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * Creates and configures {@link #getInjector() app wide Guice injector} and {@link ServletModule}.
 * A single subclass of this class must be created and either annotated with
 * {@link javax.servlet.annotation.WebListener @WebListener} or enlisted in
 * <code>web.xml</code> file in <code>listener</code> element.
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/**
	 * Returns {@link Module}s that configure bindings for user-defined components.
	 * The result will be passed when creating {@link #getInjector() the app wide injector}.
	 * <p>
	 * Implementations may use {@link com.google.inject.Scope}s,
	 * {@link pl.morgwai.base.guice.scopes.ContextTracker}s and helper methods from
	 * {@link #servletModule}.</p>
	 */
	protected abstract LinkedList<Module> configureInjections() throws ServletException;

	/**
	 * For use in {@link #configureInjections()}.
	 */
	protected final ServletModule servletModule = new ServletModule();

	/**
	 * Returns app wide Guice {@link Injector}. Exposed as {@code public static}
	 * for non-programmatic servlets/filters to manually request dependency injection with
	 * {@link Injector#injectMembers(Object)} (usually in the <code>init(config)</code> method).
	 */
	public static Injector getInjector() { return injector; }
	static Injector injector;



	/**
	 * Programmatically adds servlets, filters and endpoints.
	 * <p>
	 * If all of these components are configured via annotations or <code>web.xml</code> file,
	 * then this method may be empty.</p>
	 * <p>
	 * Convenience helper methods {@link #addServlet(String, Class, String...)},
	 * {@link #addFilter(String, Class, String...)}, {@link #addEndpoint(Class, String)} and
	 * {@link #addEndpoint(Class, String, Configurator)} are provided for the most common cases.</p>
	 * <p>
	 * This method is called <b>after</b> {@link #configureInjections()} is called and
	 * {@link #getInjector() the injector} is created.</p>
	 */
	protected abstract void configureServletsFiltersEndpoints() throws ServletException;

	/**
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 */
	protected ServletContext servletContainer;

	/**
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 */
	protected ServerContainer websocketContainer;



	/**
	 * Adds a servlet with async support and injects its dependencies.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected ServletRegistration.Dynamic addServlet(
			String name, Class<? extends HttpServlet> servletClass, String... urlPatterns)
			throws ServletException {
		final var servlet = servletContainer.createServlet(servletClass);
		injector.injectMembers(servlet);
		final var registration = servletContainer.addServlet(name, servlet);
		registration.addMapping(urlPatterns);
		registration.setAsyncSupported(true);
		log.info("registered servlet " + name);
		return registration;
	}



	/**
	 * Adds a filter with async support and injects its dependencies. Filter mappings should be
	 * added afterwards with
	 * {@link FilterRegistration.Dynamic#addMappingForUrlPatterns(EnumSet, boolean, String...)} or
	 * {@link FilterRegistration.Dynamic#addMappingForServletNames(EnumSet, boolean, String...)}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(String name, Class<? extends Filter> filterClass)
			throws ServletException {
		final var filter = servletContainer.createFilter(filterClass);
		injector.injectMembers(filter);
		final var registration = servletContainer.addFilter(name, filter);
		registration.setAsyncSupported(true);
		log.info("registered filter " + name);
		return registration;
	}

	/**
	 * Adds a filter with async support and injects its dependencies, then adds a mapping at the
	 * end of the chain for {@code urlPatterns} and {@code dispatcherTypes}.
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(
			String name,
			Class<? extends Filter> filterClass,
			EnumSet<DispatcherType> dispatcherTypes,
			String... urlPatterns)
			throws ServletException {
		final var registration = addFilter(name, filterClass);
		registration.addMappingForUrlPatterns(dispatcherTypes, true, urlPatterns);
		return registration;
	}

	/**
	 * Adds a filter with async support and injects its dependencies, then adds a mapping at the
	 * end of the chain for {@code urlPatterns} and {@link DispatcherType#REQUEST} .
	 * <p>
	 * For use in {@link #configureServletsFiltersEndpoints()}.</p>
	 */
	protected FilterRegistration.Dynamic addFilter(
			String name, Class<? extends Filter> filterClass, String... urlPatterns)
			throws ServletException {
		return addFilter(name, filterClass, null, urlPatterns);
	}



	/**
	 * Adds an endpoint using {@link GuiceServerEndpointConfigurator} that injects the dependencies
	 * and sets up contexts.&nbsp;
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 * <p>
	 * Useful mostly for unannotated endpoints extending {@link javax.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		addEndpoint(endpointClass, path, new GuiceServerEndpointConfigurator());
	}

	/**
	 * Adds an endpoint using custom {@code configurator}.
	 * <p>
	 * Useful mostly for unannotated endpoints extending {@link javax.websocket.Endpoint}.</p>
	 */
	protected void addEndpoint(Class<?> endpointClass, String path, Configurator configurator)
			throws ServletException {
		try {
			websocketContainer.addEndpoint(
				ServerEndpointConfig.Builder
					.create(endpointClass, path)
					.configurator(configurator)
					.build());
			log.info("registered endpoint " + endpointClass.getSimpleName());
		} catch (DeploymentException e) {
			throw new ServletException(e);
		}
	}



	/**
	 * Calls {@link #configureInjections()}, creates {@link #getInjector() the injector} and
	 * calls {@link #configureServletsFiltersEndpoints()}.
	 * <p>
	 * Also adds {@link RequestContextFilter} at the beginning of the chain.</p>
	 */
	@Override
	public final void contextInitialized(ServletContextEvent initializationEvent) {
		try {
			servletContainer = initializationEvent.getServletContext();
			websocketContainer = ((ServerContainer) servletContainer.getAttribute(
					"javax.websocket.server.ServerContainer"));
			servletContainer.addListener(new ContainerCallContext.SessionContextCreator());

			final var modules = configureInjections();
			modules.add(servletModule);
			injector = createInjector(modules);
			log.info("Guice injector created successfully");

			addFilter(RequestContextFilter.class.getSimpleName(), RequestContextFilter.class)
					.addMappingForUrlPatterns(
							EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*");

			configureServletsFiltersEndpoints();
		} catch (ServletException e) {
			log.error("could not start the server", e);
			System.exit(1);
		}
	}

	/**
	 * Creates injector. By default {@code return Guice.createInjector(modules)}.
	 */
	protected Injector createInjector(LinkedList<Module> modules) {
		return Guice.createInjector(modules);
	}



	/**
	 * Shutdowns all executors from {@link #servletModule}.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent destructionEvent) {
		servletModule.shutdownAllExecutors(getExecutorsShutdownTimeoutSeconds());
	}

	/**
	 * Returns the timeout for shutdown of executors obtained from {@link #servletModule}.
	 * By default 5 seconds.
	 */
	protected int getExecutorsShutdownTimeoutSeconds() { return 5; }



	protected static final Logger log =
			LoggerFactory.getLogger(GuiceServletContextListener.class.getName());
}
