// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import com.google.inject.Inject;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Creates context for each newly incoming {@link HttpServletRequest} and transfers context when
 * requests are {@link jakarta.servlet.AsyncContext#dispatch(String) dispatched from AsyncContext} to
 * new threads.
 */
public class RequestContextFilter implements Filter {



	@Inject ContextTracker<ContainerCallContext> containerCallContextTracker;



	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final var ctx = request.getDispatcherType() == DispatcherType.REQUEST
				? new ServletRequestContext(
						(HttpServletRequest) request, containerCallContextTracker)
				: (ServletRequestContext)
						request.getAttribute(ServletRequestContext.class.getName());
		try {
			ctx.executeWithinSelf(() -> {
				chain.doFilter(request, response);
				return null;
			});
		} catch (IOException | ServletException | RuntimeException e) {
			throw e;
		} catch (Exception neverHappens) {
			// result of wrapping with a Callable
		}
	}
}
