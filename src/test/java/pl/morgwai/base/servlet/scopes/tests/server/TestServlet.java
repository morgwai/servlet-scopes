// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import static pl.morgwai.base.servlet.scopes.tests.server.ServletContextListener.*;



@SuppressWarnings("serial")
public abstract class TestServlet extends HttpServlet {



	@Inject @Named(CONTAINER_CALL)
	Provider<Service> requestScopedProvider;

	@Inject @Named(HTTP_SESSION)
	Provider<Service> sessionScopedProvider;



	void verifyScoping(HttpServletRequest request, String threadName) throws ServletException {
		if (
			request.getAttribute(CONTAINER_CALL) != requestScopedProvider.get()
			|| request.getAttribute(HTTP_SESSION) != sessionScopedProvider.get()
		) {
			throw new ServletException(
					getClass().getSimpleName() + ": scoping failure on " + threadName);
		}
	}



	void doAsyncDispatch(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		verifyScoping(request, "the 2nd container thread");
		try (
			final var output = response.getWriter();
		) {
			response.setStatus(HttpServletResponse.SC_OK);
			output.println(String.format(
				"%s\nservice hashCodes:\ncall=%d\nsession=%d",
				getClass().getSimpleName(),
				requestScopedProvider.get().hashCode(),
				sessionScopedProvider.get().hashCode())
			);
		}
	}
}
