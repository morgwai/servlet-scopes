// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import javax.servlet.http.HttpSession;
import javax.websocket.*;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.ClientEndpointProxy;
import pl.morgwai.base.servlet.guice.scopes.ContainerCallContext;
import pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator.RttObserver;
import pl.morgwai.base.servlet.utils.WebsocketPingerService;



/**
 * Context-aware proxy for client {@link Endpoint}s that additionally automatically registers and
 * unregisters {@code Endpoints} to the passed {@link WebsocketPingerService}.
 * See {@link ClientEndpointProxy} for usage instructions.
 */
public class PingingClientEndpointProxy extends ClientEndpointProxy {



	final WebsocketPingerService pingerService;



	public PingingClientEndpointProxy(
		Endpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		WebsocketPingerService pingerService,
		HttpSession httpSession
	) {
		super(toWrap, ctxTracker, httpSession);
		this.pingerService = pingerService;
	}

	public PingingClientEndpointProxy(
		Endpoint toWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		WebsocketPingerService pingerService
	) {
		super(toWrap, ctxTracker);
		this.pingerService = pingerService;
	}



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		super.onOpen(connection, config);
		if (wrappedEndpoint instanceof RttObserver) {
			pingerService.addConnection(
				connectionProxy,
				(connection2, rttNanos) -> ((RttObserver) wrappedEndpoint).onPong(rttNanos)
			);
		} else {
			pingerService.addConnection(connectionProxy);
		}
	}



	@Override
	public void onClose(Session connection, CloseReason closeReason) {
		pingerService.removeConnection(connectionProxy);
		super.onClose(connection, closeReason);
	}



	@Override
	public String toString() {
		return "PingingClientEndpointProxy { wrappedEndpoint = " + wrappedEndpoint + " }";
	}
}
