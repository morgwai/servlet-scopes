/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * blah
 */
public class WebsocketConnectionContext extends TrackableContext<WebsocketConnectionContext> {



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
