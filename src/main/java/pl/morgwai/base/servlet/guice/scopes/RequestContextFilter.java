// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;

import com.google.inject.Inject;
import com.google.inject.Injector;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates {@link ServletRequestContext}s for newly incoming {@link HttpServletRequest}s and for
 * {@code Requests} {@link javax.servlet.AsyncContext#dispatch(String) dispatched asynchronously},
 * transfers existing {@code Contexts} to their new handling {@code Thread}s.
 * <p>
 * This {@code Filter} should usually be installed at the beginning of the
 * chain for all URL patterns for new and async {@code Requests}:
 * {@link FilterRegistration#addMappingForUrlPatterns(java.util.EnumSet, boolean, String...)
 * addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC), false, "/*")}.
 * </p>
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
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final ServletRequestContext ctx;
		switch (request.getDispatcherType()) {
			case REQUEST:
				ctx = new ServletRequestContext((HttpServletRequest) request, ctxTracker);
				request.setAttribute(ServletRequestContext.class.getName(), ctx);
				break;
			case ASYNC:
				ctx = (ServletRequestContext)
						request.getAttribute(ServletRequestContext.class.getName());
				if (ctx == null) {
					throw new ServletException(NO_CTX_FOR_ASYNC_REQUEST_MESSAGE
							+ request.getAttribute(AsyncContext.ASYNC_REQUEST_URI));
				}
				break;
			default:
				throw new ServletException(ILLEGAL_DISPATCHER_TYPE_MESSAGE);
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

	static final String NO_CTX_FOR_ASYNC_REQUEST_MESSAGE = "Could not find a ServletRequestContext "
			+ "for an asynchronously dispatched ServletRequest, probably it has not passed via "
			+ "RequestContextFilter before being dispatched: make sure RequestContextFilter's "
			+ "mappings cover its path: ";
	static final String ILLEGAL_DISPATCHER_TYPE_MESSAGE =
			"RequestContextFilter mapped for illegal DispatcherType";
}
