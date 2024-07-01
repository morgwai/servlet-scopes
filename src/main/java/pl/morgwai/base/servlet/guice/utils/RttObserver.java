// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

/**
 * An interface for {@code Endpoints} to get round-trip time reports upon receiving pongs.
 */
public interface RttObserver {

	/**
	 * Called by {@link javax.websocket.PongMessage}
	 * {@link javax.websocket.MessageHandler handler} to report round-trip time in nanoseconds.
	 */
	void onPong(long rttNanos);
}
