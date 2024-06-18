// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.websocket.DeploymentException;

import org.junit.Before;
import org.junit.Test;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;

import static org.junit.Assert.*;



public abstract class MultiAppWebsocketTests extends WebsocketIntegrationTests {



	String secondAppWebsocketUrl;
	String unregisteredDeploymentAppWebsocketUrl;



	@Before
	public void setupSecondApp() {
		final var multiAppServer = (MultiAppServer) server;
		secondAppWebsocketUrl = multiAppServer.getSecondAppWebsocketUrl();
		unregisteredDeploymentAppWebsocketUrl =
				multiAppServer.getUnregisteredDeploymentAppWebsocketUrl();
	}



	@Override
	protected abstract MultiAppServer createServer() throws Exception;



	@Test
	public void testProgrammaticEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + ProgrammaticEndpoint.PATH,
			true
		);
	}

	@Test
	public void testProgrammaticEndpointOnUnregisteredDeploymentApp() throws Exception {
		test2SessionsWithServerEndpoint(
			unregisteredDeploymentAppWebsocketUrl + ProgrammaticEndpoint.PATH,
			true
		);
	}

	@Test
	public void testAnnotatedEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + AnnotatedEndpoint.PATH,
			true
		);
	}

	@Test
	public void testAnnotatedEndpointOnUnregisteredDeploymentApp() throws Exception {
		test2SessionsWithServerEndpoint(
			unregisteredDeploymentAppWebsocketUrl + AnnotatedEndpoint.PATH,
			true
		);
	}

	@Test
	public void testRttReportingEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + RttReportingEndpoint.PATH,
			false
		);
	}

	@Test
	public void testRttReportingEndpointOnUnregisteredDeploymentApp() throws Exception {
		test2SessionsWithServerEndpoint(
			unregisteredDeploymentAppWebsocketUrl + RttReportingEndpoint.PATH,
			false
		);
	}

	@Test
	public void testAnnotatedExtendingEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + AnnotatedExtendingEndpoint.PATH,
			true
		);
	}

	@Test
	public void testAnnotatedExtendingEndpointOnUnregisteredDeploymentApp() throws Exception {
		test2SessionsWithServerEndpoint(
			unregisteredDeploymentAppWebsocketUrl + AnnotatedExtendingEndpoint.PATH,
			true
		);
	}



	public void testAppSeparation(String... websocketUrls)
			throws InterruptedException, DeploymentException, IOException {
		final List<String> responses = new ArrayList<>(websocketUrls.length);
		for (var url: websocketUrls) {
			final var endpoint = new ClientEndpoint(responses::add, null, null);
			final var uri = URI.create(url);
			try (
				final var ignored = clientWebsocketContainer.connectToServer(endpoint, null, uri);
			) {
				assertTrue("endpoint should be closed",
						endpoint.awaitClosure(500L, TimeUnit.MILLISECONDS));
			}
		}
		assertEquals("responses from all URLs should be received",
				websocketUrls.length, responses.size());
		final Set<String> distinctResponses = new HashSet<>(websocketUrls.length);
		distinctResponses.addAll(responses);
		assertEquals("Endpoint Configurators of separate apps should have distinct Injectors",
				websocketUrls.length, distinctResponses.size());
	}

	@Test
	public void testAppSeparation() throws InterruptedException, DeploymentException, IOException {
		testAppSeparation(
			appWebsocketUrl + AppSeparationTestEndpoint.PATH,
			secondAppWebsocketUrl + AppSeparationTestEndpoint.PATH,
			unregisteredDeploymentAppWebsocketUrl + AppSeparationTestEndpoint.PATH
		);
	}

	@Test
	public void testAppSeparationNoSession()
			throws InterruptedException, DeploymentException, IOException {
		final var log = Logger.getLogger(GuiceServerEndpointConfigurator.class.getName());
		final var levelBackup = log.getLevel();
		log.setLevel(Level.OFF);
		try {
			testAppSeparation(
				appWebsocketUrl + NoSessionAppSeparationTestEndpoint.PATH,
				secondAppWebsocketUrl + NoSessionAppSeparationTestEndpoint.PATH,
				unregisteredDeploymentAppWebsocketUrl + NoSessionAppSeparationTestEndpoint.PATH
			);
		} finally {
			log.setLevel(levelBackup);
		}
	}
}
