// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.guice.tests.servercommon.*;



/**
 * Serves the websocket echo page (a text input field and the corresponding echo log) that
 * connects to an {@code Endpoint} given by {@link #TYPE_PARAM}.
 */
public class EchoWebsocketPageServlet extends ResourceServlet {



	/**
	 * A {@link HttpServletRequest#getParameter(String) request param} specifying which
	 * {@code Endpoint} to connect to. Valid values are the same as {@code TYPE} constants in
	 * {@code Endpoints} from this package that extend/wrap {@link EchoEndpoint}.
	 */
	public static final String TYPE_PARAM = "type";

	/**
	 * {@code String} in {@code /echo.html} resource that will be replaced by the actual path on the
	 * server where the {@code Endpoint} given by {@link #TYPE_PARAM} is deployed.
	 */
	static final String PATH_PLACEHOLDER = "/websocket/path";



	String appDeploymentPath;



	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		appDeploymentPath = config.getServletContext().getContextPath();
	}



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		final var type = request.getParameter(TYPE_PARAM);
		if (type == null) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "type param missing");
			return;
		}
		String filtered;
		switch (type) {
			case ProgrammaticEndpoint.TYPE:
				filtered = replaceWebsocketPath(resource, ProgrammaticEndpoint.PATH);
				break;
			case AnnotatedExtendingProgrammaticEndpoint.TYPE:
				filtered =
						replaceWebsocketPath(resource, AnnotatedExtendingProgrammaticEndpoint.PATH);
				break;
			case AnnotatedEndpoint.TYPE:
				filtered = replaceWebsocketPath(resource, AnnotatedEndpoint.PATH);
				break;
			default:
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid websocket type");
				return;
		}
		try (
			final var output = response.getOutputStream();
		) {
			output.println(filtered);
		}
	}

	String replaceWebsocketPath(String input, String path) {
		return input.replaceFirst(PATH_PLACEHOLDER, appDeploymentPath + path);
	}



	@Override
	protected String getResourcePath(ServletConfig config) {
		return "/echo.html";
	}
}
