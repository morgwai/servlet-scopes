// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import com.google.inject.*;
import com.google.inject.Module;
import com.google.inject.name.Names;

import pl.morgwai.base.guice.scopes.ContextTrackingExecutorDecorator;
import pl.morgwai.base.servlet.guice.scopes.*;



public class ServiceModule implements Module {



	final Scope containerCallScope;
	final Scope websocketConnectionScope;
	final Scope httpSessionScope;
	final ContextTrackingExecutorDecorator executor;



	public ServiceModule(
		Scope containerCallScope,
		Scope websocketConnectionScope,
		Scope httpSessionScope,
		ContextTrackingExecutorDecorator executor
	) {
		this.containerCallScope = containerCallScope;
		this.websocketConnectionScope = websocketConnectionScope;
		this.httpSessionScope = httpSessionScope;
		this.executor = executor;
	}



	@Override
	public void configure(Binder binder) {
		// usually Executors are bound with some name, but in this app there's only 1
		binder.bind(ContextTrackingExecutorDecorator.class).toInstance(executor);

		// bind Service in 3 different scopes depending on the value of @Named
		binder.bind(Service.class)
			.annotatedWith(Names.named(Service.CONTAINER_CALL))
			.to(Service.class)
			.in(containerCallScope);
		binder.bind(Service.class)
			.annotatedWith(Names.named(Service.WEBSOCKET_CONNECTION))
			.to(Service.class)
			.in(websocketConnectionScope);
		if (httpSessionScope != null) {
			binder.bind(Service.class)
				.annotatedWith(Names.named(Service.HTTP_SESSION))
				.to(Service.class)
				.in(httpSessionScope);
		} else {
			binder.bind(Service.class)
				.annotatedWith(Names.named(Service.HTTP_SESSION))
				.to(Service.class);
		}
	}
}
