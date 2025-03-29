// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import jakarta.websocket.Session;

import pl.morgwai.base.guice.scopes.InjectionContext;



/**
 * Context of a websocket connection ({@link jakarta.websocket.Session}).
 * All {@link WebsocketEventContext websocket events} related to the same {@link Session connection}
 * are {@link WebsocketEventContext#executeWithinSelf(Runnable) handled within} <b>the same</b>
 * {@code WebsocketConnectionContext} instance.
 * <p>
 * Instances are stored in {@link Session#getUserProperties() user properites} under
 * {@link Class#getName() fully-qualified name} of this class.</p>
 * @see WebsocketModule#websocketConnectionScope corresponding Scope
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



	WebsocketConnectionContext(
		WebsocketConnectionProxy connectionProxy,
		WebsocketConnectionContext enclosingCtx
	) {
		super(enclosingCtx);
		this.connectionProxy = connectionProxy;
		connectionProxy.setConnectionCtx(this);
	}

	WebsocketConnectionContext(WebsocketConnectionProxy connectionProxy) {
		this(connectionProxy, null);
	}



	private static final long serialVersionUID = -5223603305758290198L;
}
