// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import pl.morgwai.base.servlet.guice.utils.RttObserver;



/**
 * {@link EchoEndpoint#send(String) Sends} received {@link RttObserver RTT reports} as a text
 * message. Ignores received messages.
 */
public class RttReportingEndpoint extends ProgrammaticEndpoint implements RttObserver {



	public static final String TYPE = "rttReporting";
	public static final String PATH = Server.WEBSOCKET_PATH + TYPE;
	public static final String RTT_PROPERTY = "rtt";



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		echoEndpoint.onOpen(connection, config);
	}



	@Override
	public void onPong(long rttNanos) {
		EchoEndpoint.log.info(String.format("got pong RTT report: %,10dns", rttNanos));
		echoEndpoint.send(RTT_PROPERTY, String.valueOf(rttNanos));
	}
}
