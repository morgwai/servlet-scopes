// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.inject.Inject;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;



/**
 * Receives requests from {@link ForwardingServlet},
 * {@link HttpServletRequest#startAsync() starts async processing}, dispatches processing to its
 * {@code ContextTrackingExecutor} and from there {@link AsyncContext#dispatch() dispatches} back to
 * the container in a way specified by {@link #MODE_PARAM} and {@link #TARGET_PATH_PARAM}. If a
 * request comes back to this {@code Servlet} {@link DispatcherType#ASYNC asynchronously}, it is
 * {@link #verifyScopingAndSendReply(HttpServletRequest, HttpServletResponse) handled}.
 */
public class AsyncServlet extends TestServlet {



	/**
	 * A {@link HttpServletRequest#getParameter(String) request param} controlling how
	 * {@link AsyncContext} will be created. Possible values are {@link #MODE_WRAPPED} and
	 * {@link #MODE_UNWRAPPED}.
	 */
	public static final String MODE_PARAM = "mode";
	/**
	 * {@link AsyncContext} will be created with
	 * {@link HttpServletRequest#startAsync(ServletRequest, ServletResponse)}.
	 */
	public static final String MODE_WRAPPED = "wrapped";
	/** {@link AsyncContext} will be created with {@link HttpServletRequest#startAsync()}. */
	public static final String MODE_UNWRAPPED = "unwrapped";

	/**
	 * A {@link HttpServletRequest#getParameter(String) request param} controlling
	 * {@link AsyncContext} dispatch target. If present, the request will be
	 * {@link AsyncContext#dispatch(String) dispatched to the given path}, otherwise it will be
	 * {@link AsyncContext#dispatch() dispatched to its original URI}.
	 */
	public static final String TARGET_PATH_PARAM = "targetPath";



	@Inject ContextTrackingExecutor executor;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		if (request.getDispatcherType() == DispatcherType.ASYNC) {
			verifyScopingAndSendReply(request, response);
			return;
		}

		verifyScoping(request, INITIAL_THREAD_DESIGNATION);
		final var asyncCtx = MODE_WRAPPED.equals(request.getParameter(MODE_PARAM))
				? request.startAsync(request, response)
				: request.startAsync();
		asyncCtx.setTimeout(1800L * 1000L);
		executor.execute(() -> {
			try {
				verifyScoping(request, "the executor thread");
				final var targetPath = request.getParameter(TARGET_PATH_PARAM);
				if (targetPath != null) {
					asyncCtx.dispatch(targetPath);
				} else {
					asyncCtx.dispatch();
				}
			} catch (Throwable e) {
				log.log(Level.SEVERE, "Exception", e);
				try {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.toString());
				} catch (IOException ignored) {}
				asyncCtx.complete();
				if (e instanceof Error) throw (Error) e;
			}
		});
	}



	static final Logger log = Logger.getLogger(AsyncServlet.class.getName());
}
