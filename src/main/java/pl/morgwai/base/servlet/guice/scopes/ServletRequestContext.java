// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of an {@link HttpServletRequest}.
 * Each {@link HttpServletRequest} processing
 * {@link ServletRequestContext#executeWithinSelf(java.util.concurrent.Callable) runs within} a
 * <b>separate</b> {@code ServletRequestContext} instance. Specifically
 * {@link javax.servlet.Filter}s {@link
 * javax.servlet.FilterRegistration#addMappingForServletNames(java.util.EnumSet, boolean, String...)
 * registered} after the {@link RequestContextFilter} (&nbsp;{@code true} passed as
 * {@code isMatchAfter} param), {@link
 * javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
 * Servlet.service(...) methods} and as a consequence all the
 * {@link javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest,
 * javax.servlet.http.HttpServletResponse) Servlet.doXXX(...) methods}.
 * <p>
 * Note: this context is transferred automatically to the new thread when
 * {@link javax.servlet.AsyncContext#dispatch(String) dispatching from AsyncContext}.</p>
 * @see ContainerCallContext super class for more info
 */
public class ServletRequestContext extends ContainerCallContext {



	final HttpServletRequest request;
	public HttpServletRequest getRequest() { return request; }



	@Override
	public HttpSession getHttpSession() {
		return request.getSession();
	}



	ServletRequestContext(HttpServletRequest request, ContextTracker<ContainerCallContext> tracker)
	{
		super(tracker);
		this.request = request;
	}
}
