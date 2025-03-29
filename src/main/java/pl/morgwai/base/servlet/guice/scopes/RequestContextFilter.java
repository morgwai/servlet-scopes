// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import com.google.inject.Inject;
import com.google.inject.Injector;
import pl.morgwai.base.function.ThrowingTask;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates new {@link ServletRequestContext}s for {@link DispatcherType#REQUEST newly incoming}
 * {@link HttpServletRequest}s, transfers existing {@link ServletRequestContext Context}s to their
 * new handling {@code Thread}s for dispatched {@code Request}s
 * ({@link AsyncContext#dispatch() asynchronously},
 * {@link RequestDispatcher#forward(ServletRequest, ServletResponse) forwarded},
 * {@link RequestDispatcher#include(ServletRequest, ServletResponse) included} or
 * {@link DispatcherType#ERROR to error handlers}).
 * <p>
 * This {@code Filter} should usually be installed at the beginning of the
 * chain for all URL patterns and for {@link java.util.EnumSet#allOf(Class) all DispatcherTypes}:
 * {@link FilterRegistration#addMappingForUrlPatterns(java.util.EnumSet, boolean, String...)
 * addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*")}.</p>
 * <p>
 * Note: if an {@link HttpServletRequest} is
 * "{@link ServletContext#getContext(String) cross-deployment}
 * {@link ServletContext#getRequestDispatcher(String) dispatched}" more than once to some
 * given target {@link ServletContext deployment}, it will have a separate
 * {@link ServletRequestContext} each time in that target deployment.</p>
 */
public class RequestContextFilter implements Filter {



	ContextTracker<ContainerCallContext> ctxTracker;



	@Inject
	public void setCtxTracker(ContextTracker<ContainerCallContext> ctxTracker) {
		this.ctxTracker = ctxTracker;
	}

	@Override
	public void init(FilterConfig config) {
		if (ctxTracker != null) return;
		((Injector) config.getServletContext().getAttribute(Injector.class.getName()))
				.injectMembers(this);
	}



	@Override
	public void doFilter(
		ServletRequest servletRequest,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		final var request = (HttpServletRequest) servletRequest;
		final ServletRequestContext ctxToActivate;  // null => the Ctx of request is already active
		switch (request.getDispatcherType()) {
			case INCLUDE:
			case FORWARD:
				if (ctxTracker.getCurrentContext() != null) {  // dispatch from the same deployment
					// dispatching is performed from within the Ctx, so it's still active
					ctxToActivate = null;
					break;
				}

				// dispatch from another deployment: continue to create a Ctx for this deployment
			case REQUEST:  // create a new Ctx, store it in the attribute, activate it
				ctxToActivate = new ServletRequestContext(request, ctxTracker);
				getStoredCtxsMap(request).put(ctxTracker, ctxToActivate);
				break;
			default:  // ASYNC/ERROR: reactivate the Ctx stored in the attribute
				ctxToActivate = getStoredCtxsMap(request).get(ctxTracker);
				if (ctxToActivate == null) {  // misconfigured app
					throw new ServletException(formatCtxNotFoundMessage(request));
				}
		}
		if (ctxToActivate == null) {  // already running within the Ctx of this request
			chain.doFilter(request, response);
		} else {  // (re)-activate the Ctx of this request
			ctxToActivate.executeWithinSelf((ThrowingTask<IOException, ServletException>)
					() -> chain.doFilter(request, response));
		}
	}

	/**
	 * Obtains from {@code request}'s {@link HttpServletRequest#getAttribute(String) attribute} a
	 * {@code Map} of {@link ServletRequestContext}s stored by each deployment that processed
	 * {@code request}.
	 * If the attribute is empty, initializes it with a new {@code Map}. {@code Context}s are
	 * indexed using their respective {@link #ctxTracker}s.
	 */
	static Map<
		ContextTracker<ContainerCallContext>,
		ServletRequestContext
	> getStoredCtxsMap(HttpServletRequest request) {
		@SuppressWarnings("unchecked")
		var storedCtxs = (Map<ContextTracker<ContainerCallContext>, ServletRequestContext>)
				request.getAttribute(ServletRequestContext.class.getName());
		if (storedCtxs == null) {
			storedCtxs = new HashMap<>(3);
			request.setAttribute(ServletRequestContext.class.getName(), storedCtxs);
		}
		return storedCtxs;
	}

	static String formatCtxNotFoundMessage(HttpServletRequest request) {
		final var dispatcherType = request.getDispatcherType();
		return String.format(
			CTX_NOT_FOUND_MESSAGE,
			dispatcherType,
			dispatcherType == DispatcherType.ASYNC
				? request.getAttribute(AsyncContext.ASYNC_REQUEST_URI)
				: request.getRequestURI()
		);
	}

	static final String CTX_NOT_FOUND_MESSAGE = "could not find a ServletRequestContext for %s "
			+ "dispatched ServletRequest: probably it has not passed via a RequestContextFilter "
			+ "before being dispatched: make sure RequestContextFilter has async support enabled, "
			+ "its mappings have ALL DispatcherTypes enabled and cover the request path: \"%s\"";
}
