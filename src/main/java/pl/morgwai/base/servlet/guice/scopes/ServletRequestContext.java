// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of an {@link HttpServletRequest}.
 * Each {@link HttpServletRequest} processing
 * {@link ServletRequestContext#executeWithinSelf(java.util.concurrent.Callable) runs within} a
 * <b>separate</b> {@code ServletRequestContext} instance. Specifically
 * {@link jakarta.servlet.Filter}s {@link
 * jakarta.servlet.FilterRegistration#addMappingForServletNames(java.util.EnumSet, boolean, String...)
 * registered} after the {@link RequestContextFilter} (&nbsp;{@code true} passed as
 * {@code isMatchAfter} param), {@link
 * jakarta.servlet.Servlet#service(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse)
 * Servlet.service(...) methods} and as a consequence all the
 * {@link jakarta.servlet.http.HttpServlet#doGet(jakarta.servlet.http.HttpServletRequest,
 * jakarta.servlet.http.HttpServletResponse) Servlet.doXXX(...) methods}.
 * <p>
 * Note: this context is transferred automatically to the new thread when
 * {@link jakarta.servlet.AsyncContext#dispatch(String) dispatching from AsyncContext}.</p>
 * @see ContainerCallContext super class for more info
 */
public class ServletRequestContext extends ContainerCallContext {



	public HttpServletRequest getRequest() { return request; }
	public final HttpServletRequest request;



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
