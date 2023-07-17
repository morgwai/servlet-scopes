// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of a {@link HttpServletRequest}.
 * <p>
 * Note: this context is transferred automatically to the new thread when
 * {@link javax.servlet.AsyncContext#dispatch(String) dispatching from AsyncContext}.</p>
 *
 * @see ContainerCallContext super class for more info
 */
public class ServletRequestContext extends ContainerCallContext {



	final HttpServletRequest request;
	public HttpServletRequest getRequest() { return request; }



	@Override
	public HttpSession getHttpSession() {
		return request.getSession();
	}



	ServletRequestContext(
		HttpServletRequest request,
		ContextTracker<ContainerCallContext> tracker
	) {
		super(tracker);
		this.request = request;
		request.setAttribute(ServletRequestContext.class.getName(), this);
	}
}
