// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import java.util.concurrent.atomic.AtomicInteger;



/** Injected dependency mock. */
public class Service {



	// values for @Named corresponding to available Scopes
	public static final String CONTAINER_CALL = "containerCall";
	public static final String WEBSOCKET_CONNECTION = "wsConnection";
	public static final String HTTP_SESSION = "httpSession";



	static final AtomicInteger idSequence = new AtomicInteger(0);
	final int id = idSequence.incrementAndGet();



	@Override
	public int hashCode() {
		return id;
	}
}
