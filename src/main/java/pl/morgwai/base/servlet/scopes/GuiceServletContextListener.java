// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.EnumSet;
import java.util.LinkedList;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.Servlet;
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
 * Creates and configures app wide Guice {@link #INJECTOR} and {@link ServletModule}.
 * A single subclass of this class must be created and either annotated with
 * {@link javax.servlet.annotation.WebListener @WebListener} or enlisted in
 * <code>web.xml</code> file in <code>listener</code> element.
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/**
	 * Returns Guice {@link Module} list that configure bindings for all user-defined components
	 * that will be requested from Guice {@link #INJECTOR}.
	 * <p>
	 * Implementations may use
	 * {@link com.google.inject.Scope}s, {@link pl.morgwai.base.guice.scopes.ContextTracker}s
	 * and helper methods from {@link #servletModule}.</p>
	 */
	protected abstract LinkedList<Module> configureInjections() throws ServletException;

	/**
	 * For use in {@link #configureInjections()}.
	 */
	protected ServletModule servletModule = new ServletModule();

	/**
	 * App wide Guice {@link Injector}. Exposed as {@code public static} for non-programmatic
	 * servlets/filters to manually request dependency injection with
	 * {@link Injector#injectMembers(Object)} (usually in the <code>init(config)</code> method).
	 */
	public static Injector INJECTOR;



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
	 * {@link #INJECTOR} is created.</p>
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
	 * Adds an async servlet and injects its dependencies.<br/>
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 */
	protected ServletRegistration.Dynamic addServlet(
			String name, Class<? extends HttpServlet> servletClass, String... urlPatterns)
			throws ServletException {
		Servlet servlet = servletContainer.createServlet(servletClass);
		INJECTOR.injectMembers(servlet);
		ServletRegistration.Dynamic reg = servletContainer.addServlet(name, servlet);
		reg.addMapping(urlPatterns);
		reg.setAsyncSupported(true);
		log.info("registered servlet " + name);
		return reg;
	}



	/**
	 * Adds an async filter at the end of the chain (with <code>isMatchAfter==true</code> and
	 * {@link DispatcherType#REQUEST}) and injects its dependencies.<br/>
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 */
	protected FilterRegistration.Dynamic addFilter(
			String name, Class<? extends Filter> filterClass, String... urlPatterns)
			throws ServletException {
		Filter filter = servletContainer.createFilter(filterClass);
		INJECTOR.injectMembers(filter);
		FilterRegistration.Dynamic reg = servletContainer.addFilter(name, filter);
		reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns);
		reg.setAsyncSupported(true);
		log.info("registered filter " + name);
		return reg;
	}



	/**
	 * Adds an endpoint using {@link GuiceServerEndpointConfigurator} that injects the dependencies
	 * and sets up contexts.
	 * Useful mostly for unannotated endpoints extending {@link javax.websocket.Endpoint}.<br/>
	 * For use in {@link #configureServletsFiltersEndpoints()}.
	 * @see #addEndpoint(Class, String, Configurator)
	 */
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		addEndpoint(endpointClass, path, new GuiceServerEndpointConfigurator());
	}

	/**
	 * Adds an endpoint.<br/>
	 * Helper for subclasses that need to override {@link #addEndpoint(Class, String)} to use
	 * custom {@code configurator}.
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
	 * Calls {@link #configureInjections()}, creates {@link #INJECTOR} and
	 * {@link RequestContextFilter}, finally calls {@link #configureServletsFiltersEndpoints()}.
	 */
	@Override
	public void contextInitialized(ServletContextEvent initializationEvent) {
		try {
			LinkedList<Module> modules = configureInjections();
			modules.add(servletModule);
			INJECTOR = Guice.createInjector(modules);
			log.info("Guice INJECTOR created successfully");

			servletContainer = initializationEvent.getServletContext();
			websocketContainer = ((ServerContainer) servletContainer.getAttribute(
					"javax.websocket.server.ServerContainer"));

			Filter requestContextFilter = servletContainer.createFilter(RequestContextFilter.class);
			INJECTOR.injectMembers(requestContextFilter);
			FilterRegistration.Dynamic reg =
					servletContainer.addFilter("requestContextFilter", requestContextFilter);
			reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");
			reg.setAsyncSupported(true);

			configureServletsFiltersEndpoints();
		} catch (ServletException e) {
			log.error("could not start the server", e);
			System.exit(1);
		}
	}



	protected static final Logger log =
			LoggerFactory.getLogger(GuiceServletContextListener.class.getName());
}
