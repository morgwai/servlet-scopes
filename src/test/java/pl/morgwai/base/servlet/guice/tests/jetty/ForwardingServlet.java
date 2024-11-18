// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



/**
 * Most async-wanna-be test requests initially arrive to this {@code Servlet} and are
 * {@link jakarta.servlet.RequestDispatcher#forward(ServletRequest, ServletResponse) forwarded} to
 * {@link AsyncServlet}. If a request comes back to this {@code Servlet}
 * {@link DispatcherType#ASYNC asynchronously}, a
 * {@link #verifyScopingAndSendReply(HttpServletRequest, HttpServletResponse) reply is sent}.
 */
public class ForwardingServlet extends TestServlet {



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			verifyScopingAndSendReply(request, response);
			return;
		}

		// store scoped objects for verifyScoping(...)
		request.setAttribute(Service.CONTAINER_CALL, requestScopedProvider.get());
		request.setAttribute(Service.HTTP_SESSION, sessionScopedProvider.get());
		verifyScoping(INITIAL_THREAD_DESIGNATION, request);
		request.getRequestDispatcher("/" + AsyncServlet.class.getSimpleName())
				.forward(request, response);
	}
}
