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
 * &commat;{@link javax.servlet.annotation.WebListener WebListener} or enlisted in
 * <code>web.xml</code> file in <code>listener</code> element.
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/**
	 * @return Guice {@link Module} list that configure bindings for all user-defined components
	 * that will be requested from Guice {@link #INJECTOR}. This method may use <code>Scope</code>s
	 * and <Code>Tracker</code>s and helper methods from {@link #servletModule}.
	 */
	protected abstract LinkedList<Module> configureInjections() throws ServletException;
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
	 * then this method may be empty.<br/>
	 * Convenience helper methods {@link #addServlet(String, Class, String...)},
	 * {@link #addFilter(String, Class, String...)}, {@link #addEndpoint(Class, String)} and
	 * {@link #addEndpoint(Class, String, Configurator)} are provided for the most common cases.</p>
	 */
	protected abstract void configureServletsFiltersEndpoints() throws ServletException;
	protected ServletContext ctx;
	protected ServerContainer websocketContainer;



	/**
	 * Adds async servlet and injects its dependencies.
	 */
	protected ServletRegistration.Dynamic addServlet(
			String name, Class<? extends HttpServlet> servletClass, String... urlPatterns)
			throws ServletException {
		Servlet servlet = ctx.createServlet(servletClass);
		INJECTOR.injectMembers(servlet);
		ServletRegistration.Dynamic reg = ctx.addServlet(name, servlet);
		reg.addMapping(urlPatterns);
		reg.setAsyncSupported(true);
		log.info("registered servlet " + name);
		return reg;
	}



	/**
	 * Adds async filter at the end of the chain (with <code>isMatchAfter==true</code> and
	 * {@link DispatcherType#REQUEST}) and injects its dependencies.
	 */
	protected FilterRegistration.Dynamic addFilter(
			String name, Class<? extends Filter> filterClass, String... urlPatterns)
			throws ServletException {
		Filter filter = ctx.createFilter(filterClass);
		INJECTOR.injectMembers(filter);
		FilterRegistration.Dynamic reg = ctx.addFilter(name, filter);
		reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns);
		reg.setAsyncSupported(true);
		log.info("registered filter " + name);
		return reg;
	}



	/**
	 * Adds endpoint.
	 * Useful mostly for unannotated endpoints extending {@link javax.websocket.Endpoint}.
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
	 * Adds endpoint using {@link GuiceServerEndpointConfigurator} that injects the dependencies.
	 * @see #addEndpoint(Class, String, Configurator)
	 */
	protected void addEndpoint(Class<?> endpointClass, String path) throws ServletException {
		addEndpoint(endpointClass, path, new GuiceServerEndpointConfigurator());
	}



	@Override
	public void contextInitialized(ServletContextEvent initializationEvent) {
		try {
			LinkedList<Module> modules = configureInjections();
			modules.add(servletModule);
			INJECTOR = Guice.createInjector(modules);
			log.info("Guice INJECTOR created successfully");

			ctx = initializationEvent.getServletContext();
			websocketContainer = ((ServerContainer) ctx.getAttribute(
					"javax.websocket.server.ServerContainer"));

			Filter requestContextFilter = ctx.createFilter(RequestContextFilter.class);
			INJECTOR.injectMembers(requestContextFilter);
			FilterRegistration.Dynamic reg =
					ctx.addFilter("requestContextFilter", requestContextFilter);
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
