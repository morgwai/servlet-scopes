/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of a <code>HttpServletRequest</code> or a websocket event.
 */
public abstract class RequestContext extends TrackableContext<RequestContext> {



	public abstract HttpSession getHttpSession();



	/**
	 * @return context of the <code>HttpSession</code> this request belongs to
	 */
	public ServerSideContext getHttpSessionContext() {
		HttpSession session = getHttpSession();
		ServerSideContext sessionContext =
				(ServerSideContext) session.getAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME);
		if (sessionContext == null) {
			sessionContext = new ServerSideContext();
			session.setAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME, sessionContext);
		}
		return sessionContext;
	}

	private static final String SESSION_CONTEXT_ATTRIBUTE_NAME = "contextAttributes";



	protected RequestContext(ContextTracker<RequestContext> tracker) {
		super(tracker);
	}
}
