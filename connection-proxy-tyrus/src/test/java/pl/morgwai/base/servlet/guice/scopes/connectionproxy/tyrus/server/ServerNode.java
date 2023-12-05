// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus.server;

import java.util.Map;

import javax.websocket.DeploymentException;

import org.glassfish.tyrus.core.cluster.ClusterContext;



public class ServerNode extends org.glassfish.tyrus.server.Server {



	public static final String PATH = "/websocket";



	public ServerNode(int port, ClusterContext clusterCtx) throws DeploymentException {
		super(
			"localhost",
			port,
			PATH,
			clusterCtx == null ? null : Map.of(ClusterContext.CLUSTER_CONTEXT, clusterCtx),
			BroadcastEndpoint.class
		);
		start();
	}
}
