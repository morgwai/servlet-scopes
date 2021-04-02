/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;
import java.io.PrintWriter;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import pl.morgwai.base.servlet.scopes.RequestContext;



@SuppressWarnings("serial")
public class TestServlet extends HttpServlet {



	@Inject Provider<RequestContext> requestContextTracker;
	@Inject Provider<Service> serviceProvider;



	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter writer = response.getWriter();
		if (requestContextTracker.get().getRequest() == request) {
			response.setStatus(HttpServletResponse.SC_OK);
			writer.println("OK");
		} else {
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			writer.println("DUPA");
		}
		writer.println("service hash: " + serviceProvider.get().hashCode());
		writer.close();
	}
}
