// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.MultiAppServer;
import pl.morgwai.base.servlet.guice.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



/**
 * First receives a normal {@link HttpServletRequest}, {@link RequestDispatcher dispatches} it to
 * the same {@code Servlet} in another deployment, finally
 * {@link AsyncContext#dispatch() dispatches it asynchronously} again to the same {@code Servlet} in
 * the second deployment in case of
 * {@link RequestDispatcher#forward(ServletRequest, ServletResponse) forward} or back in the initial
 * deployment in case of {@link RequestDispatcher#include(ServletRequest, ServletResponse) include}.
 * During each stage {@link #verifyScoping(String, HttpServletRequest) verifies scoping} and
 * {@link #sendScopedObjectHashes(HttpServletRequest, HttpServletResponse, String)
 * sends scoped Object hashes} as {@link java.util.Properties} with names prefixed with one of
 * {@link #INITIAL_DEPLOYMENT_PREFIX}, {@link #SECOND_DEPLOYMENT_PREFIX} or
 * {@link #ASYNC_PREFIX} respectively.
 */
public class CrossDeploymentDispatchingServlet extends TestServlet {



	public static final String INITIAL_DEPLOYMENT_PREFIX = "initialDeployment.";
	public static final String SECOND_DEPLOYMENT_PREFIX = "secondDeployment.";
	public static final String ASYNC_PREFIX = "async.";



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		final var dispatcherType = request.getDispatcherType();
		switch (dispatcherType) {
			case REQUEST: {  // initial checks, dispatch, after INCLUDE also ASYNC
				final var requestScopedInstance = requestScopedProvider.get();
				final var sessionScopedInstance = sessionScopedProvider.get();
				storeInstancesAndVerifyScoping(INITIAL_THREAD_DESIGNATION,
						request, requestScopedInstance, sessionScopedInstance);

				final var otherDeploymentPath =
						request.getContextPath().equals(Server.TEST_APP_PATH)
							? MultiAppServer.SECOND_APP_PATH
							: Server.TEST_APP_PATH;
				final var dispatcher = request.getServletContext()
					.getContext(otherDeploymentPath)
					.getRequestDispatcher(request.getServletPath());
				if (
					DispatcherType.FORWARD.toString().equals(
							request.getParameter(DispatcherType.class.getSimpleName()))
				) {
					dispatcher.forward(request, response);
				} else {  // INCLUDE
					dispatcher.include(request, response);

					// back from the other deployment
					storeInstancesAndVerifyScoping("back to the initial container Thread",
							request, requestScopedInstance, sessionScopedInstance);
					final var asyncCtx = request.startAsync();
					asyncCtx.setTimeout(1800L * 1000L);
					asyncCtx.dispatch();
				}
				return;
			}
			case FORWARD:
			case INCLUDE: {
				final var requestScopedInstance = requestScopedProvider.get();
				final var sessionScopedInstance = sessionScopedProvider.get();
				if (
					request.getAttribute(Service.CONTAINER_CALL) == requestScopedInstance
					|| request.getAttribute(Service.HTTP_SESSION) == sessionScopedInstance
				) {
					throw new ServletException(
							"Provider served stale instance after cross-deployment dispatch ("
							+ Thread.currentThread().getName() + ')');
				}
				sendScopedObjectHashes(request, response, INITIAL_DEPLOYMENT_PREFIX);
				storeInstancesAndVerifyScoping("the second deployment container Thread",
						request, requestScopedInstance, sessionScopedInstance);
				sendScopedObjectHashes(request, response, SECOND_DEPLOYMENT_PREFIX);
				if (request.getDispatcherType() == DispatcherType.FORWARD) {
					final var asyncCtx = request.startAsync(request, response);
					asyncCtx.setTimeout(1800L * 1000L);
					asyncCtx.dispatch();
				}
				return;
			}
			case ASYNC: {
				verifyScoping("async container Thread", request);
				sendScopedObjectHashes(request, response, ASYNC_PREFIX);
				return;
			}
			default: throw new AssertionError("unexpected DispatcherType: " + dispatcherType);
		}
	}



	void storeInstancesAndVerifyScoping(
		String threadDesignation,
		HttpServletRequest request,
		Service requestScopedInstance,
		Service sessionScopedInstance
	) throws ServletException {
		request.setAttribute(Service.CONTAINER_CALL, requestScopedInstance);
		request.setAttribute(Service.HTTP_SESSION, sessionScopedInstance);
		verifyScoping(threadDesignation, request);
	}
}
