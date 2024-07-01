// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator.*;



public abstract class EndpointDynamicProxyTests extends EndpointProxyTests {



	protected GuiceEndpointConfigurator configurator;

	@Override
	protected void additionalSetup() {
		configurator = new GuiceEndpointConfigurator(null, ctxTracker);
	}



	@Override
	protected final TestEndpoint createEndpointProxy(
		TestEndpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) throws Exception {
		return configurator.getProxyClass(toWrap.getClass()).getConstructor().newInstance();
	}



	void setEndpointProxyHandler(
		TestEndpoint endpointProxy,
		InvocationHandler handler
	) throws NoSuchFieldException, IllegalAccessException {
		endpointProxy.getClass().getDeclaredField(INVOCATION_HANDLER_FIELD_NAME).set(
			endpointProxy,
			new EndpointProxyHandler(handler, ctxTracker)
		);
	}



	@Override
	public final void testOnOpenThenOnClose() throws Exception {
		final var handlerFromConfigurator = configurator.getAdditionalDecorator(testEndpoint);
		final InvocationHandler decoratedHandler = (proxy, method, args) -> {
			assertNotNull("additional decorator should be executed within a Context",
					ctxTracker.getCurrentContext());
			return handlerFromConfigurator.invoke(proxy, method, args);
		};
		setEndpointProxyHandler(endpointProxy, decoratedHandler);
		super.testOnOpenThenOnClose();
	}



	@Override
	public final void testToStringBeforeOnOpen() throws Exception {
		final var handlerFromConfigurator = configurator.getAdditionalDecorator(testEndpoint);
		final InvocationHandler decoratedHandler = new InvocationHandler() {

			boolean onOpenCalled = false;

			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
				if (args != null) {
					for (var arg: args) {
						if (arg instanceof Session) {
							onOpenCalled = true;
							break;
						}
					}
				}
				if (onOpenCalled) {
					assertNotNull(
						"onOpen(...) and any subsequent method calls should be executed within a "
								+ "Context",
						ctxTracker.getCurrentContext()
					);
				} else {
					assertNull(
						"methods invoked before onOpen(...) should be executed outside of any "
								+ "Context",
						ctxTracker.getCurrentContext()
					);
				}
				return handlerFromConfigurator.invoke(proxy, method, args);
			}
		};
		setEndpointProxyHandler(endpointProxy, decoratedHandler);
		super.testToStringBeforeOnOpen();
	}



	@Override
	public final void testTwoSeparateEndpoints() throws Exception {
		setEndpointProxyHandler(endpointProxy, configurator.getAdditionalDecorator(testEndpoint));
		super.testTwoSeparateEndpoints();
	}

	/**
	 * This method is final to ensure that
	 * {@link pl.morgwai.base.servlet.guice.utils.AnnotatedPingingEndpointDynamicProxyTests} and
	 * {@link pl.morgwai.base.servlet.guice.utils.ProgrammaticPingingEndpointDynamicProxyTests} also
	 * use just a dummy {@link InvocationHandler} not to confuse mockPingerService with a 2nd
	 * connection.
	 */
	@Override
	protected final TestEndpoint createSecondProxy(
		TestEndpoint secondEndpoint,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) throws Exception {
		final var secondProxy = configurator.getProxyClass(secondEndpoint.getClass())
				.getConstructor().newInstance();
		setEndpointProxyHandler(
			secondProxy,
			(proxy, method, args) -> method.invoke(secondEndpoint, args)
		);
		return secondProxy;
	}
}
