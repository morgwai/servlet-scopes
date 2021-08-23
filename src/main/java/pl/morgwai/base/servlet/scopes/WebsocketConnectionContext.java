// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a websocket connection ({@link javax.websocket.Session}).
 * <p>
 * A single instance has its lifetime coupled with a given endpoint instance. Specifically, all
 * calls to given endpoint's annotated methods (from {@link javax.websocket.OnOpen @OnOpen},
 * across all calls to {@link javax.websocket.OnMessage @OnMessage} and
 * {@link javax.websocket.OnError @OnError} until and including
 * {@link javax.websocket.OnClose @OnClose} or methods overriding those of
 * {@link javax.websocket.Endpoint} together with methods of registered
 * {@link javax.websocket.MessageHandler}s are executed within a single
 * <code>WebsocketConnectionContext</code>.</p>
 *
 * @see ServletModule#websocketConnectionScope corresponding <code>Scope</code>
 */
public class WebsocketConnectionContext extends ServerSideContext<WebsocketConnectionContext> {



	final WebsocketConnectionWrapper connection;
	public Session getConnection() { return connection; }



	protected WebsocketConnectionContext(
		WebsocketConnectionWrapper connection,
		ContextTracker<WebsocketConnectionContext> tracker
	) {
		super(tracker);
		this.connection = connection;
		connection.setConnectionCtx(this);
	}
}
