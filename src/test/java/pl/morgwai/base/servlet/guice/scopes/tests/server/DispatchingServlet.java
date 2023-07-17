// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static pl.morgwai.base.servlet.guice.scopes.tests.server.ServletContextListener.*;



@SuppressWarnings("serial")
public class DispatchingServlet extends TestServlet {



	public static final String PATH = "/dispatch";



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			doAsyncDispatch(request, response);
			return;
		}

		final var requestScopedService = requestScopedProvider.get();
		final var sessionScopedService = sessionScopedProvider.get();
		request.setAttribute(CONTAINER_CALL, requestScopedService);
		request.setAttribute(HTTP_SESSION, sessionScopedService);
		verifyScoping(request, "the original container thread");
		request.getRequestDispatcher(AsyncServlet.PATH)
				.forward(request, response);
	}
}
