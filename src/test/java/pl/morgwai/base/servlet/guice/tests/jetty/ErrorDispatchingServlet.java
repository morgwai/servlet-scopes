// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



/** Tests handling of dispatching to non-existent path. */
public class ErrorDispatchingServlet extends TestServlet {



	public static final String ERROR_HANDLER_PATH = "/error";
	public static final String NON_EXISTENT_PATH = "/nonExistent";
	static final String APP_DISPATCHED_ATTRIBUTE = "appDispatched";



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		final var dispatcherType = request.getDispatcherType();
		switch (dispatcherType) {
			case REQUEST:
				request.setAttribute(APP_DISPATCHED_ATTRIBUTE, true);
				request.setAttribute(Service.CONTAINER_CALL, requestScopedProvider.get());
				request.setAttribute(Service.HTTP_SESSION, sessionScopedProvider.get());
				verifyScoping(INITIAL_THREAD_DESIGNATION, request);
				request.getRequestDispatcher(NON_EXISTENT_PATH).forward(request, response);
				return;
			case ERROR:
				if (request.getAttribute(APP_DISPATCHED_ATTRIBUTE) == null) {  // from user miss
					request.setAttribute(Service.CONTAINER_CALL, requestScopedProvider.get());
					request.setAttribute(Service.HTTP_SESSION, sessionScopedProvider.get());
				}
				verifyScopingAndSendReply(request, response);
				return;
			default: throw new ServletException("unexpected DispatcherType: " + dispatcherType);
		}
	}
}
