/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_scopes;

import java.util.LinkedList;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebListener;

import com.google.inject.Module;

import pl.morgwai.base.servlet.scopes.GuiceServletContextListener;



@WebListener
public class ServletContextListener extends GuiceServletContextListener {



	@Override
	protected LinkedList<Module> configureInjections() {
		LinkedList<Module> modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(Service.class).in(servletModule.sessionScope);
		});
		return modules;
	}



	@Override
	protected void configureServletsAndFilters() throws ServletException {
		addServlet("testServlet", TestServlet.class, "/test");
	}
}
