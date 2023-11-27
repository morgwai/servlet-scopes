// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.tyrusserver;

import java.util.Map;

import javax.websocket.DeploymentException;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.glassfish.tyrus.core.cluster.ClusterContext;
import pl.morgwai.base.servlet.guice.scopes.GuiceServerEndpointConfigurator;
import pl.morgwai.base.servlet.guice.scopes.ServletModule;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;



public class TyrusServer extends org.glassfish.tyrus.server.Server {



	public static final String PATH = "/websocket";



	public TyrusServer(int port, ClusterContext clusterCtx) throws DeploymentException {
		super(
			"localhost",
			port,
			PATH,
			Map.of(ClusterContext.CLUSTER_CONTEXT, clusterCtx),
			BroadcastEndpoint.class
		);
		start();
	}
}
