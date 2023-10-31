// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of an {@link HttpServletRequest}. Each {@link HttpServletRequest} processing {@link
 * pl.morgwai.base.guice.scopes.TrackableContext#executeWithinSelf(java.util.concurrent.Callable)
 * runs within} a separate instance of {@code ServletRequestContext}. Specifically
 * {@link javax.servlet.Filter}s {@link
 * javax.servlet.FilterRegistration#addMappingForServletNames(java.util.EnumSet, boolean, String...)
 * registered} at the end of the {@link javax.servlet.FilterChain} (&nbsp;{@code true} passed as
 * {@code isMatchAfter} param), the {@link javax.servlet.Servlet}'s {@link
 * javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
 * service(request, response) method} and the {@link javax.servlet.Servlet}'s appropriate
 * {@code doXXX(request, response)} method.
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
		request.setAttribute(ServletRequestContext.class.getName(), this);
	}
}
