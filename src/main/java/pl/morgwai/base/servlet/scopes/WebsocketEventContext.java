/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of a single websocket event such session being opened/closed, message being received or
 * error occurrence.
 * Each instance is coupled with a single invocation of some endpoint or <code>MessageHandler</code>
 * method, either annotated with one of the websocket annotations (<code>@OnOpen</code>,
 * <code>@OnMessage</code>, <code>@OnError</code>, <code>@OnClose</code>), or overriding
 * those of <code>javax.websocket.Endpoint</code> or <code>MessageHandler</code>.
 *
 * @see RequestContext super class for more info
 */
public class WebsocketEventContext extends RequestContext {



	HttpSession httpSession;
	@Override public HttpSession getHttpSession() { return httpSession; }



	protected WebsocketEventContext(
			HttpSession httpSession, ContextTracker<RequestContext> tracker) {
		super(tracker);
		this.httpSession = httpSession;
	}
}
