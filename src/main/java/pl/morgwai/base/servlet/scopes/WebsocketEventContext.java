// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of a single websocket event such as connection creation/closure, message arrival or
 * error occurrence.
 * <p>
 * Each instance is coupled with a single invocation of some endpoint life-cycle or
 * {@link javax.websocket.MessageHandler} method (either annotated with one of the websocket
 * annotations ({@link javax.websocket.OnOpen @OnOpen},
 * {@link javax.websocket.OnMessage @OnMessage}, {@link javax.websocket.OnError @OnError} and
 * {@link javax.websocket.OnClose @OnClose}), or overriding those of
 * {@link javax.websocket.Endpoint} or {@link javax.websocket.MessageHandler}.</p>
 *
 * @see RequestContext super class for more info
 */
public class WebsocketEventContext extends RequestContext {



	final HttpSession httpSession;
	@Override public HttpSession getHttpSession() { return httpSession; }



	protected WebsocketEventContext(
			HttpSession httpSession, ContextTracker<RequestContext> tracker) {
		super(tracker);
		this.httpSession = httpSession;
	}
}
