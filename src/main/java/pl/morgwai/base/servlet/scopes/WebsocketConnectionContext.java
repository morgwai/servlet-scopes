// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.*;



/**
 * Context of a websocket connection ({@link javax.websocket.Session}).
 * <p>
 * Each instance is associated with a given endpoint instance.
 * Specifically, all methods of the associated endpoint annotated with one of the websocket
 * annotations ({@link javax.websocket.OnOpen @OnOpen},
 * {@link javax.websocket.OnMessage @OnMessage}, {@link javax.websocket.OnError @OnError} and
 * {@link javax.websocket.OnClose @OnClose}), or overriding those of
 * {@link javax.websocket.Endpoint} or of registered {@link javax.websocket.MessageHandler}s, are
 * executed within the same {@code WebsocketConnectionContext} instance.</p>
 *
 * @see ServletModule#websocketConnectionScope corresponding <code>Scope</code>
 */
public class WebsocketConnectionContext extends InjectionContext {



	final WebsocketConnectionWrapper connection;
	public Session getConnection() { return connection; }



	WebsocketConnectionContext(WebsocketConnectionWrapper connection) {
		this.connection = connection;
		connection.setConnectionCtx(this);
	}
}
