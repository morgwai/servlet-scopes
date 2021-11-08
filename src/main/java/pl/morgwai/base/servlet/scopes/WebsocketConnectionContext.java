// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import jakarta.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



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
 *
 * @see ServletModule#websocketConnectionScope corresponding <code>Scope</code>
 */
public class WebsocketConnectionContext extends TrackableContext<WebsocketConnectionContext> {



	final WebsocketConnectionWrapper connection;
	public Session getConnection() { return connection; }



	WebsocketConnectionContext(
		WebsocketConnectionWrapper connection,
		ContextTracker<WebsocketConnectionContext> tracker
	) {
		super(tracker);
		this.connection = connection;
		connection.setConnectionCtx(this);
	}
}
