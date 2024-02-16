// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of an {@link HttpServletRequest}. Each {@link HttpServletRequest} processing {@link
 * pl.morgwai.base.guice.scopes.TrackableContext#executeWithinSelf(java.util.concurrent.Callable)
 * runs within} a separate instance of {@code ServletRequestContext}. Specifically
 * {@link jakarta.servlet.Filter}s {@link
 * jakarta.servlet.FilterRegistration#addMappingForServletNames(java.util.EnumSet, boolean, String...)
 * registered} at the end of the {@link jakarta.servlet.FilterChain} (&nbsp;{@code true} passed as
 * {@code isMatchAfter} param), the {@link jakarta.servlet.Servlet}'s {@link
 * jakarta.servlet.Servlet#service(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse)
 * service(request, response) method} and the {@link jakarta.servlet.Servlet}'s appropriate
 * {@code doXXX(request, response)} method.
 * <p>
 * Note: this context is transferred automatically to the new thread when
 * {@link jakarta.servlet.AsyncContext#dispatch(String) dispatching from AsyncContext}.</p>
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
