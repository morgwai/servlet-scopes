// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import org.junit.*;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;

import com.google.inject.Injector;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator
		.REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY;
import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator.*;



public class GuiceServerEndpointConfiguratorModifyHandshakeTests extends EasyMockSupport {



	static final String MOCK_DEPLOYMENT_PATH = "/mockDeploymentPath";
	static final String NON_PRIMARY_MOCK_DEPLOYMENT_PATH = "/nonPrimaryMockDeploymentPath";
	static final String WEBSOCKET_PATH = "/websocket/mock";

	/** Test subject. */
	final GuiceServerEndpointConfigurator configurator = new GuiceServerEndpointConfigurator();
	/**
	 * At the end of positive tests of {@link GuiceServerEndpointConfigurator#modifyHandshake(
	 * ServerEndpointConfig, HandshakeRequest, HandshakeResponse) modifyHandshake(...)},
	 * {@link GuiceEndpointConfigurator#ctxTracker configurator.ctxTracker} should be set to
	 * this object.
	 */
	final ContextTracker<ContainerCallContext> ctxTracker = new ContextTracker<>();
	/** Returned by {@link #mockConfig}. */
	final Map<String, Object> userProperties = new HashMap<>(1);
	/** Returned by {@link #mockRequest}, overridden at the beginning of some tests. */
	URI requestUri = URI.create("ws://localhost:666" + MOCK_DEPLOYMENT_PATH + WEBSOCKET_PATH);

	ServletContext mockDeployment;
	@Mock ServerEndpointConfig mockConfig;
	@Mock HandshakeRequest mockRequest;
	@Mock HandshakeResponse mockResponse;
	@Mock HttpSession mockSession;
	@Mock Injector mockInjector;



	@Before
	public void setupMocks() {
		injectMocks(this);

		expect(mockConfig.getPath())
			.andReturn(WEBSOCKET_PATH)
			.anyTimes();
		expect(mockConfig.getUserProperties())
			.andReturn(userProperties)
			.anyTimes();

		expect(mockRequest.getRequestURI())
			.andAnswer(() -> requestUri)
			.anyTimes();

		mockDeployment = new StandaloneWebsocketContainerServletContext(
			MOCK_DEPLOYMENT_PATH,
			"mockApp"
		) {
			@Override public ServletContext getContext(String path) {
				if (
					path.equals(MOCK_DEPLOYMENT_PATH)
					|| path.equals(NON_PRIMARY_MOCK_DEPLOYMENT_PATH)
				) {
					return this;
				}
				return null;
			}
		};
		mockDeployment.setAttribute(Injector.class.getName(), mockInjector);
		expect(mockInjector.getInstance(ServletWebsocketModule.CTX_TRACKER_KEY))
			.andReturn(ctxTracker)
			.anyTimes();
		expect(mockInjector.getInstance(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY))
			.andReturn(false)
			.anyTimes();
	}

	@After
	public void verifyMocks() {
		verifyAll();
	}

	/**
	 * Called manually at the end of positive tests of
	 * {@link GuiceServerEndpointConfigurator#modifyHandshake(
	 * ServerEndpointConfig, HandshakeRequest, HandshakeResponse) modifyHandshake(...)}.
	 */
	void verifyInitialization() {
		assertSame("ctxTracker should be properly initialized",
				ctxTracker, configurator.backingConfigurator.ctxTracker);
	}



	@Test
	public void testFindBySession() {
		expect(mockRequest.getHttpSession())
			.andReturn(mockSession)
			.anyTimes();
		expect(mockSession.getServletContext())
			.andReturn(mockDeployment)
			.anyTimes();
		replayAll();

		configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
		verifyInitialization();
		assertSame("HttpSession should be stored into userProperties",
				mockSession, userProperties.get(HttpSession.class.getName()));
	}



	@Test
	public void testFindByPath() {
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		GuiceServerEndpointConfigurator.registerDeployment(mockDeployment, mockInjector);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			verifyInitialization();
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(mockDeployment);
		}
	}



	@Test
	public void testFindByNonPrimaryPath() {
		requestUri = URI.create(
				"ws://localhost:666" + NON_PRIMARY_MOCK_DEPLOYMENT_PATH + WEBSOCKET_PATH);
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		final var leakedDeploymentPath = "/leaked1";
		GuiceServerEndpointConfigurator.appDeployments.put(  // test skipping leaked deployments
				leakedDeploymentPath, new WeakReference<>(null));
		GuiceServerEndpointConfigurator.registerDeployment(mockDeployment, mockInjector);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			verifyInitialization();
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(mockDeployment);
			GuiceServerEndpointConfigurator.appDeployments.remove(leakedDeploymentPath);
		}
	}



	@Test
	public void testFindByNonPrimaryPathSecondDeploymentHasRestrictedAccess() {
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		mockDeployment.setAttribute(Injector.class.getName(), mockInjector);
		final var secondDeployment = new StandaloneWebsocketContainerServletContext(
			"/secondDeploymentPath",
			"secondApp"
		);
		GuiceServerEndpointConfigurator.registerDeployment(secondDeployment, null);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			fail("RuntimeException expected");
		} catch (RuntimeException expectedException) {
			assertEquals(
				"expectedException message should be DEPLOYMENT_NOT_FOUND_MESSAGE",
				String.format(
					DEPLOYMENT_NOT_FOUND_MESSAGE,
					MOCK_DEPLOYMENT_PATH + WEBSOCKET_PATH,
					'"' + MOCK_DEPLOYMENT_PATH + '"'
				),
				expectedException.getMessage()
			);
			assertNull("backingConfigurator should NOT be initialized",
					configurator.backingConfigurator);
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(secondDeployment);
		}
	}



	static final Logger log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
	static Level levelBackup;

	@BeforeClass
	public static void suppressErrorLogs() {
		levelBackup = log.getLevel();
		log.setLevel(Level.OFF);
	}

	@AfterClass
	public static void restoreLogLevel() {
		log.setLevel(levelBackup);
	}
}
