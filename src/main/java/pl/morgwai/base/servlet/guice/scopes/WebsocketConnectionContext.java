// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.websocket.Session;

import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of a websocket connection ({@link jakarta.websocket.Session}).
 * <p>
 * Each instance is associated with a given endpoint instance.
 * Specifically, all methods of the associated endpoint annotated with one of the websocket
 * annotations ({@link jakarta.websocket.OnOpen @OnOpen},
 * {@link jakarta.websocket.OnMessage @OnMessage}, {@link jakarta.websocket.OnError @OnError} and
 * {@link jakarta.websocket.OnClose @OnClose}), or overriding those of
 * {@link jakarta.websocket.Endpoint} or of registered {@link jakarta.websocket.MessageHandler}s, are
 * executed within the same {@code WebsocketConnectionContext} instance.</p>
 * <p>
 * Instances are stored in {@link Session#getUserProperties() user properites} under
 * {@link Class#getName() fully-qualified name} of {@code WebsocketConnectionContext} class.</p>
 * @see ServletModule#websocketConnectionScope corresponding Scope
 */
public class WebsocketConnectionContext extends InjectionContext {



	/**
	 * Set by {@link #WebsocketConnectionContext(WebsocketConnectionProxy) constructor} and by
	 * {@link WebsocketConnectionProxy#getOpenSessions()} when remote connections from other cluster
	 * nodes are fetched.
	 */
	transient WebsocketConnectionProxy connectionProxy;



	public Session getConnection() {
		return connectionProxy;
	}



	WebsocketConnectionContext(WebsocketConnectionProxy connectionProxy) {
		this.connectionProxy = connectionProxy;
		connectionProxy.setConnectionCtx(this);
	}



	private static final long serialVersionUID = -3426641069769956104L;
}
