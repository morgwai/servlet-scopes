// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus;

import java.io.IOException;

import javax.websocket.DeploymentException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.*;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServletModule;
import pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus.server.InMemoryClusterContext;
import pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus.server.ServerNode;
import pl.morgwai.base.servlet.guice.scopes.tests.WebsocketBroadcastingTests;
import pl.morgwai.base.servlet.guice.scopes.tests.WebsocketTestBase;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.BroadcastEndpoint;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;



public class TyrusClusteringTests extends WebsocketTestBase {



	static final String URL_PREFIX = "ws://localhost:";

	StandaloneWebsocketContainerServletContext appDeployment;
	ServerNode node1;
	ServerNode node2;



	@Before
	public void setup() throws DeploymentException {
		appDeployment  = new StandaloneWebsocketContainerServletContext(ServerNode.PATH);
		final var servletModule = new ServletModule(appDeployment);
		final var injector = Guice.createInjector(servletModule);
		appDeployment.setAttribute(Injector.class.getName(), injector);
		GuiceServerEndpointConfigurator.registerDeployment(appDeployment);

		final var clusterCtx1 = new InMemoryClusterContext(1);
		final var clusterCtx2 = new InMemoryClusterContext(2);
		node1 = new ServerNode(-1, clusterCtx1);
		node2 = new ServerNode(-1, clusterCtx2);
	}



	@After
	public void shutdown() {
		node1.stop();
		node2.stop();
		GuiceServerEndpointConfigurator.deregisterDeployment(appDeployment);
	}



	@Test
	public void testBroadcast() throws DeploymentException, IOException, InterruptedException {
		final var port1 = node1.getPort();
		final var port2 = node2.getPort();
		final var url1 = URL_PREFIX + port1 + ServerNode.PATH + BroadcastEndpoint.PATH;
		final var url2 = URL_PREFIX + port2 + ServerNode.PATH + BroadcastEndpoint.PATH;
		WebsocketBroadcastingTests.testBroadcast(clientWebsocketContainer, url1, url2, url1);
	}
}
