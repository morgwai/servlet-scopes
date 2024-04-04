// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.HttpSession;
import javax.websocket.*;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Context of a single websocket event such as a connection creation/closure, a message arrival or
 * an error occurrence.
 * Each container-invoked call to some {@code Endpoint} event-handling method or to a registered
 * {@link MessageHandler} method
 * {@link WebsocketEventContext#executeWithinSelf(java.util.concurrent.Callable) runs within} a
 * <b>separate</b> {@code WebsocketEventContext} instance. Specifically, all methods annotated with
 * one of the websocket {@code Annotations} (&nbsp;{@link OnOpen @OnOpen},
 * {@link OnMessage @OnMessage}, {@link OnError @OnError}, {@link OnClose @OnClose}&nbsp;), or
 * overriding one of {@link Endpoint} methods
 * (&nbsp;{@link Endpoint#onOpen(Session, EndpointConfig) onOpen(...)},
 * {@link Endpoint#onClose(Session, CloseReason) onClose(...)},
 * {@link Endpoint#onError(Session, Throwable) onError(...)}&nbsp;) or overriding one of
 * {@link MessageHandler} methods
 * (&nbsp;{@link MessageHandler.Whole#onMessage(Object) Whole.onMessage(message)},
 * {@link MessageHandler.Partial#onMessage(Object, boolean) Partial.onMessage(...)}&nbsp;).
 */
public class WebsocketEventContext extends ContainerCallContext {



	public WebsocketConnectionContext getConnectionContext() { return connectionContext; }
	public final WebsocketConnectionContext connectionContext;

	@Override public HttpSession getHttpSession() { return httpSession; }
	public final HttpSession httpSession;



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
