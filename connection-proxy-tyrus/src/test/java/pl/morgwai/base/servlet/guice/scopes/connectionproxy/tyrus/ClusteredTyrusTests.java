// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus;

import java.io.IOException;

import javax.websocket.DeploymentException;

import pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus.server.InMemoryClusterContext;
import pl.morgwai.base.servlet.guice.scopes.tests.TyrusTests;
import pl.morgwai.base.servlet.guice.scopes.tests.WebsocketBroadcastingTests;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.BroadcastEndpoint;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.scopes.tests.tyrus.TyrusServer;



public class ClusteredTyrusTests extends TyrusTests {



	TyrusServer node1;
	TyrusServer node2;



	@Override
	protected Server createServer() throws Exception {
		appDeployment = TyrusServer.createDeployment(Server.APP_PATH);
		final var clusterCtx1 = new InMemoryClusterContext(1);
		final var clusterCtx2 = new InMemoryClusterContext(2);
		node1 = new TyrusServer(-1, Server.APP_PATH, clusterCtx1);
		node2 = new TyrusServer(-1, Server.APP_PATH, clusterCtx2);
		return node1;
	}



	@Override
	public void cleanupDeployment() {
		node2.shutdown();
		super.cleanupDeployment();
	}



	@Override
	public void testBroadcast() throws DeploymentException, IOException, InterruptedException {
		final var url1 = appWebsocketUrl + BroadcastEndpoint.PATH;
		final var url2 = node2.getAppWebsocketUrl() + BroadcastEndpoint.PATH;
		WebsocketBroadcastingTests.testBroadcast(clientWebsocketContainer, url1, url2, url1);
	}
}
