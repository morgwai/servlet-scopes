// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;

import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.Inject;

import pl.morgwai.base.servlet.scopes.ContextTrackingExecutor;



@SuppressWarnings("serial")
public class AsyncServlet extends TestServlet {



	public static final String URI = "/test/async";
	public static final String MODE_PARAM_NAME = "mode";
	public static final String MODE_WRAPPED = "wrapped";
	public static final String DISPATCH_PARAM_NAME = "dispatch";

	@Inject
	ContextTrackingExecutor executor;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			doAsyncDispatch(request, response);
			return;
		}

		verifyScoping(request, "the original container thread");
		final var asyncCtx = MODE_WRAPPED.equals(request.getParameter(MODE_PARAM_NAME))
				? request.startAsync(request, response)
				: request.startAsync();
		asyncCtx.setTimeout(0l);
		executor.execute(response, () -> {
			try {
				try {
					verifyScoping(request, "the executor thread");
					String dispatchUri = request.getParameter(DISPATCH_PARAM_NAME);
					if (dispatchUri != null) {
						asyncCtx.dispatch(dispatchUri);
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