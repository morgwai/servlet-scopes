// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.websocket.MessageHandler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static pl.morgwai.base.servlet.guice.scopes.WebsocketConnectionProxy.getHandlerMessageClass;



public class WebsocketConnectionProxyTests {



	@Test
	public void testGetHandlerMessageOfAnonymousClasses() {
		final var partialStringHandler = new MessageHandler.Partial<String>() {
			@Override public void onMessage(String partialMessage, boolean last) {}
		};
		assertEquals("message class of an anonymous Handler should be properly recognized",
				String.class, getHandlerMessageClass(partialStringHandler));

		final var wholeLongHandler = new MessageHandler.Whole<Long>() {
			@Override public void onMessage(Long message) {}
		};
		assertEquals("message class of an anonymous Handler should be properly recognized",
				Long.class, getHandlerMessageClass(wholeLongHandler));
	}



	@Test
	public void testGetHandlerMessageOfAbstractHandler() {
		try {
			getHandlerMessageClass(new MessageHandler() {});
			fail("attempting to get a message class of an abstract Handler should fail");
		} catch (IllegalArgumentException expected) {}
	}



	static class TestHandler implements MessageHandler.Whole<String> {
		@Override public void onMessage(String message) {}
	}

	@Test
	public void testGetHandlerMessageOfConcreteClass() {
		assertEquals(
			"message class of a concrete class implementing a single Handler should be properly "
					+ "recognized",
			String.class,
			getHandlerMessageClass(new TestHandler())
		);
	}



	static class MultiHandler
			implements MessageHandler.Partial<String>, MessageHandler.Whole<Long> {
		@Override public void onMessage(String partialMessage, boolean last) {}
		@Override public void onMessage(Long message) {}
	}

	@Test
	public void testGetHandlerMessageOfAmbiguousHandler() {
		try {
			getHandlerMessageClass(new MultiHandler());
			fail("attempting to get a message class of a Handler of multiple types should fail");
		} catch (IllegalArgumentException expected) {}
	}
}
