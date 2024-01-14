// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.jetty;

import java.io.IOException;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Service;

import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Service.CONTAINER_CALL;
import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Service.HTTP_SESSION;



/** Base class for all other {@code Servlets} testing different ways of dispatching. */
public abstract class TestServlet extends HttpServlet {



	public static final String RESPONDING_SERVLET = "respondingServlet";

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
	 * Sends the final response as {@link Properties}.
	 * The following {@link Properties#setProperty(String, String) properties will be set}:
	 * <ul>
	 *   <li>{@link #RESPONDING_SERVLET} - {@link Class#getSimpleName() simple name of the Class}
	 *       of the {@code Servlet} that actually produced the response</li>
	 *   <li>{@link Service#CONTAINER_CALL} - hash of the
	 *       {@link pl.morgwai.base.servlet.guice.scopes.ServletModule#containerCallScope
	 *       containerCallScope}d instance of {@link Service}</li>
	 *   <li>{@link Service#HTTP_SESSION} - hash of the
	 *       {@link pl.morgwai.base.servlet.guice.scopes.ServletModule#httpSessionScope
	 *       httpSessionScope}d instance of {@link Service}</li>
	 * </ul>
	 */
	void doAsyncHandling(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		verifyScoping(request, "the 2nd container thread");
		try (
			final var responseStream = response.getOutputStream();
		) {
			final var responseContent = new Properties(5);
			responseContent.setProperty(RESPONDING_SERVLET, getClass().getSimpleName());
			responseContent.setProperty(
				CONTAINER_CALL,
				String.valueOf(requestScopedProvider.get().hashCode())
			);
			responseContent.setProperty(
				HTTP_SESSION,
				String.valueOf(sessionScopedProvider.get().hashCode())
			);
			response.setStatus(HttpServletResponse.SC_OK);
			responseContent.store(responseStream, null);
		}
	}
}
