// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;

import static pl.morgwai.samples.servlet_scopes.ServletContextListener.*;



@SuppressWarnings("serial")
public class AsyncDispatchingServlet extends HttpServlet {



	public static final String URI = "/test";

	@Inject @Named(CONTAINER_CALL)
	Provider<Service> requestScopedProvider;

	@Inject @Named(HTTP_SESSION)
	Provider<Service> sessionScopedProvider;

	@Inject
	ContextTrackingExecutor executor;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (request.getDispatcherType() == DispatcherType.REQUEST) {
			doRequestDispatch(request, response);
		}
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			doAsyncDispatch(request, response);
		}
	}



	void doRequestDispatch(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final var requestScopedService = requestScopedProvider.get();
		final var sessionScopedService = sessionScopedProvider.get();
		if (
			requestScopedService != requestScopedProvider.get()
			|| sessionScopedService != sessionScopedProvider.get()
		) {
			response.sendError(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"scoping failure on the original container thread");
			return;
		}
		request.setAttribute(CONTAINER_CALL, requestScopedService);
		request.setAttribute(HTTP_SESSION, sessionScopedService);
		final var asyncCtx = request.startAsync();
		asyncCtx.setTimeout(0l);
		executor.execute(response, () -> {
			try {
				if (
					requestScopedService != requestScopedProvider.get()
					|| sessionScopedService != sessionScopedProvider.get()
				) {
					response.sendError(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
							"scoping failure on the executor thread");
					return;
				}
				asyncCtx.dispatch();
			} catch (IOException ignored) {}
		});
	}



	void doAsyncDispatch(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (
			request.getAttribute(CONTAINER_CALL) != requestScopedProvider.get()
			|| request.getAttribute(HTTP_SESSION) != sessionScopedProvider.get()
		) {
			response.sendError(
					HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"scoping failure on the second container thread");
			return;
		}

		try (
			final var writer = response.getWriter();
		) {
			response.setStatus(HttpServletResponse.SC_OK);
			writer.println(String.format(
					"service hashCodes: request=%d, session=%d",
					requestScopedProvider.get().hashCode(),
					sessionScopedProvider.get().hashCode()));
		}
	}
}
