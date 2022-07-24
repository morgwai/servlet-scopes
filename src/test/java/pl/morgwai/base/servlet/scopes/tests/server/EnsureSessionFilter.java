// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import java.io.IOException;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;



/**
 * Makes sure that each passing request has an <code>HttpSession</code> created. This is necessary
 * if {@link pl.morgwai.base.servlet.scopes.ServletModule#httpSessionScope} is used in a websocket
 * endpoint, as there's no way to create a session from the endpoint layer later on.
 */
public class EnsureSessionFilter implements Filter {



	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		((HttpServletRequest) request).getSession();
		chain.doFilter(request, response);
	}
}
