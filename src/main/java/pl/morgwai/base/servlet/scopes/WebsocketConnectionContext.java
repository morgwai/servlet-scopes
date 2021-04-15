/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a websocket connection (<code>javax.websocket.Session</code>).
 * A single instance has its lifetime coupled with a given endpoint instance.
 * Specifically, all calls to given given endpoint's annotated methods (from <code>@OnOpen</code>,
 * across all calls to <code>@OnMessage</code> and <code>@OnError</code> until and including
 * <code>@OnClose</code>) or methods overriding those of <code>javax.websocket.Endpoint</code>
 * together with methods of registered <code>MessageHandler</code>s are executed within a single
 * <code>WebsocketConnectionContext</code>.
 *
 * @see ServletModule#websocketConnectionScope corresponding <code>Scope</code>
 */
public class WebsocketConnectionContext extends ServerSideContext<WebsocketConnectionContext> {



	WebsocketConnectionProxy connection;
	public Session getConnection() { return connection; }



	protected WebsocketConnectionContext(
		WebsocketConnectionProxy connection,
		ContextTracker<WebsocketConnectionContext> tracker
	) {
		super(tracker);
		this.connection = connection;
		connection.setConnectionCtx(this);
	}
}
