// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of a {@link HttpServletRequest}.
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
			HttpServletRequest request, ContextTracker<ContainerCallContext> tracker) {
		super(tracker);
		this.request = request;
	}
}
