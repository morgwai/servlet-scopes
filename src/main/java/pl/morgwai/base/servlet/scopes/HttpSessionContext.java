// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of a {@link HttpSession}.
 */
public class HttpSessionContext extends InjectionContext {



	final HttpSession session;
	public HttpSession getSession() { return session; }



	public HttpSessionContext(HttpSession session) {
		this.session = session;
	}
}
