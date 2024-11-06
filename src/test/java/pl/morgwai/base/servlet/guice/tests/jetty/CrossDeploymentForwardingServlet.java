// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.MultiAppServer;
import pl.morgwai.base.servlet.guice.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



public class CrossDeploymentForwardingServlet extends TestServlet {



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		final var dispatcherType = request.getDispatcherType();
		switch (dispatcherType) {
			case REQUEST:
				request.setAttribute(Service.CONTAINER_CALL, requestScopedProvider.get());
				request.setAttribute(Service.HTTP_SESSION, sessionScopedProvider.get());
				verifyScoping(request, INITIAL_THREAD_DESIGNATION);
				final var otherDeploymentPath =
						request.getContextPath().equals(Server.TEST_APP_PATH)
							? MultiAppServer.SECOND_APP_PATH
							: Server.TEST_APP_PATH;
				request.getServletContext()
					.getContext(otherDeploymentPath)
					.getRequestDispatcher(request.getServletPath())
					.forward(request, response);
				return;
			case FORWARD:
			case INCLUDE:
				try {
					final var requestScopedService = requestScopedProvider.get();
					final var sessionScopedService = sessionScopedProvider.get();
					if (
						request.getAttribute(Service.CONTAINER_CALL) == requestScopedService
						|| request.getAttribute(Service.HTTP_SESSION) == sessionScopedService
					) {
						throw new ServletException(getClass().getSimpleName() + ": scoping failure "
								+ "after cross-deployment forward ("
								+ Thread.currentThread().getName() + ')');
					}

					request.setAttribute(Service.CONTAINER_CALL, requestScopedService);
					request.setAttribute(Service.HTTP_SESSION, sessionScopedService);
					verifyScopingAndSendReply(request, response);
				} catch (RuntimeException e) {
					throw new ServletException(
						getClass().getSimpleName() + ": scoping failure after cross-deployment " +
								"forward (" + Thread.currentThread().getName() + ')',
						e
					);
				}
				return;
			default: throw new ServletException("unexpected DispatcherType: " + dispatcherType);
		}
	}
}
