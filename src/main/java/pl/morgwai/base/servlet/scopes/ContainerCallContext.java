// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;

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
		HttpSession session = getHttpSession();
		// TODO: consider maintaining ConcurrentMap<Session, Attributes> to avoid synchronization
		try {
			synchronized (session) {
				var sessionCtx = (HttpSessionContext)
						session.getAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME);
				if (sessionCtx == null) {
					sessionCtx = new HttpSessionContext(session);
					session.setAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME, sessionCtx);
				}
				return sessionCtx;
			}
		} catch (NullPointerException e) {
			// result of a bug that will be fixed in development phase: don't check manually
			// in production each time.
			throw new RuntimeException("no session in request context,  consider using a filter"
					+ " that creates a session for every incoming request");
		}
	}

	static final String SESSION_CONTEXT_ATTRIBUTE_NAME =
			ContainerCallContext.class.getPackageName() + ".contextAttributes";



	protected ContainerCallContext(ContextTracker<ContainerCallContext> tracker) {
		super(tracker);
	}
}
