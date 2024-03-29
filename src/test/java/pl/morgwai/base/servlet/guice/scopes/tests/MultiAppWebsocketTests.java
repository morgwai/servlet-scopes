// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import javax.websocket.DeploymentException;

import org.junit.Before;
import org.junit.Test;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.*;

import static org.junit.Assert.*;



public abstract class MultiAppWebsocketTests extends WebsocketIntegrationTests {



	String secondAppWebsocketUrl;



	@Before
	public void setupSecondApp() {
		secondAppWebsocketUrl = ((MultiAppServer) server).getSecondAppWebsocketUrl();
	}



	@Override
	protected abstract MultiAppServer createServer() throws Exception;



	@Test
	public void testProgrammaticEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(secondAppWebsocketUrl + ProgrammaticEndpoint.PATH, true);
	}

	@Test
	public void testAnnotatedEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(secondAppWebsocketUrl + AnnotatedEndpoint.PATH, true);
	}

	@Test
	public void testRttReportingEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(secondAppWebsocketUrl + RttReportingEndpoint.PATH, false);
	}

	@Test
	public void testAnnotatedExtendingEndpointOnSecondApp() throws Exception {
		test2SessionsWithServerEndpoint(
			secondAppWebsocketUrl + AnnotatedExtendingEndpoint.PATH,
			true
		);
	}



	public void testAppSeparation(String testAppWebsocketUrl, String secondAppWebsocketUrl)
			throws InterruptedException, DeploymentException, IOException {
		final var messages = new ArrayList<String>(2);

		final var endpoint1 = new ClientEndpoint(messages::add, null, null);
		final var uri1 = URI.create(testAppWebsocketUrl);
		try (
			final var ignored = clientWebsocketContainer.connectToServer(endpoint1, null, uri1);
		) {
			assertTrue("endpoint1 should be closed",
					endpoint1.awaitClosure(500L, TimeUnit.MILLISECONDS));
		}

		final var endpoint2 = new ClientEndpoint(messages::add, null, null);
		final var uri2 = URI.create(secondAppWebsocketUrl);
		try (
			final var ignored = clientWebsocketContainer.connectToServer(endpoint2, null, uri2);
		) {
			assertTrue("endpoint2 should be closed",
					endpoint2.awaitClosure(500L, TimeUnit.MILLISECONDS));
		}

		assertNotEquals("Endpoint Configurators of separate apps should have separate Injectors",
				messages.get(0), messages.get(1));
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
		testAppSeparation(
			appWebsocketUrl + NoSessionAppSeparationTestEndpoint.PATH,
			secondAppWebsocketUrl + NoSessionAppSeparationTestEndpoint.PATH
		);
	}
}
