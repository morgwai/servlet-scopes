/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * blah
 */
public class WebsocketEventContext extends RequestContext {



	HttpSession httpSession;
	@Override public HttpSession getHttpSession() { return httpSession; }



	protected WebsocketEventContext(
			HttpSession httpSession, ContextTracker<RequestContext> tracker) {
		super(tracker);
		this.httpSession = httpSession;
	}
}
