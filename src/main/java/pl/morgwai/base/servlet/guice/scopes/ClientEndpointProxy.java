// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.net.URI;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context-aware proxy for client {@link Endpoint}s.
 * Executes lifecycle methods of wrapped {@link Endpoint}s and of their registered
 * {@link MessageHandler}s within websocket {@code Contexts}.
 * <p>
 * <b>Usage:</b><br/>
 * Create an instance of your client {@link Endpoint}, then
 * {@link #ClientEndpointProxy(Endpoint, ContextTracker) create a Proxy for it} and pass it to
 * {@link WebSocketContainer#connectToServer(Endpoint, ClientEndpointConfig, URI)} method.</p>
 */
public class ClientEndpointProxy extends Endpoint {



	protected final Endpoint wrappedEndpoint;
	final ContextTracker<ContainerCallContext> ctxTracker;
	final WebsocketConnectionContext enclosingCtx;
	final HttpSession httpSession;



	public ClientEndpointProxy(
		Endpoint endpointToWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		WebsocketConnectionContext enclosingCtx,
		HttpSession httpSession
	) {
		this.wrappedEndpoint = endpointToWrap;
		this.ctxTracker = ctxTracker;
		this.enclosingCtx = enclosingCtx;
		this.httpSession = httpSession;
	}

	public ClientEndpointProxy(
		Endpoint endpointToWrap,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		this(endpointToWrap, ctxTracker, null, null);
	}



	protected WebsocketConnectionProxy connectionProxy;
	WebsocketConnectionContext connectionCtx;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		if (httpSession != null) {
			connection.getUserProperties().put(HttpSession.class.getName(), httpSession);
		}
		connectionProxy = new WebsocketConnectionProxy(connection, ctxTracker);
		connectionCtx = new WebsocketConnectionContext(connectionProxy);
		new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
				() -> wrappedEndpoint.onOpen(connectionProxy, config));
	}



	@Override
	public void onClose(Session connection, CloseReason closeReason) {
		new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
				() -> wrappedEndpoint.onClose(connectionProxy, closeReason));
	}



	@Override
	public void onError(Session connection, Throwable error) {
		new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
				() -> wrappedEndpoint.onError(connectionProxy, error));
	}



	@Override
	public String toString() {
		return "ClientEndpointProxy { wrappedEndpoint = " + wrappedEndpoint + " }";
	}
}
