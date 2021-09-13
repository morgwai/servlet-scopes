// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpSession;

import com.google.inject.Key;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a {@link javax.servlet.http.HttpServletRequest} or a websocket event.
 * Each instance is coupled with a single invocations of some method, which makes it suitable
 * for storing short-living objects, such as <code>EntityManager</code>s or DB transactions.
 * Having a common super class for {@link ServletRequestContext} and {@link WebsocketEventContext}
 * allows instances from a single request scoped binding to be obtained both in servlets and
 * endpoints without a need for 2 separate bindings with different @{@link javax.inject.Named Named}
 * annotation value.
 *
 * @see ServletModule#requestScope corresponding <code>Scope</code>
 */
public abstract class RequestContext extends ServerSideContext<RequestContext> {



	public abstract HttpSession getHttpSession();



	/**
	 * @return context of the <code>HttpSession</code> this request belongs to
	 */
	public ConcurrentMap<Key<?>, Object> getHttpSessionContextAttributes() {
		HttpSession session = getHttpSession();
		if (session == null) {
			throw new RuntimeException("no session in request context,  consider using a filter"
					+ " that creates a session for every incoming request");
		}
		// TODO: consider maintaining ConcurrentMap<Session, Attributes> to avoid synchronization
		synchronized (session) {
			@SuppressWarnings("unchecked")
			var sessionContextAttributes = (ConcurrentMap<Key<?>, Object>)
					session.getAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME);
			if (sessionContextAttributes == null) {
				sessionContextAttributes = new ConcurrentHashMap<>();
				session.setAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME, sessionContextAttributes);
			}
			return sessionContextAttributes;
		}
	}

	private static final String SESSION_CONTEXT_ATTRIBUTE_NAME =
			RequestContext.class.getPackageName() + ".contextAttributes";



	protected RequestContext(ContextTracker<RequestContext> tracker) {
		super(tracker);
	}
}
