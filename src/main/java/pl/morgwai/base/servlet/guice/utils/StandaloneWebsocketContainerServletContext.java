// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

import javax.servlet.*;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.descriptor.JspConfigDescriptor;



/**
 * A fake {@link ServletContext} useful for configuring
 * {@link pl.morgwai.base.servlet.guice.scopes.ServletModule} and
 * {@link pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator} in standalone
 * websocket container apps.
 * Most methods throw an {@link UnsupportedOperationException} except the below ones:
 * <ul>
 *     <li>{@link #getContextPath()}</li>
 *     <li>{@link #getContext(String)}</li>
 *     <li>{@link #getServletContextName()}</li>
 *     <li>{@link #getVirtualServerName()}</li>
 *     <li>{@link #getAttribute(String)}</li>
 *     <li>{@link #getAttributeNames()}</li>
 *     <li>{@link #setAttribute(String, Object)}</li>
 *     <li>{@link #removeAttribute(String)}</li>
 *     <li>{@link #getInitParameter(String)}</li>
 *     <li>{@link #getInitParameterNames()}</li>
 *     <li>{@link #setInitParameter(String, String)}</li>
 * </ul>
 */
public class StandaloneWebsocketContainerServletContext implements ServletContext {



	final String contextPath;
	final String servletContextName;
	final String virtualServerName;
	final Map<String, Object> attributes = new HashMap<>(5);
	final Map<String, String> initParams = new HashMap<>(5);



	/**
	 * Calls {@link #StandaloneWebsocketContainerServletContext(String, String, String)
	 * this(contextPath, "app at " + contextPath, null)}.
	 */
	public StandaloneWebsocketContainerServletContext(String contextPath) {
		this(contextPath, "app at " + contextPath, null);
	}



	/**
	 * Calls {@link #StandaloneWebsocketContainerServletContext(String, String, String)
	 * this(contextPath, servletContextName, null)}.
	 */
	public StandaloneWebsocketContainerServletContext(String contextPath, String servletContextName)
	{
		this(contextPath, servletContextName, null);
	}



	/**
	 * Initializes values to be returned by the corresponding methods.
	 * @param contextPath value returned by {@link #getContextPath()}.
	 * @param servletContextName value returned by {@link #getServletContextName()}.
	 * @param virtualServerName value returned by {@link #getVirtualServerName()}.
	 */
	public StandaloneWebsocketContainerServletContext(
		String contextPath,
		String servletContextName,
		String virtualServerName
	) {
		this.contextPath = contextPath;
		this.servletContextName = servletContextName;
		this.virtualServerName = virtualServerName;
	}



	@Override
	public String getContextPath() {
		return contextPath;
	}



	/** Returns itself if {@code path} equals {@link #getContextPath()}, {@code null} otherwise. */
	@Override
	public ServletContext getContext(String path) {
		return (path.equals(contextPath)) ? this : null;
	}



	@Override
	public String getServletContextName() {
		return servletContextName;
	}



	@Override
	public String getVirtualServerName() {
		return virtualServerName;
	}



	@Override
	public Object getAttribute(String name) {
		return attributes.get(name);
	}



	@Override
	public Enumeration<String> getAttributeNames() {
		return Collections.enumeration(attributes.keySet());
	}



	@Override
	public void setAttribute(String name, Object object) {
		attributes.put(name, object);
	}



	@Override
	public void removeAttribute(String name) {
		attributes.remove(name);
	}



	@Override
	public String getInitParameter(String name) {
		return initParams.get(name);
	}



	@Override
	public Enumeration<String> getInitParameterNames() {
		return Collections.enumeration(initParams.keySet());
	}



	@Override public boolean setInitParameter(String name, String value) {
		if (initParams.containsKey(name)) return false;
		initParams.put(name, value);
		return true;
	}



	// all the other below methods throw UnsupportedOperationException

