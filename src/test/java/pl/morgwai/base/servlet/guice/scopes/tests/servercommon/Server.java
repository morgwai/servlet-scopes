// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;



/** Either a Jetty or TyrusServer or other. */
public interface Server {

	/** All {@code Endpoints} are deployed somewhere under this path (relative to deployment). */
	String WEBSOCKET_PATH = "/websocket/";
	String PING_INTERVAL_MILLIS_PROPERTY = "pingIntervalMillis";
	String APP_PATH = "/test";

	String getAppWebsocketUrl();
	void shutdown() throws Exception;
}
