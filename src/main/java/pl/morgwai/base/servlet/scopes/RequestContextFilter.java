/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.google.inject.Inject;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Starts tracking context of newly incoming <code>HttpServletRequest</code>s.
 */
public class RequestContextFilter implements Filter {



	@Inject
	ContextTracker<RequestContext> tracker;



	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		RequestContext ctx = new ServletRequestContext((HttpServletRequest) request, tracker);
		try {
			ctx.runWithinSelf(() -> {
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
			Throwable cause = e.getCause();
			if (cause instanceof IOException) throw (IOException) cause;
			if (cause instanceof ServletException) throw (ServletException) cause;
			throw e;
		}
	}
}
