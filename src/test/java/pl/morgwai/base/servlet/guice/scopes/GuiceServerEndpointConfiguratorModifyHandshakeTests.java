// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.ref.WeakReference;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;

import com.google.inject.Injector;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.*;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;

import static org.easymock.EasyMock.expect;
import static org.junit.Assert.*;
import static pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator.*;



public class GuiceServerEndpointConfiguratorModifyHandshakeTests extends EasyMockSupport {



	static final String MOCK_DEPLOYMENT_PATH = "/mockDeploymentPath";
	static final String NON_PRIMARY_MOCK_DEPLOYMENT_PATH = "/nonPrimaryMockDeploymentPath";
	static final String SECOND_DEPLOYMENT_PATH = "/secondDeploymentPath";
	static final String WEBSOCKET_PATH = "/websocket/mock";

	/** Test subject. */
	final GuiceServerEndpointConfigurator configurator = new GuiceServerEndpointConfigurator();
	/**
	 * At the end of positive tests of {@link GuiceServerEndpointConfigurator#modifyHandshake(
	 * ServerEndpointConfig, HandshakeRequest, HandshakeResponse) modifyHandshake(...)},
	 * {@link GuiceServerEndpointConfigurator#ctxTracker configurator.ctxTracker} should be set to
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
		expect(mockInjector.getInstance(ServletModule.containerCallContextTrackerKey))
			.andReturn(ctxTracker)
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
				ctxTracker, configurator.ctxTracker);
	}



	@Test
	public void testModifyHandshakeFindBySession() {
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
	public void testModifyHandshakeFindByPath() {
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		GuiceServerEndpointConfigurator.registerDeployment(mockDeployment);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			verifyInitialization();
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(mockDeployment);
		}
	}



	@Test
	public void testModifyHandshakeFindByNonPrimaryPath() {
		requestUri = URI.create(
				"ws://localhost:666" + NON_PRIMARY_MOCK_DEPLOYMENT_PATH + WEBSOCKET_PATH);
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		GuiceServerEndpointConfigurator.registerDeployment(mockDeployment);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			verifyInitialization();
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(mockDeployment);
		}
	}



	@Test
	public void testModifyHandshakeFindUnregisteredDeploymentViaSecondDeployment() {
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		final var secondDeployment = new StandaloneWebsocketContainerServletContext(
			SECOND_DEPLOYMENT_PATH,
			"secondApp"
		) {
			@Override public ServletContext getContext(String path) {
				if (
					path.equals(MOCK_DEPLOYMENT_PATH)
					|| path.equals(NON_PRIMARY_MOCK_DEPLOYMENT_PATH)
				) {
					return mockDeployment;
				}
				if (path.equals(getContextPath())) return this;
				return null;
			}
		};
		final var leakedDeploymentPath = "/leaked1";  // must be placed at the beginning of the Map
		GuiceServerEndpointConfigurator.appDeployments.put(  // test skipping leaked deployments
				leakedDeploymentPath, new WeakReference<>(null));
		GuiceServerEndpointConfigurator.registerDeployment(secondDeployment);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			verifyInitialization();
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(secondDeployment);
			GuiceServerEndpointConfigurator.appDeployments.remove(leakedDeploymentPath);
		}
	}



	@Test
	public void testModifyHandshakeUnregisteredDeploymentSecondDeploymentHasRestrictedAccess() {
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		final var secondDeployment = new StandaloneWebsocketContainerServletContext(
			SECOND_DEPLOYMENT_PATH,
			"secondApp"
		) {
			@Override public ServletContext getContext(String path) {
				return null;
			}
		};
		GuiceServerEndpointConfigurator.registerDeployment(secondDeployment);
		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			fail("RuntimeException expected");
		} catch (RuntimeException expectedException) {
			assertEquals(
				"expectedException message should be DEPLOYMENT_NOT_FOUND_MESSAGE",
				String.format(
					DEPLOYMENT_NOT_FOUND_MESSAGE,
					MOCK_DEPLOYMENT_PATH + WEBSOCKET_PATH,
					MOCK_DEPLOYMENT_PATH
				),
				expectedException.getMessage()
			);
			assertNull("ctxTracker should NOT be initialized",
					configurator.ctxTracker);
		} finally {
			GuiceServerEndpointConfigurator.deregisterDeployment(secondDeployment);
		}
	}



	@Test
	public void testModifyHandshakeNoRegisteredDeployments() {
		expect(mockRequest.getHttpSession())
			.andReturn(null)
			.anyTimes();
		replayAll();

		try {
			configurator.modifyHandshake(mockConfig, mockRequest, mockResponse);
			fail("RuntimeException expected");
		} catch (RuntimeException expectedException) {
			assertEquals(
				"expectedException message should be DEPLOYMENT_NOT_FOUND_MESSAGE",
				String.format(
					DEPLOYMENT_NOT_FOUND_MESSAGE,
					MOCK_DEPLOYMENT_PATH + WEBSOCKET_PATH,
					MOCK_DEPLOYMENT_PATH
				),
				expectedException.getMessage()
			);
			assertNull("ctxTracker should NOT be initialized",
					configurator.ctxTracker);
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
