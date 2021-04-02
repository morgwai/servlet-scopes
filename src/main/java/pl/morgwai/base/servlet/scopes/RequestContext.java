/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ServerSideContext;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of a given <code>HttpServletRequest</code>.
 */
public class RequestContext extends TrackableContext<RequestContext> {



	HttpServletRequest request;
	public HttpServletRequest getRequest() { return request; }



	/**
	 * @return context of the session this request belongs to
	 */
	public ServerSideContext getSessionContext() {
		HttpSession session = getRequest().getSession();
		ServerSideContext sessionContext =
				(ServerSideContext) session.getAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME);
		if (sessionContext == null) {
			sessionContext = new ServerSideContext();
			session.setAttribute(SESSION_CONTEXT_ATTRIBUTE_NAME, sessionContext);
		}
		return sessionContext;
	}

	private static final String SESSION_CONTEXT_ATTRIBUTE_NAME = "contextAttributes";



	public RequestContext(HttpServletRequest request, ContextTracker<RequestContext> tracker) {
		super(tracker);
		this.request = request;
	}
}
