// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import java.io.IOException;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;



@SuppressWarnings("serial")
public class WebsocketPageServlet extends ResourceServlet {



	public static final String PATH = "/echo";
	public static final String TYPE_PARAM = "type";

	static final String PATH_PLACEHOLDER = "/websocket/path";



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
			case ExtendingEndpoint.TYPE:
				filtered = replaceWebsocketPath(resource, ExtendingEndpoint.PATH);
				break;
			case AnnotatedEndpoint.TYPE:
				filtered = replaceWebsocketPath(resource, AnnotatedEndpoint.PATH);
				break;
			default:
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "invalid type");
				return;
		}
		try (
			final var output = response.getOutputStream();
		) {
			output.println(filtered);
		}
	}

	String replaceWebsocketPath(String input, String path) {
		return input.replaceFirst(PATH_PLACEHOLDER, TestServer.APP_PATH + path);
	}



	@Override
	protected String getResourcePath(ServletConfig config) {
		return "/echo.html";
	}
}
