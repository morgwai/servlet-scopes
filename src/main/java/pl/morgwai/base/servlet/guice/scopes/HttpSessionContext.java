// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.*;

import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of an {@link HttpSession}.
 * @see ServletModule#httpSessionScope corresponding Scope
 */
public class HttpSessionContext extends InjectionContext implements HttpSessionActivationListener {



	final HttpSession session;
	public HttpSession getSession() { return session; }



	HttpSessionContext(HttpSession session) {
		this.session = session;
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



	/** Calls {@link #prepareForSerialization()}. */
	@Override
	public void sessionWillPassivate(HttpSessionEvent serialization) {
		prepareForSerialization();
	}



	/** Calls {@link #restoreAfterDeserialization()} . */
	@Override
	public void sessionDidActivate(HttpSessionEvent deserialization) {
		try {
			restoreAfterDeserialization();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}



	private static final long serialVersionUID = 3922213799812986253L;
}
