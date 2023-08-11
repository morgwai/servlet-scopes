// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of a single websocket event such as a connection creation/closure, a message arrival or
 * an error occurrence.
 * <p>
 * Each instance is associated with a single invocation of some endpoint life-cycle or
 * {@link javax.websocket.MessageHandler} method.
 * Specifically, all methods annotated with one of the websocket
 * annotations ({@link javax.websocket.OnOpen @OnOpen},
 * {@link javax.websocket.OnMessage @OnMessage}, {@link javax.websocket.OnError @OnError} and
 * {@link javax.websocket.OnClose @OnClose}), or overriding those of
 * {@link javax.websocket.Endpoint} or {@link javax.websocket.MessageHandler}s, are
 * executed within a separate new {@code WebsocketEventContext} instance.</p>
 * @see ContainerCallContext super class for more info
 */
public class WebsocketEventContext extends ContainerCallContext {



	public WebsocketConnectionContext getConnectionContext() { return connectionContext; }
	final WebsocketConnectionContext connectionContext;

	@Override public HttpSession getHttpSession() { return httpSession; }
	final HttpSession httpSession;



	WebsocketEventContext(
		WebsocketConnectionContext connectionContext,
		HttpSession httpSession,
		ContextTracker<ContainerCallContext> tracker
	) {
		super(tracker);
		this.connectionContext = connectionContext;
		this.httpSession = httpSession;
	}
}
