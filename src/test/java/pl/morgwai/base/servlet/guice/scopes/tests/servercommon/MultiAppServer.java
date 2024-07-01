// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;



public interface MultiAppServer extends Server {
	String SECOND_APP_PATH = "";  // root app
	String getSecondAppWebsocketUrl();
}
