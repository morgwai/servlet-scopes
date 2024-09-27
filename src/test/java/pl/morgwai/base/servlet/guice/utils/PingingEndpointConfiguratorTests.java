// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import javax.websocket.*;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator.isOnOpen;



public class PingingEndpointConfiguratorTests {



	public static class AnnotatedEndpoint {
		@OnOpen public void onOpen(Session connection) {}
		public void onOpen(Session connection, EndpointConfig config) {}
	}

	@Test
	public void testIsOnOpenValidAnnotatedOnOpen() throws NoSuchMethodException {
		final var method = AnnotatedEndpoint.class.getDeclaredMethod("onOpen", Session.class);
		assertTrue(isOnOpen(method));
	}

	@Test
	public void testIsOnOpenUnannotatedMethodMatchingEndpointOnOpen() throws NoSuchMethodException {
		final var method = AnnotatedEndpoint.class.getDeclaredMethod(
				"onOpen", Session.class, EndpointConfig.class);
		assertFalse(isOnOpen(method));
	}



	public static class AnnotatedExtendingEndpoint extends AnnotatedEndpoint {
		@Override public void onOpen(Session connection) {}
	}

	@Test
	public void testIsOnOpenValidOverridingAnnotatedOnOpen() throws NoSuchMethodException {
		final var method =
				AnnotatedExtendingEndpoint.class.getDeclaredMethod("onOpen", Session.class);
		assertTrue(isOnOpen(method));
	}



	public static class ProgrammaticEndpoint extends Endpoint {
		@Override public void onOpen(Session session, EndpointConfig config) {}
		@OnOpen public void onOpen(Session connection) {}
	}

	@Test
	public void testIsOnOpenValidProgrammaticEndpointOnOpen() throws NoSuchMethodException {
		final var method = ProgrammaticEndpoint.class.getDeclaredMethod(
				"onOpen", Session.class, EndpointConfig.class);
		assertTrue(isOnOpen(method));
	}

	@Test
	public void testIsOnOpenAnnotatedOnOpenInProgrammaticEndpoint() throws NoSuchMethodException {
		final var method = ProgrammaticEndpoint.class.getDeclaredMethod("onOpen", Session.class);
		assertFalse(isOnOpen(method));
	}
}
