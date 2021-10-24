// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;
import java.io.PrintWriter;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Provider;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



@SuppressWarnings("serial")
public class TestServlet extends HttpServlet {



	@Inject @Named(ServletContextListener.CONTAINER_CALL)
	Provider<Service> requestScopedProvider;

	@Inject @Named(ServletContextListener.HTTP_SESSION)
	Provider<Service> sessionScopedProvider;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		var requestScopedService = requestScopedProvider.get();
		var sessionScopedService = sessionScopedProvider.get();
		if (requestScopedService != requestScopedProvider.get()
				|| sessionScopedService != sessionScopedProvider.get()) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "scoping failure");
			return;
		}
		PrintWriter writer = response.getWriter();
		writer.println(String.format(
				"service hashCodes: request=%d, session=%d",
				requestScopedService.hashCode(),
				sessionScopedService.hashCode()));
		writer.close();
	}
}
