// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * Receives async {@link AsyncServlet#TARGET_PATH_PARAM targeted} requests from {@link AsyncServlet}
 * and {@link #verifyScopingAndSendReply(HttpServletRequest, HttpServletResponse) handles} them.
 */
public class TargetedServlet extends TestServlet {



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (request.getDispatcherType() != DispatcherType.ASYNC) {
			throw new AssertionError("unexpected DispatcherType: " + request.getDispatcherType());
		}
		verifyScopingAndSendReply(request, response);
	}
}
