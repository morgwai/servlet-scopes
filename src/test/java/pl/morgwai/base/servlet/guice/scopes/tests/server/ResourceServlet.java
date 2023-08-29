// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.server;

import java.io.IOException;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;



/** Serves a resource given by {@link #RESOURCE_PATH_PARAM} init param. */
public class ResourceServlet extends HttpServlet {



	public static final String RESOURCE_PATH_PARAM =
			ResourceServlet.class.getName() + ".resourcePath";



	protected String resource;



	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try (
			final var resourceStream = getClass().getResourceAsStream(getResourcePath(config));
		) {
			resource = new String(resourceStream.readAllBytes());
		} catch (IOException | NullPointerException e) {
			throw new ServletException(e);
		}
	}

	protected String getResourcePath(ServletConfig config) {
		return config.getInitParameter(RESOURCE_PATH_PARAM);
	}



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		try (
			final var output = response.getOutputStream();
		) {
			output.println(resource);
		}
	}
}
