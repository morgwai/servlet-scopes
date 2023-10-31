// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.*;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of either an {@link ServletRequestContext HttpServletRequest} or a
 * {@link WebsocketEventContext websocket event}.
 * Each single container-invoked call either to one of {@link javax.servlet.Servlet}'s
 * {@code doXXX(...)} methods or to one of websocket {@code Endpoint}'s event-handling methods
 * {@link TrackableContext#executeWithinSelf(java.util.concurrent.Callable) runs within} its own
 * separate instance of the appropriate subclass of {@code ContainerCallContext}.
 * <p>
 * Having a common base class for {@link ServletRequestContext} and {@link WebsocketEventContext}
 * allows to provide {@link ServletModule#containerCallScope container-call scoped} objects both in
 * {@link javax.servlet.Servlet}s and {@code Endpoints} without a need for 2 separate bindings.</p>
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
