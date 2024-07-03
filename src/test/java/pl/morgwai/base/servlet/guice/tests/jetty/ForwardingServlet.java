// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



/**
 * All test requests initially arrive to this {@code Servlet} and are
 * {@link javax.servlet.RequestDispatcher#forward(ServletRequest, ServletResponse) forwarded} to
 * {@link AsyncServlet}. If a request comes back to this {@code Servlet}
 * {@link DispatcherType#ASYNC asynchronously}, it is
 * {@link #doAsyncHandling(HttpServletRequest, HttpServletResponse) handled}.
 */
public class ForwardingServlet extends TestServlet {



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			doAsyncHandling(request, response);
			return;
		}

		// store scoped objects for verifyScoping(...)
		request.setAttribute(Service.CONTAINER_CALL, requestScopedProvider.get());
		request.setAttribute(Service.HTTP_SESSION, sessionScopedProvider.get());
		verifyScoping(request, "the original container thread");
		request.getRequestDispatcher("/" + AsyncServlet.class.getSimpleName())
				.forward(request, response);
	}
}
