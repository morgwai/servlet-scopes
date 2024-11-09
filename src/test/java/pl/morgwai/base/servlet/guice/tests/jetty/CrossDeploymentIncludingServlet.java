// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import static pl.morgwai.base.servlet.guice.tests.servercommon.Service.CONTAINER_CALL;
import static pl.morgwai.base.servlet.guice.tests.servercommon.Service.HTTP_SESSION;

import java.io.IOException;
import java.util.Properties;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.MultiAppServer;
import pl.morgwai.base.servlet.guice.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



public class CrossDeploymentIncludingServlet extends TestServlet {



	public static final String INCLUDED_DEPLOYMENT_PREFIX = "includedDeployment.";



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		final var dispatcherType = request.getDispatcherType();
		switch (dispatcherType) {
			case REQUEST: {
				final var requestScopedInstance = requestScopedProvider.get();
				final var sessionScopedInstance = sessionScopedProvider.get();

				storeInstancesAndVerifyScoping(INITIAL_THREAD_DESIGNATION,
						request, requestScopedInstance, sessionScopedInstance);

				// include the other deployment
				final var otherDeploymentPath =
						request.getContextPath().equals(Server.TEST_APP_PATH)
							? MultiAppServer.SECOND_APP_PATH
							: Server.TEST_APP_PATH;
				request.getServletContext()
					.getContext(otherDeploymentPath)
					.getRequestDispatcher(request.getServletPath())
					.include(request, response);

				storeInstancesAndVerifyScoping("back to the initial container Thread",
						request, requestScopedInstance, sessionScopedInstance);

				// verify if ServletRequestContext attribute was restored properly
				final AsyncContext asyncCtx = request.startAsync();
				asyncCtx.setTimeout(1800L * 1000L);
				asyncCtx.dispatch();
				return;
			}
			case FORWARD:
			case INCLUDE: {
				final Service requestScopedInstance = requestScopedProvider.get();
				final Service sessionScopedInstance = sessionScopedProvider.get();

				// verify we are in a new Context
				if (
					request.getAttribute(CONTAINER_CALL) == requestScopedInstance
					|| request.getAttribute(HTTP_SESSION) == sessionScopedInstance
				) {
					throw new ServletException(getClass().getSimpleName() + ": stale instances "
							+ "after cross-deployment include ("
							+ Thread.currentThread().getName() + ')');
				}

				storeInstancesAndVerifyScoping("included deployment container Thread",
						request, requestScopedInstance, sessionScopedInstance);

				// send partial reply
				final var responseStream = response.getOutputStream();
				final var responseContent = new Properties(5);
				responseContent.setProperty(
					INCLUDED_DEPLOYMENT_PREFIX + CONTAINER_CALL,
					String.valueOf(requestScopedInstance.hashCode())
				);
				responseContent.setProperty(
					INCLUDED_DEPLOYMENT_PREFIX + HTTP_SESSION,
					String.valueOf(sessionScopedInstance.hashCode())
				);
				responseContent.store(responseStream, null);
				return;
			}
			case ASYNC: {
				verifyScopingAndSendReply(request,response);
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
		request.setAttribute(CONTAINER_CALL, requestScopedInstance);
		request.setAttribute(HTTP_SESSION, sessionScopedInstance);
		verifyScoping(request, threadDesignation);
	}
}
