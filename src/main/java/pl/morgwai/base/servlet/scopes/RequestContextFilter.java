// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import com.google.inject.Inject;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Starts tracking contexts of newly incoming {@link HttpServletRequest}s and transfers context when
 * {@link jakarta.servlet.AsyncContext#dispatch(String) dispatching from AsyncContext}.
 */
public class RequestContextFilter implements Filter {



	@Inject ContextTracker<ContainerCallContext> tracker;



	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		final var ctx = request.getDispatcherType() == DispatcherType.REQUEST
				? new ServletRequestContext((HttpServletRequest) request, tracker)
				: (ServletRequestContext)
						request.getAttribute(ServletRequestContext.class.getName());
		try {
			ctx.executeWithinSelf(() -> {
				try {
					chain.doFilter(request, response);
				} catch (IOException | ServletException e) {
					throw new RuntimeException(e);
				}
			});
		} catch (RuntimeException e) {
			// if a ServletException or an IOException was thrown from doFilter(...) then unwrap it.
			// otherwise just re-throw.
			if (e.getCause() == null) throw e;
			final var cause = e.getCause();
			if (cause instanceof IOException) throw (IOException) cause;
			if (cause instanceof ServletException) throw (ServletException) cause;
			throw e;
		}
	}
}
