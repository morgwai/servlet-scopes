// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.*;

import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of a {@link HttpSession}.
 */
public class HttpSessionContext extends InjectionContext {



	final HttpSession session;
	public HttpSession getSession() { return session; }



	HttpSessionContext(HttpSession session) {
		this.session = session;
	}



	/** Registered in {@link GuiceServletContextListener}. */
	static class SessionContextCreator implements HttpSessionListener {

		@Override
		public void sessionCreated(HttpSessionEvent event) {
			final var session = event.getSession();
			session.setAttribute(
					HttpSessionContext.class.getName(), new HttpSessionContext(session));
		}
	}



	private static final long serialVersionUID = 7039954797806124124L;
}
