// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of either an {@link ServletRequestContext HttpServletRequest} or a
 * {@link WebsocketEventContext websocket event}. Each instance corresponds to a single
 * container-initiated call to either one of servlet's {@code doXXX(...)} methods or to a websocket
 * endpoint life-cycle method.
 * <p>
 * Suitable for storing short-living objects, such as {@code EntityManager}s or DB transactions.</p>
 * <p>
 * Having a common super class for {@link ServletRequestContext} and {@link WebsocketEventContext}
 * allows instances from a single container-call scoped binding to be obtained both in servlets and
 * endpoints without a need for 2 separate bindings with different
 * {@link com.google.inject.name.Named @Named} annotation value.</p>
 *
 * @see ServletModule#containerCallScope corresponding <code>Scope</code>
 */
public abstract class ContainerCallContext extends TrackableContext<ContainerCallContext> {



	/**
	 * Returns the {@link HttpSession}  this request belongs to.
	 */
	public abstract HttpSession getHttpSession();



	/**
	 * Returns context of {@link #getHttpSession() the session this request belongs to}.
	 */
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



	static class SessionContextCreator implements HttpSessionListener {

		@Override
		public void sessionCreated(HttpSessionEvent event) {
			var session = event.getSession();
			session.setAttribute(
					HttpSessionContext.class.getName(), new HttpSessionContext(session));
		}
	}



	protected ContainerCallContext(ContextTracker<ContainerCallContext> tracker) {
		super(tracker);
	}
}
