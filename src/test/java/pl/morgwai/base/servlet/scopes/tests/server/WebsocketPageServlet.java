// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;



@SuppressWarnings("serial")
public class WebsocketPageServlet extends FilteredResouceServlet {



	public static final String PATH = "/echo";
	public static final String TYPE_PARAM = "type";

	static final String PATH_PLACEHOLDER = "/websocket/path";



	@Override
	protected String filter(String input, HttpServletRequest request) throws ServletException {
		final var type = request.getParameter(TYPE_PARAM);
		if (type == null) throw new ServletException("type param missing");
		switch (type) {
			case ProgrammaticEndpoint.TYPE:
				return replaceWebsocketPath(input, ProgrammaticEndpoint.PATH);
			case ExtendingEndpoint.TYPE: return replaceWebsocketPath(input, ExtendingEndpoint.PATH);
			case AnnotatedEndpoint.TYPE: return replaceWebsocketPath(input, AnnotatedEndpoint.PATH);
			default: throw new ServletException("invalid type " + type);
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
