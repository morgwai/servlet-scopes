// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;



/** Either a Jetty or Tyrus or other. */
public interface Server {
	int getPort();
	void stopz() throws Exception;
}
