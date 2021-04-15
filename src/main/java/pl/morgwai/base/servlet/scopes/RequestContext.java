/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Context of a <code>HttpServletRequest</code> or a websocket event.
 * Each instance is coupled with a single invocations of some method, which makes it suitable
 * for storing short-living objects, such as <code>EntityManager</code>s or DB transactions.
 * Having a common super class for {@link ServletRequestContext} and {@link WebsocketEventContext}
 * allows instances from a single request scoped binding to be obtained both in servlets and
 * endpoints without a need for 2 separate bindings with different <code>@Named</code> annotation
 * value.
 *
 * @see ServletModule#requestScope corresponding <code>Scope</code>
 */
public abstract class RequestContext extends ServerSideContext<RequestContext> {



	public abstract HttpSession getHttpSession();



	/**
	 * @return context of the <code>HttpSession</code> this request belongs to
	 */
	public Map<Object, Object> getHttpSessionContextAttributes() {
		HttpSession session = getHttpSession();
		synchronized (session) {
			@SuppressWarnings("unchecked")
			var sessionContext =
					(Map<Object, Object>) session.getAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME);
			if (sessionContext == null) {
				sessionContext = new HashMap<>();
				session.setAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME, sessionContext);
			}
			return sessionContext;
		}
	}

	private static final String SESSION_CONTEXT_ATTRIBUTE_NAME = "contextAttributes";



	protected RequestContext(ContextTracker<RequestContext> tracker) {
		super(tracker);
	}
}
