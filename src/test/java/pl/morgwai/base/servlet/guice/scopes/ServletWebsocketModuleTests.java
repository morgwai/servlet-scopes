// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.*;

import com.google.inject.*;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;



public class ServletWebsocketModuleTests extends EasyMockSupport {



	final ServletContext testDeployment = new StandaloneWebsocketContainerServletContext("/test");
	final WebsocketModule websocketModule = new WebsocketModule();
	final ContextTracker<ContainerCallContext> ctxTracker =
			websocketModule.containerCallScope.tracker;
	final ServletWebsocketModule servletModule =
			new ServletWebsocketModule(testDeployment, websocketModule);
	final Injector injector = Guice.createInjector(servletModule);

	@Mock HttpSession mockHttpSession;
	@Mock HttpServletRequest mockServletRequest;
	@Mock Session mockWebsocketConnection;
	final Map<String, Object> userProperties = new HashMap<>(2);
	final HttpSessionContext[] httpSessionContextHolder = {null};



	@Before
	public final void setup() {
		injectMocks(this);
		expect(mockWebsocketConnection.getUserProperties())
			.andReturn(userProperties)
			.anyTimes();
		expect(mockServletRequest.getSession())
			.andReturn(mockHttpSession)
			.anyTimes();
		expect(mockHttpSession.getServletContext())
			.andReturn(testDeployment)
			.anyTimes();
		expect(mockHttpSession.getAttribute(HttpSessionContext.class.getName()))
			.andAnswer(() -> httpSessionContextHolder[0])
			.anyTimes();
	}

	@After
	public final void verifyMocks() {
		verifyAll();
	}



	@Test
	public void testServletContextProviders() {
		replayAll();
		httpSessionContextHolder[0] = new HttpSessionContext(mockHttpSession);
		final var testRequestCtx = new ServletRequestContext(
				mockServletRequest, injector.getInstance(WebsocketModule.CTX_TRACKER_KEY));

		testRequestCtx.executeWithinSelf(() ->{
			assertSame("enclosing testRequestCtx should be provided",
					testRequestCtx, injector.getInstance(ContainerCallContext.class));
			assertSame("HttpSessionContext associated with the enclosing testRequestCtx should be "
							+ "provided",
					httpSessionContextHolder[0], injector.getInstance(HttpSessionContext.class));
			try {
				injector.getInstance(WebsocketConnectionContext.class);
				fail("providing a WebsocketConnectionContext withing a ServletRequestContext should"
						+ " throw an ProvisionException");
			} catch (ProvisionException expected) {
				assertTrue("expected ProvisionException should be caused be an OutOfScopeException",
						expected.getCause() instanceof OutOfScopeException);
				assertSame(
					"error message should be the one explaining Scope mismatch",
					WebsocketModule.WS_CONNECTION_CTX_WITHIN_SERVLET_CTX_MESSAGE,
					expected.getCause().getMessage()
				);
			}
		});
	}



	void testWebsocketContextProviders(boolean httpSessionPresent) {
		replayAll();
		httpSessionContextHolder[0] = new HttpSessionContext(mockHttpSession);
		if (httpSessionPresent) userProperties.put(HttpSession.class.getName(), mockHttpSession);
		final var connectionProxy =
				new WebsocketConnectionProxy(mockWebsocketConnection, ctxTracker);
		final var connectionCtx = new WebsocketConnectionContext(connectionProxy);
		final var testEventCtx = new WebsocketEventContext(
			connectionCtx,
			httpSessionPresent ? mockHttpSession : null,
			ctxTracker
		);

		testEventCtx.executeWithinSelf(() ->{
			assertSame("enclosing testEventCtx should be provided",
					testEventCtx, injector.getInstance(ContainerCallContext.class));
			assertSame("connectionCtx associated with the enclosing testEventCtx should be "
							+ "provided",
					connectionCtx, injector.getInstance(WebsocketConnectionContext.class));
			if (httpSessionPresent) {
				assertSame(
					"HttpSessionContext associated with the enclosing testRequestCtx should be "
							+ "provided",
					httpSessionContextHolder[0],
					injector.getInstance(HttpSessionContext.class)
				);
			} else {
				try {
					injector.getInstance(HttpSessionContext.class);
					fail("providing a HttpSessionContext when there's no any HttpSession should"
							+ " throw an ProvisionException");
				} catch (ProvisionException expected) {
					assertTrue("expected Exception should be caused be an OutOfScopeException",
							expected.getCause() instanceof OutOfScopeException);
					assertSame(
						"error message should be the one explaining missing HttpSession",
						ContainerCallContext.NO_HTTP_SESSION_MESSAGE,
						expected.getCause().getMessage()
					);
				}
			}
		});
	}

	@Test
	public void testWebsocketContextProvidersNoHttpSession() {
		testWebsocketContextProviders(false);
	}

	@Test
	public void testWebsocketContextProvidersHttpSessionPresent() {
		testWebsocketContextProviders(true);
	}
}
