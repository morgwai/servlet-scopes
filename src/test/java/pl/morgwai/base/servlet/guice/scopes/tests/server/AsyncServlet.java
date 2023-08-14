// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.io.IOException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.inject.Inject;

import pl.morgwai.base.servlet.guice.scopes.ServletContextTrackingExecutor;



@SuppressWarnings("serial")
public class AsyncServlet extends TestServlet {



	public static final String PATH = "/async";
	public static final String MODE_PARAM = "mode";
	public static final String MODE_WRAPPED = "wrapped";
	public static final String MODE_TARGETED = "targeted";

	@Inject
	ServletContextTrackingExecutor executor;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			doAsyncDispatch(request, response);
			return;
		}

		verifyScoping(request, "the original container thread");
		final var asyncCtx = MODE_WRAPPED.equals(request.getParameter(MODE_PARAM))
				? request.startAsync(request, response)
				: request.startAsync();
		asyncCtx.setTimeout(0L);
		executor.execute(response, () -> {
			try {
				try {
					verifyScoping(request, "the executor thread");
					if (MODE_TARGETED.equals(request.getParameter(MODE_PARAM))) {
						asyncCtx.dispatch(PATH);
					} else {
						asyncCtx.dispatch();
					}
				} catch (ServletException e) {
					response.sendError(
							HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
				}
			} catch (IOException ignored) {}
		});
	}
}
