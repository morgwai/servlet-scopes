// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

import com.google.inject.Inject;
import com.google.inject.Injector;
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
		ServletRequest request,
		ServletResponse response,
		FilterChain chain
	) throws IOException, ServletException {
		final var ctx = getContext((HttpServletRequest) request);
		if (ctx == null) {  // already running within the Context of a given request
			chain.doFilter(request, response);
			return;
		}

		try {
			ctx.executeWithinSelf(() -> {
				chain.doFilter(request, response);
				return null;  // Void
			});
		} catch (IOException | ServletException | RuntimeException e) {
			throw e;
		} catch (Exception neverHappens) {
			// result of wrapping with a Callable
		}
	}

	/**
	 * Retrieves or creates a {@link ServletRequestContext} that should be entered to handle
	 * {@code request}.
	 * If {@link Thread#currentThread() the current Thread} is already running within the
	 * {@code Context} of {@code request}, {@code null} is returned.
	 */
	ServletRequestContext getContext(HttpServletRequest request) throws ServletException {
		final ServletRequestContext ctx;
		switch (request.getDispatcherType()) {
			case INCLUDE:
			case FORWARD:
				if (ctxTracker.getCurrentContext() != null) return null; // standard FORWARD/INCLUDE
				// no break: FORWARD / INCLUDE from another deployment: same processing as REQUEST
			case REQUEST:
				ctx = new ServletRequestContext(request, ctxTracker);
				request.setAttribute(CTX_ATTRIBUTE, ctx);
				return ctx;
			default:  // ASYNC or ERROR
				ctx = (ServletRequestContext) request.getAttribute(CTX_ATTRIBUTE);
				if (ctx == null) throw new ServletException(formatCtxNotFoundMessage(request));
				return ctx;
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
