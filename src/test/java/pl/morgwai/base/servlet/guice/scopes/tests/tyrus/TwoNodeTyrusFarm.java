// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.tyrus;

import jakarta.websocket.DeploymentException;

import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.MultiAppServer;



public class TwoNodeTyrusFarm implements MultiAppServer {



	TyrusServer node1;
	TyrusServer node2;



	public TwoNodeTyrusFarm(int port1, int port2, String path1, String path2)
			throws DeploymentException {
		node1 = new TyrusServer(port1, path1);
		node2 = new TyrusServer(port2, path2);
	}



	@Override
	public String getAppWebsocketUrl() {
		return node1.getAppWebsocketUrl();
	}



	@Override
	public String getSecondAppWebsocketUrl() {
		return node2.getAppWebsocketUrl();
	}



	@Override
	public void shutdown() {
		node1.shutdown();
		node2.shutdown();
	}
}