	@Override public int getMajorVersion() { throw new UnsupportedOperationException(); }
	@Override public int getMinorVersion() { throw new UnsupportedOperationException(); }
	@Override public int getEffectiveMajorVersion() { throw new UnsupportedOperationException(); }
	@Override public int getEffectiveMinorVersion() { throw new UnsupportedOperationException(); }
	@Override public String getMimeType(String file) { throw new UnsupportedOperationException(); }
	@Override
	public Set<String> getResourcePaths(String path) { throw new UnsupportedOperationException(); }
	@Override public URL getResource(String path) { throw new UnsupportedOperationException(); }
	@Override public InputStream getResourceAsStream(String path) {
		throw new UnsupportedOperationException();
	}
	@Override public RequestDispatcher getRequestDispatcher(String path) {
		throw new UnsupportedOperationException();
	}
	@Override public RequestDispatcher getNamedDispatcher(String name) {
		throw new UnsupportedOperationException();
	}
	@Override public Servlet getServlet(String name) { throw new UnsupportedOperationException(); }
	@Override
	public Enumeration<Servlet> getServlets() { throw new UnsupportedOperationException(); }
	@Override
	public Enumeration<String> getServletNames() { throw new UnsupportedOperationException(); }
	@Override public void log(String msg) { throw new UnsupportedOperationException(); }
	@Override
	public void log(Exception exception, String msg) { throw new UnsupportedOperationException(); }
	@Override public void log(String message, Throwable throwable) {
		throw new UnsupportedOperationException();
	}
	@Override public String getRealPath(String path) { throw new UnsupportedOperationException(); }
	@Override public String getServerInfo() { throw new UnsupportedOperationException(); }
	@Override public Dynamic addServlet(String servletName, String className) {
		throw new UnsupportedOperationException();
	}
	@Override public Dynamic addServlet(String servletName, Servlet servlet) {
		throw new UnsupportedOperationException();
	}
	@Override public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
		throw new UnsupportedOperationException();
	}
	@Override public Dynamic addJspFile(String servletName, String jspFile) {
		throw new UnsupportedOperationException();
	}
	@Override public <T extends Servlet> T createServlet(Class<T> clazz) {
		throw new UnsupportedOperationException();
	}
	@Override public ServletRegistration getServletRegistration(String servletName) {
		throw new UnsupportedOperationException();
	}
	@Override public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		throw new UnsupportedOperationException();
	}
	@Override public FilterRegistration.Dynamic addFilter(String filterName, String className) {
		throw new UnsupportedOperationException();
	}
	@Override public FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		throw new UnsupportedOperationException();
	}
	@Override public FilterRegistration.Dynamic addFilter(
			String filterName, Class<? extends Filter> filterClass) {
		throw new UnsupportedOperationException();
	}
	@Override public <T extends Filter> T createFilter(Class<T> clazz) {
		throw new UnsupportedOperationException();
	}
	@Override public FilterRegistration getFilterRegistration(String filterName) {
		throw new UnsupportedOperationException();
	}
	@Override public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		throw new UnsupportedOperationException();
	}
	@Override public SessionCookieConfig getSessionCookieConfig() {
		throw new UnsupportedOperationException();
	}
	@Override public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
		throw new UnsupportedOperationException();
	}
	@Override public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		throw new UnsupportedOperationException();
	}
	@Override public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		throw new UnsupportedOperationException();
	}
	@Override
	public void addListener(String className) { throw new UnsupportedOperationException(); }
	@Override public <T extends EventListener> void addListener(T t) {
		throw new UnsupportedOperationException();
	}
	@Override public void addListener(Class<? extends EventListener> listenerClass) {
		throw new UnsupportedOperationException();
	}
	@Override public <T extends EventListener> T createListener(Class<T> clazz) {
		throw new UnsupportedOperationException();
	}
	@Override public JspConfigDescriptor getJspConfigDescriptor() {
		throw new UnsupportedOperationException();
	}
	@Override public ClassLoader getClassLoader() { throw new UnsupportedOperationException(); }
	@Override
	public void declareRoles(String... roleNames) { throw new UnsupportedOperationException(); }
	@Override public int getSessionTimeout() { throw new UnsupportedOperationException(); }
	@Override
	public void setSessionTimeout(int sessionTimeout) { throw new UnsupportedOperationException(); }
	@Override
	public String getRequestCharacterEncoding() { throw new UnsupportedOperationException(); }
	@Override public void setRequestCharacterEncoding(String encoding) {
		throw new UnsupportedOperationException();
	}
	@Override
	public String getResponseCharacterEncoding() { throw new UnsupportedOperationException(); }
	@Override public void setResponseCharacterEncoding(String encoding) {
		throw new UnsupportedOperationException();
	}
}
