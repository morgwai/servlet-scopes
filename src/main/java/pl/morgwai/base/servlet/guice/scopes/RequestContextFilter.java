// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

import com.google.inject.Inject;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates {@link ServletRequestContext}s for newly incoming {@link HttpServletRequest}s and
 * transfers {@code Contexts} for {@code Requests}
 * {@link javax.servlet.AsyncContext#dispatch(String) dispatched from AsyncContext} to
 * new {@code Threads}.
 * <p>
 * If an instance of this {@code Filter} is not created by Guice, then a reference to the
 * {@link ContextTracker} must be set either
 * {@link #setContainerCallContextTracker(ContextTracker) manually} or by requesting
 * {@link com.google.inject.Injector#injectMembers(Object) Guice member injection}.</p>
 * <p>
 * This {@code Filter} should usually be installed at the beginning of the
 * chain for all URL patterns for new and async {@code Requests}:
 * {@link FilterRegistration#addMappingForUrlPatterns(java.util.EnumSet, boolean, String...)
 * addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*")}.
 * </p>
 */
public class RequestContextFilter implements Filter {



	ContextTracker<ContainerCallContext> containerCallContextTracker;



	@Inject
	public void setContainerCallContextTracker(
		ContextTracker<ContainerCallContext> containerCallContextTracker
	) {
		this.containerCallContextTracker = containerCallContextTracker;
	}



	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final ServletRequestContext ctx;
		if (request.getDispatcherType() == DispatcherType.REQUEST) {  // new request
			ctx = new ServletRequestContext(
					(HttpServletRequest) request, containerCallContextTracker);
			request.setAttribute(ServletRequestContext.class.getName(), ctx);
		} else {  // async request dispatched from another thread
			ctx = (ServletRequestContext)
					request.getAttribute(ServletRequestContext.class.getName());
		}
		try {
			ctx.executeWithinSelf(
				() -> {
					chain.doFilter(request, response);
					return null;  // Void
				}
			);
		} catch (IOException | ServletException | RuntimeException e) {
			throw e;
		} catch (Exception neverHappens) {
			// result of wrapping with a Callable
		}
	}
}
