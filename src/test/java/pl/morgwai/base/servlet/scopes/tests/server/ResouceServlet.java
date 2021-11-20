// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



@SuppressWarnings("serial")
public class ResouceServlet extends HttpServlet {



	public static final String RESOURCE_PATH_PARAM =
			ResouceServlet.class.getName() + ".resourcePath";



	protected String resource;



	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			resource = new String(
					getClass().getResourceAsStream(getResourcePath(config)).readAllBytes());
		} catch (IOException e) {
			throw new ServletException(e);
		}
	}

	protected String getResourcePath(ServletConfig config) {
		return config.getInitParameter(RESOURCE_PATH_PARAM);
	}



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try (
			final var output = response.getOutputStream();
		) {
			output.println(resource);
		}
	}
}
