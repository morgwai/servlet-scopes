// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.servercommon;



/** Either a Jetty or a Tyrus or another. */
public interface Server {

	String TEST_APP_PATH = "/test";
	/**
	 * All {@code Endpoints} are deployed somewhere under this path (relative to app's deployment
	 * path).
	 */
	String WEBSOCKET_PATH = "/websocket/";

	long DEFAULT_PING_INTERVAL_MILLIS = 500L;
	/**
	 * {@link System#getProperty(String) System property} that may override
	 * {@link #DEFAULT_PING_INTERVAL_MILLIS}.
	 */
	String PING_INTERVAL_MILLIS_PROPERTY = "pingIntervalMillis";

	String getTestAppWebsocketUrl();
	void stop() throws Exception;
}
