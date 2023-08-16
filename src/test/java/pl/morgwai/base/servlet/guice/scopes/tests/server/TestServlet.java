// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.*;

import com.google.inject.*;
import com.google.inject.name.Named;

import static pl.morgwai.base.servlet.guice.scopes.tests.server.ServletContextListener.*;



/** Base class for all other {@code Servlets} testing different ways of dispatching. */
public abstract class TestServlet extends HttpServlet {



	/** Format for servlet and websocket responses. */
	public static final String RESPONSE_FORMAT = "%s\nservice hashCodes:\ncall=%d\nsession=%d";



	@Inject @Named(CONTAINER_CALL)
	Provider<Service> requestScopedProvider;

	@Inject @Named(HTTP_SESSION)
	Provider<Service> sessionScopedProvider;



	/**
	 * Verifies if objects obtained from {@link #requestScopedProvider} and sessionScopedProvider
	 * are the same as the ones stored initially in
	 * {@link HttpServletRequest#getAttribute(String) request attributes} by
	 * {@link ForwardingServlet}.
	 */
	void verifyScoping(HttpServletRequest request, String threadDesignation)
			throws ServletException {
		try {
			if (
				request.getAttribute(CONTAINER_CALL) != requestScopedProvider.get()
				|| request.getAttribute(HTTP_SESSION) != sessionScopedProvider.get()
			) {
				throw new ServletException(getClass().getSimpleName() + ": scoping failure on "
						+ threadDesignation + " (" + Thread.currentThread().getName() + ')');
			}
		} catch (RuntimeException e) {
			throw new ServletException(
				getClass().getSimpleName() + ": scoping failure on " + threadDesignation
						+ " (" + Thread.currentThread().getName() + ')',
				e
			);
		}
	}



	/**
	 * Sends the final response in {@link #RESPONSE_FORMAT}. The 1st line contains
	 * {@link Class#getSimpleName() simple class name} of the actual {@code Servlet} that sent the
	 * response as this method may be called in various {@code Servlets} depending on
	 * {@link AsyncServlet#MODE_PARAM} and {@link AsyncServlet#TARGET_PATH_PARAM} request params.
	 */
	void doAsyncHandling(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		verifyScoping(request, "the 2nd container thread");
		try (
			final var output = response.getWriter();
		) {
			response.setStatus(HttpServletResponse.SC_OK);
			output.println(String.format(
				RESPONSE_FORMAT,
				getClass().getSimpleName(),
				requestScopedProvider.get().hashCode(),
				sessionScopedProvider.get().hashCode())
			);
		}
	}
}
