// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import org.junit.Test;

import static org.junit.Assert.*;



public class GuiceEndpointConfiguratorGetProxyClassTests {



	/** Test subject. */
	GuiceEndpointConfigurator configurator = new GuiceEndpointConfigurator(null, null, false) {
		@Override
		protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
			final var requiredAnnotationTypes = super.getRequiredEndpointMethodAnnotationTypes();
			requiredAnnotationTypes.add(OnClose.class);
			return requiredAnnotationTypes;
		}
	};



	void testGetProxyClassForAnnotatedEndpoint(Class<?> endpointClass) {
		final var proxyClass = configurator.getProxyClass(endpointClass);
		assertTrue("proxyClass should be a subclass of AnnotatedEndpoint",
				endpointClass.isAssignableFrom(proxyClass));
		assertEquals(
			"ServerEndpoint annotation of proxyClass should be equal to this of "
					+ endpointClass.getSimpleName(),
			endpointClass.getAnnotation(ServerEndpoint.class),
			proxyClass.getAnnotation(ServerEndpoint.class)
		);
		assertEquals(
			"ClientEndpoint annotation of proxyClass should be equal to this of "
					+ endpointClass.getSimpleName(),
			endpointClass.getAnnotation(ClientEndpoint.class),
			proxyClass.getAnnotation(ClientEndpoint.class)
		);
	}



	@ClientEndpoint
	public static class AnnotatedEndpoint {
		@OnOpen public void onOpen(Session connection) {}
		@OnClose public void onClose() {}
	}

	@Test
	public void testGetProxyClassForAnnotatedEndpoint() {
		testGetProxyClassForAnnotatedEndpoint(AnnotatedEndpoint.class);
	}



	@ServerEndpoint("/noOnOpen")
	public static class AnnotatedEndpointWithoutOnOpen {
		@OnClose public void onClose() {}
	}

	@Test
	public void testGetProxyClassForAnnotatedEndpointWithoutOnOpen() {
		try {
			configurator.getProxyClass(AnnotatedEndpointWithoutOnOpen.class);
			fail("creating proxyClass for AnnotatedEndpointWithoutOnOpen should fail");
		} catch (IllegalArgumentException expected) {}
	}



	@ServerEndpoint("/extending")
	public static class AnnotatedExtendingEndpoint extends AnnotatedEndpointWithoutOnOpen {
		@OnOpen public void onOpen(Session connection) {}
	}

	@Test
	public void testGetProxyClassForAnnotatedExtendingEndpoint() {
		testGetProxyClassForAnnotatedEndpoint(AnnotatedExtendingEndpoint.class);
	}



	@ServerEndpoint("/overriding")
	public static class AnnotatedOverridingEndpoint extends AnnotatedEndpoint {
		@OnOpen @Override public void onOpen(Session connection) { super.onOpen(connection); }
	}

	@Test
	public void testGetProxyClassForAnnotatedOverridingEndpoint() {
		testGetProxyClassForAnnotatedEndpoint(AnnotatedOverridingEndpoint.class);
	}



	@ServerEndpoint("/overridingNotReAnnotating")
	public static class AnnotatedOverridingNotReAnnotatingEndpoint extends AnnotatedEndpoint {
		@Override public void onOpen(Session connection) { super.onOpen(connection); }
	}

	@Test
	public void testGetProxyClassForAnnotatedOverridingNotReAnnotatingEndpoint() {
		testGetProxyClassForAnnotatedEndpoint(AnnotatedOverridingNotReAnnotatingEndpoint.class);
	}

	@Test
	public void testGetProxyClassForAnnotatedOverridingNotReAnnotatingEndpointOnTyrus() {
		configurator = new GuiceEndpointConfigurator(null, null, true) {
			@Override
			protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes(
			) {
				final var requiredAnnotationTypes =
						super.getRequiredEndpointMethodAnnotationTypes();
				requiredAnnotationTypes.add(OnClose.class);
				return requiredAnnotationTypes;
			}
		};
		try {
			testGetProxyClassForAnnotatedEndpoint(AnnotatedOverridingNotReAnnotatingEndpoint.class);
			fail("creating proxyClass for AnnotatedOverridingNotReAnnotatingEndpoint should fail on"
					+ " Tyrus");
		} catch (IllegalArgumentException expected) {}
	}



	@ServerEndpoint("/noOnClose")
	public static class AnnotatedEndpointWithoutOnClose {
		@OnOpen public void onOpen(Session connection) {}
	}

	@Test
	public void testGetProxyClassForAnnotatedEndpointWithoutOnClose() {
		try {
			configurator.getProxyClass(AnnotatedEndpointWithoutOnClose.class);
			fail("creating proxyClass for AnnotatedEndpointWithoutOnClose should fail");
		} catch (IllegalArgumentException expected) {}
	}



	@ServerEndpoint("/noSessionParamInOnOpen")
	public static class AnnotatedEndpointWithoutSessionParamInOnOpen {
		@OnOpen public void onOpen() {}
		@OnClose public void onClose() {}
	}

	@Test
	public void testGetProxyClassForAnnotatedEndpointWithoutSessionParamInOnOpen() {
		try {
			configurator.getProxyClass(AnnotatedEndpointWithoutSessionParamInOnOpen.class);
			fail(
				"creating proxyClass for AnnotatedEndpointWithoutSessionParamInOnOpen should fail"
			);
		} catch (IllegalArgumentException expected) {}
	}



	public static class ProgrammaticEndpoint extends Endpoint {
		@Override public void onOpen(Session session, EndpointConfig config) {}
	}

	@Test
	public void testGetProxyClassForProgrammaticEndpoint() {
		final var proxyClass = configurator.getProxyClass(ProgrammaticEndpoint.class);
		assertTrue("proxyClass should be a subclass of ProgrammaticEndpoint",
				ProgrammaticEndpoint.class.isAssignableFrom(proxyClass));
	}
}
