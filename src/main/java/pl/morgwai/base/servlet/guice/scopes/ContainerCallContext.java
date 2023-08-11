// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.*;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of either an {@link ServletRequestContext HttpServletRequest} or a
 * {@link WebsocketEventContext websocket event}. Each instance corresponds to a single
 * container-initiated call to either one of {@link javax.servlet.Servlet}'s
 * {@code doXXX(...)} methods or to one of websocket {@code Endpoint}'s life-cycle methods (either
 * ones overriding one of {@link javax.websocket.Endpoint} methods or ones annotated with one
 * of {@link javax.websocket.OnOpen}, {@link javax.websocket.OnMessage},
 * {@link javax.websocket.OnError}, {@link javax.websocket.OnClose}).
 * <p>
 * Suitable for storing short-living objects, such as {@code EntityManager}s or DB transactions.</p>
 * <p>
 * Having a common super class for {@link ServletRequestContext} and {@link WebsocketEventContext}
 * allows to inject container-call scoped objects both in servlets and endpoints without a need for
 * 2 separate bindings.</p>
 * @see ServletModule#containerCallScope corresponding Scope
 */
public abstract class ContainerCallContext extends TrackableContext<ContainerCallContext> {



	/** Returns the {@link HttpSession}  this request/event belongs to. */
	public abstract HttpSession getHttpSession();



	/** Returns context of {@link #getHttpSession() the session this request/event belongs to}. */
	public HttpSessionContext getHttpSessionContext() {
		try {
			return (HttpSessionContext)
					getHttpSession().getAttribute(HttpSessionContext.class.getName());
		} catch (NullPointerException e) {
			// result of a bug that will be fixed in development phase: don't check manually
			// in production each time.
			throw new RuntimeException("No session in call context. Consider either using a filter"
					+ " that creates a session for every incoming request or using websocket"
					+ " connection scope instead.");
		}
	}



	protected ContainerCallContext(ContextTracker<ContainerCallContext> tracker) {
		super(tracker);
	}
}
