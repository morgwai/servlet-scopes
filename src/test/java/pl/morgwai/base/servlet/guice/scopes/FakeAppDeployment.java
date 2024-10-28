// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;



public class FakeAppDeployment extends StandaloneWebsocketServerDeployment {



	@Override public String getServletContextName() { return servletContextName; }
	final String servletContextName;



	public FakeAppDeployment(String contextPath, String servletContextName) {
		super(contextPath);
		this.servletContextName = servletContextName;
	}

	public FakeAppDeployment(String contextPath) {
		this(contextPath, contextPath.isEmpty() ? "rootApp" : "app at " + contextPath);
	}



	final Map<String, String> initParams = new HashMap<>(5);

	@Override
	public String getInitParameter(String name) {
		return initParams.get(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		return Collections.enumeration(initParams.keySet());
	}

	@Override
	public boolean setInitParameter(String name, String value) {
		if (initParams.containsKey(name)) return false;
		initParams.put(name, value);
		return true;
	}
}
