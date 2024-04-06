// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.http.*;

import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of an {@link HttpSession}.
 * <p>
 * <b>NOTE:</b> If the servlet container being used, uses mechanism other than the standard
 * {@link java.io.Serializable Java Serialization} to persist/replicate {@link HttpSession}s, then
 * a {@link ServletContext#setInitParameter(String, String) deployment init-param} named as the
 * value of {@link #CUSTOM_SERIALIZATION_PARAM}, must be set to {@code "true"} either in
 * {@code web.xml} or programmatically before any request is served (for example in
 * {@link jakarta.servlet.ServletContextListener#contextInitialized(ServletContextEvent)}).</p>
 * @see ServletModule#httpSessionScope corresponding Scope
 */
public class HttpSessionContext extends InjectionContext implements HttpSessionActivationListener {



	public HttpSession getSession() { return session; }
	private transient HttpSession session;

	/** See {@link #CUSTOM_SERIALIZATION_PARAM}. */
	public static final String CUSTOM_SERIALIZATION_PARAM_SUFFIX = ".customSerialization";
	/**
	 * Name of the {@link ServletContext#setInitParameter(String, String) deployment init-param}
	 * indicating that the servlet container uses serialization mechanism other than the
	 * {@link java.io.Serializable standard Java Serialization} to persist/replicate
	 * {@link HttpSession}s.
	 * The value is a concatenation of
	 * {@link Class#getName() the fully qualified name of this class} and
	 * {@value #CUSTOM_SERIALIZATION_PARAM_SUFFIX}.
	 */
	public static final String CUSTOM_SERIALIZATION_PARAM =
			HttpSessionContext.class.getName() + CUSTOM_SERIALIZATION_PARAM_SUFFIX;
	final boolean customSerialization;



	HttpSessionContext(HttpSession session) {
		this.session = session;
		this.customSerialization = Boolean.parseBoolean(
				session.getServletContext().getInitParameter(CUSTOM_SERIALIZATION_PARAM));
	}



	/**
	 * Creates {@link HttpSessionContext}s for newly created {@link HttpSession}s.
	 * Registered in {@link GuiceServletContextListener}.
	 */
	public static class SessionContextCreator implements HttpSessionListener {

		@Override
		public void sessionCreated(HttpSessionEvent creation) {
			final var session = creation.getSession();
			session.setAttribute(
				HttpSessionContext.class.getName(),
				new HttpSessionContext(session)
			);
		}
	}



	/** Returns the {@code Context} of {@code session}. */
	public static HttpSessionContext of(HttpSession session) {
		return (HttpSessionContext) session.getAttribute(HttpSessionContext.class.getName());
	}



	/**
	 * Calls {@link #prepareForSerialization()} if
	 * {@link jakarta.servlet.ServletContext#getInitParameter(String) deployment init-param} named as
	 * the value of {@link #CUSTOM_SERIALIZATION_PARAM} is {@code "true"}.
	 */
	@Override
	public void sessionWillPassivate(HttpSessionEvent serialization) {
		if (customSerialization) prepareForSerialization();
	}



	/**
	 * Calls {@link #restoreAfterDeserialization()} if
	 * {@link jakarta.servlet.ServletContext#getInitParameter(String) deployment init-param} named as
	 * the value of {@link #CUSTOM_SERIALIZATION_PARAM} is {@code "true"}.
	 */
	@Override
	public void sessionDidActivate(HttpSessionEvent deserialization) {
		if (session != null) return;
		session = deserialization.getSession();
		if (customSerialization) {
			try {
				restoreAfterDeserialization();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
	}



	private static final long serialVersionUID = -3482947070671038422L;
}
