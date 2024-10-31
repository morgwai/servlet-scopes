// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.InputStream;
import java.net.URL;
import java.util.*;
import javax.servlet.*;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.descriptor.JspConfigDescriptor;



public class FakeAppDeployment implements ServletContext {



	@Override public String getContextPath() { return contextPath; }
	final String contextPath;

	@Override public String getServletContextName() { return servletContextName; }
	final String servletContextName;



	public FakeAppDeployment(String contextPath, String servletContextName) {
		this.contextPath = contextPath;
		this.servletContextName = servletContextName;
	}

	public FakeAppDeployment(String contextPath) {
		this(contextPath, contextPath.isEmpty() ? "rootApp" : "app at " + contextPath);
	}



	/** Returns itself if {@code path} equals {@link #getContextPath()}, {@code null} otherwise. */
	@Override
	public ServletContext getContext(String path) {
		return (path.equals(contextPath)) ? this : null;
	}



	final Map<String, Object> attributes = new HashMap<>(5);

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



	final Map<String, String> initParams = new HashMap<>(5);

	@Override
	public String getInitParameter(String name) {
		return initParams.get(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		return Collections.enumeration(initParams.keySet());
	}

	@Override
	public boolean setInitParameter(String name, String value) {
		if (initParams.containsKey(name)) return false;
		initParams.put(name, value);
		return true;
	}



	// all the other below methods throw an UnsupportedOperationException

	@Override public String getVirtualServerName() { throw new UnsupportedOperationException(); }
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
