// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.name.Names;
import pl.morgwai.base.servlet.guice.scopes.*;



public class ServiceModule implements Module {



	final ServletWebsocketModule servletModule;
	final ExecutorManager executorManager;
	final boolean httpSessionAvailable;



	public ServiceModule(
		ServletWebsocketModule servletModule,
		ExecutorManager executorManager,
		boolean httpSessionAvailable
	) {
		this.servletModule = servletModule;
		this.executorManager = executorManager;
		this.httpSessionAvailable = httpSessionAvailable;
	}



	@Override
	public void configure(Binder binder) {
		final var executor = executorManager.newContextTrackingExecutor("testExecutor", 2);
		// usually Executors are bound with some name, but in this app there's only 1
		binder.bind(ServletContextTrackingExecutor.class).toInstance(executor);

		// bind Service in 3 different scopes depending on the value of @Named
		binder.bind(Service.class)
			.annotatedWith(Names.named(Service.CONTAINER_CALL))
			.to(Service.class)
			.in(servletModule.containerCallScope);
		binder.bind(Service.class)
			.annotatedWith(Names.named(Service.WEBSOCKET_CONNECTION))
			.to(Service.class)
			.in(servletModule.websocketConnectionScope);
		if (httpSessionAvailable) {
			binder.bind(Service.class)
				.annotatedWith(Names.named(Service.HTTP_SESSION))
				.to(Service.class)
				.in(servletModule.httpSessionScope);
		} else {
			binder.bind(Service.class)
				.annotatedWith(Names.named(Service.HTTP_SESSION))
				.to(Service.class);
		}
	}
}