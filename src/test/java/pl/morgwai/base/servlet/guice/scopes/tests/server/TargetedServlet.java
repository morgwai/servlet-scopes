// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



/**
 * Receives async {@link AsyncServlet#TARGET_PATH_PARAM targeted} requests from {@link AsyncServlet}
 * and {@link #doAsyncHandling(HttpServletRequest, HttpServletResponse) handles} them.
 */
public class TargetedServlet extends TestServlet {



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (request.getDispatcherType() != DispatcherType.ASYNC) {
			throw new ServletException(TestServlet.class.getSimpleName()
					+ "should only receive async requests");
		}
		doAsyncHandling(request, response);
	}
}
