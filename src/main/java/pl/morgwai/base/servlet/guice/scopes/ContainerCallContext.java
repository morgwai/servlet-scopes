// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import javax.servlet.http.*;

import com.google.inject.OutOfScopeException;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.TrackableContext;



/**
 * Context of either an {@link ServletRequestContext HttpServletRequest} or a
 * {@link WebsocketEventContext websocket event}.
 * Each single container-invoked call either to one of
 * {@link javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
 * Servlet's service(...)}&nbsp;/&nbsp;{@link
 * javax.servlet.http.HttpServlet#doGet(HttpServletRequest, HttpServletResponse) doXXX(...)} methods
 * or to one of websocket {@code Endpoint}'s
 * {@link javax.websocket.Endpoint#onOpen(javax.websocket.Session, javax.websocket.EndpointConfig)
 * event}-{@link javax.websocket.OnMessage handling} methods
 * {@link TrackableContext#executeWithinSelf(java.util.concurrent.Callable) runs within} a
 * <b>separate</b> instance of the appropriate {@code ContainerCallContext} subclass.
 * <p>
 * Having a common base class for {@link ServletRequestContext} and {@link WebsocketEventContext}
 * allows to provide {@link WebsocketModule#containerCallScope container-call scoped} objects both
 * in {@link javax.servlet.Servlet}s and {@code Endpoints} without a need for 2 separate bindings.
 * </p>
 * @see WebsocketModule#containerCallScope corresponding Scope
 */
public abstract class ContainerCallContext extends TrackableContext<ContainerCallContext> {



	/** Returns the {@link HttpSession} this request/event belongs to or {@code null} if none. */
	public abstract HttpSession getHttpSession();



	/** Returns context of {@link #getHttpSession() the session this request/event belongs to}. */
	public HttpSessionContext getHttpSessionContext() {
		try {
			return HttpSessionContext.of(getHttpSession());
		} catch (NullPointerException e) {
			throw new OutOfScopeException(NO_HTTP_SESSION_MESSAGE);
		}
	}

	static final String NO_HTTP_SESSION_MESSAGE = "no HttpSession present. See the javadoc for "
			+ "ServletWebsocketModule.httpSessionScope -> https://javadoc.io/doc/pl.morgwai.base/"
			+ "servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/"
			+ "ServletWebsocketModule.html#httpSessionScope";



	protected ContainerCallContext(ContextTracker<ContainerCallContext> tracker) {
		super(tracker);
	}
}
