// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

import com.google.inject.Inject;
import com.google.inject.Injector;
import pl.morgwai.base.function.ThrowingTask;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates new {@link ServletRequestContext}s for newly incoming {@link HttpServletRequest}s,
 * transfers existing {@link ServletRequestContext Context}s to their new handling {@code Thread}s
 * for dispatched {@code Request}s ({@link javax.servlet.AsyncContext#dispatch() asynchronously},
 * {@link RequestDispatcher forwarded, included} or {@link DispatcherType#ERROR to error handlers}).
 * <p>
 * This {@code Filter} should usually be installed at the beginning of the
 * chain for all URL patterns and for {@link java.util.EnumSet#allOf(Class) all DispatcherTypes}:
 * {@link FilterRegistration#addMappingForUrlPatterns(java.util.EnumSet, boolean, String...)
 * addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*")}.</p>
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
		ServletRequest rawRequest,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		final var request = (HttpServletRequest) rawRequest;
		final ServletRequestContext ctxToActivate;
		Object savedCtx = null;  // for requests INCLUDE/FORWARD-ed from other deployments
		switch (request.getDispatcherType()) {
			case INCLUDE:
			case FORWARD:
				if (ctxTracker.getCurrentContext() != null) {
					// normal INCLUDE/FORWARD from the same deployment handled from within the
					// original ctx, so it's still active
					ctxToActivate = null;
					break;
				}

				// INCLUDE/FORWARD from another deployment: save the previous CTX_ATTRIBUTE (if any)
				// and continue to create a new one for this deployment
				savedCtx = request.getAttribute(CTX_ATTRIBUTE);
			case REQUEST: // create, store in CTX_ATTRIBUTE, activate
				ctxToActivate = new ServletRequestContext(request, ctxTracker);
				request.setAttribute(CTX_ATTRIBUTE, ctxToActivate);
				break;
			default:  // ASYNC/ERROR: reactivate from CTX_ATTRIBUTE
				ctxToActivate = (ServletRequestContext) request.getAttribute(CTX_ATTRIBUTE);
				if (ctxToActivate == null) {  // misconfigured app
					throw new ServletException(formatCtxNotFoundMessage(request));
				}
		}
		if (ctxToActivate == null) {  // already running within the Context of the request
			chain.doFilter(request, response);
		} else {  // (re)-activate the Context of the request
			try {
				ctxToActivate.executeWithinSelf((ThrowingTask<IOException, ServletException>)
						() -> chain.doFilter(request, response));
			} finally {
				if (savedCtx != null) request.setAttribute(CTX_ATTRIBUTE, savedCtx);
			}
		}
	}

	static final String CTX_ATTRIBUTE = ServletRequestContext.class.getName();

	String formatCtxNotFoundMessage(HttpServletRequest request) {
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
