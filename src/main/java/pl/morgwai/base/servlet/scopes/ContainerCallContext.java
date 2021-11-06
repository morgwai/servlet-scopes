// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.http.HttpSession;

import com.google.inject.Key;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



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
public abstract class ContainerCallContext extends ServerSideContext<ContainerCallContext> {



	/**
	 * Returns the {@link HttpSession}  this request belongs to.
	 */
	public abstract HttpSession getHttpSession();



	/**
	 * Returns attributes of the context of the {@link HttpSession}  this request belongs to.
	 */
	public ConcurrentMap<Key<?>, Object> getHttpSessionContextAttributes() {
		HttpSession session = getHttpSession();
		// TODO: consider maintaining ConcurrentMap<Session, Attributes> to avoid synchronization
		try {
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
