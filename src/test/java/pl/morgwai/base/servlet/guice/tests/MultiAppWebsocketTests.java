// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.DeploymentException;
import org.junit.Before;
import org.junit.Test;

import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.tests.servercommon.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;



public abstract class MultiAppWebsocketTests extends WebsocketIntegrationTests {



	String secondAppWebsocketUrl;



	@Before
	public void setupSecondApp() {
		final var multiAppServer = (MultiAppServer) server;
		secondAppWebsocketUrl = multiAppServer.getSecondAppWebsocketUrl();
	}



	@Override
	protected abstract MultiAppServer createServer(String testName) throws Exception;



	@Test
	public void testProgrammaticEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + ProgrammaticEndpoint.PATH,
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
	public void testRttReportingEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + RttReportingEndpoint.PATH,
			false
		);
	}

	@Test
	public void testAnnotatedExtendingEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + AnnotatedExtendingProgrammaticEndpoint.PATH,
			true
		);
	}



	public void testAppSeparation(String... websocketUrls)
			throws InterruptedException, DeploymentException, IOException {
		final List<String> responses = new ArrayList<>(websocketUrls.length);
		for (var url: websocketUrls) {
			final var clientEndpoint = new PluggableClientEndpoint(responses::add, null, null);
			final var uri = URI.create(url);
			try (
				final var ignored =
						clientWebsocketContainer.connectToServer(clientEndpoint, null, uri);
			) {
				assertTrue("clientEndpoint should be closed",
						clientEndpoint.awaitClosure(500L, MILLISECONDS));
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
			secondAppWebsocketUrl + AppSeparationTestEndpoint.PATH
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
				secondAppWebsocketUrl + NoSessionAppSeparationTestEndpoint.PATH
			);
		} finally {
			log.setLevel(levelBackup);
		}
	}
}
