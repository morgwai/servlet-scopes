/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;



/**
 * Creates and configures app wide Guice <code>Injector</code> and {@link ServletModule}.
 * A single subclass of this class must be created and either annotated with
 * <code>@WebListener</code> or enlisted in <code>web.xml</code> file in <code>listener</code>
 * element.
 */
public abstract class GuiceServletContextListener implements ServletContextListener {



	/**
	 * @return Guice <code>Module</code> list that configure bindings for all user-defined
	 * components that will be requested from Guice {@link #INJECTOR}. This method may use
	 * <code>Scope</code>s and <Code>Tracker</code>s and helper methods from
	 * {@link #servletModule}.
	 */
	protected abstract LinkedList<Module> configureInjections();
	public static Injector INJECTOR;  // public static for non-programmatic servlets/filters that
			// cannot extend GuicifiedServlet/GuicifiedFilter
	protected ServletModule servletModule;



	/**
	 * Programmatically adds servlets and filters. If all app's servlets and filters are configured
	 * via annotations or <code>web.xml</code> file, then this method can be left empty. Convenience
	 * helper methods {@link #addServlet(String, Class, String...)} and
	 * {@link #addFilter(String, Class, String...)} are provided for the most common cases.
	 */
	protected abstract void configureServletsAndFilters() throws ServletException;
	protected ServletContext ctx;



	/**
	 * Adds async servlet and injects its dependencies.
	 */
	protected ServletRegistration.Dynamic addServlet(
			String name, Class<? extends HttpServlet> aClass, String... urlPatterns)
					throws ServletException {
		Servlet servlet = ctx.createServlet(aClass);
		INJECTOR.injectMembers(servlet);
		ServletRegistration.Dynamic reg = ctx.addServlet(name, servlet);
		reg.addMapping(urlPatterns);
		reg.setAsyncSupported(true);
		log.info("registered servlet " + name);
		return reg;
	}



	/**
	 * Adds async filter for dispatcher type <code>REQUEST</code> and injects its dependencies.
	 */
	protected FilterRegistration.Dynamic addFilter(
			String name, Class<? extends Filter> aClass, String... urlPatterns)
					throws ServletException {
		Filter filter = ctx.createFilter(aClass);
		INJECTOR.injectMembers(filter);
		FilterRegistration.Dynamic reg = ctx.addFilter(name, filter);
		reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, urlPatterns);
		reg.setAsyncSupported(true);
		log.info("registered filter " + name);
		return reg;
	}



	@Override
	public void contextInitialized(ServletContextEvent initializationEvent) {
		servletModule = new ServletModule();
		LinkedList<Module> modules = configureInjections();
		modules.add(servletModule);
		INJECTOR = Guice.createInjector(modules);
		log.info("Guice INJECTOR created successfully");

		ctx = initializationEvent.getServletContext();
		try {
			Filter requestContextFilter = ctx.createFilter(RequestContextFilter.class);
			INJECTOR.injectMembers(requestContextFilter);
			FilterRegistration.Dynamic reg =
					ctx.addFilter("requestContextFilter", requestContextFilter);
			reg.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");
			reg.setAsyncSupported(true);

			configureServletsAndFilters();
		} catch (ServletException e) {
			throw new RuntimeException(e);
		}
	}



	/**
	 * Helper method for use in {@link #contextDestroyed(ServletContextEvent)}.
	 */
	public static void gracefullyShutdownExecutor(
			ContextTrackingExecutor executor, long timeoutSeconds) {
		executor.shutdown();
		try {
			executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {}
		if (!executor.isTerminated()) {
			List<Runnable> remianingTasks = executor.shutdownNow();
			log.warning(remianingTasks.size() + " tasks still remaining in " + executor.getName());
		} else {
			log.info(executor.getName() + " shutdown completed");
		}
	}



	static final Logger log = Logger.getLogger(GuiceServletContextListener.class.getName());
}
