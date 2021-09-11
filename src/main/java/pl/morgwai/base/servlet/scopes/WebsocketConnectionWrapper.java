// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.Extension;
import javax.websocket.MessageHandler;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.RemoteEndpoint.Basic;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Decorates {@link MessageHandler}s passed to {@link #addMessageHandler(MessageHandler)} family
 * method with {@link WebsocketEventContext} tracking.
 * This is an internal class and users of the library don't need to deal with it directly.
 */
class WebsocketConnectionWrapper implements Session {



	final Session wrapped;
	final ContextTracker<RequestContext> eventCtxTracker;
	final HttpSession httpSession;

	/**
	 * Set by {@link WebsocketConnectionContext#WebsocketConnectionContext(
	 * WebsocketConnectionWrapper, ContextTracker) WebsocketConnectionContext's constructor}.
	 */
	void setConnectionCtx(WebsocketConnectionContext ctx) { this.connectionCtx = ctx; }
	WebsocketConnectionContext connectionCtx;  // set by WebsocketConnectionContext constructor



	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
		if (handler instanceof MessageHandler.Whole) {
			handler = new WholeMessageHandlerWrapper((MessageHandler.Whole) handler);
		} else {
			handler = new PartialMessageHandlerWrapper((MessageHandler.Partial) handler);
		}
		wrapped.addMessageHandler(handler);
	}



	@Override
	public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
		wrapped.addMessageHandler(
				clazz, new WebsocketConnectionWrapper.WholeMessageHandlerWrapper<>(handler));
	}



	@Override
	public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
		wrapped.addMessageHandler(
				clazz, new WebsocketConnectionWrapper.PartialMessageHandlerWrapper<>(handler));
	}



	@Override
	public Set<Session> getOpenSessions() {
		Set<Session> result = new HashSet<>();
		for (Session connection: wrapped.getOpenSessions()) {
			var connectionCtx = (WebsocketConnectionContext) connection.getUserProperties().get(
					GuiceServerEndpointConfigurator.CONNECTION_CTX_PROPERTY_NAME);
			result.add(connectionCtx.getConnection());
		}
		return result;
	}



	@Override
	public boolean equals(Object other) {
		if (other == null) return false;
		if (WebsocketConnectionWrapper.class != other.getClass()) return false;
		return wrapped == ((WebsocketConnectionWrapper) other).wrapped;
	}

	@Override public int hashCode() { return wrapped.hashCode(); }



	public WebsocketConnectionWrapper(
			Session connection, ContextTracker<RequestContext> eventCtxTracker) {
		this.wrapped = connection;
		this.eventCtxTracker = eventCtxTracker;
		this.httpSession = (HttpSession) wrapped.getUserProperties().get(
				GuiceServerEndpointConfigurator.HTTP_SESSION_PROPERTY_NAME);
	}



	class MessageHandlerWrapper implements MessageHandler {

		MessageHandler wrapped;

		@Override
		public boolean equals(Object other) {
			if (other instanceof WebsocketConnectionWrapper.MessageHandlerWrapper) {
				return wrapped.equals(
						((WebsocketConnectionWrapper.MessageHandlerWrapper) other).wrapped);
			}
			return wrapped.equals(other);
		}

		@Override public int hashCode() { return wrapped.hashCode(); }

		public MessageHandlerWrapper(MessageHandler handler) { this.wrapped = handler; }
	}



	class WholeMessageHandlerWrapper<T>
			extends MessageHandlerWrapper implements MessageHandler.Whole<T> {

		MessageHandler.Whole<T> wrapped;

		@Override
		public void onMessage(T message) {
			connectionCtx.executeWithinSelf(
					() -> new WebsocketEventContext(httpSession, eventCtxTracker).executeWithinSelf(
							() -> wrapped.onMessage(message)));
		}

		public WholeMessageHandlerWrapper(MessageHandler.Whole<T> handler) {
			super(handler);
			this.wrapped = handler;
		}
	}



	class PartialMessageHandlerWrapper<T>
			extends MessageHandlerWrapper implements MessageHandler.Partial<T> {

		MessageHandler.Partial<T> wrapped;

		@Override
		public void onMessage(T message, boolean last) {
			connectionCtx.executeWithinSelf(
					() -> new WebsocketEventContext(httpSession, eventCtxTracker).executeWithinSelf(
							() -> wrapped.onMessage(message, last)));
		}

		public PartialMessageHandlerWrapper(MessageHandler.Partial<T> handler) {
			super(handler);
			this.wrapped = handler;
		}
	}



	// below only dumb delegations to wrapped

	@Override public WebSocketContainer getContainer() { return wrapped.getContainer(); }

	@Override public Set<MessageHandler> getMessageHandlers() {return wrapped.getMessageHandlers();}

	@Override
	public void removeMessageHandler(MessageHandler handler){wrapped.removeMessageHandler(handler);}

	@Override public String getProtocolVersion() { return wrapped.getProtocolVersion(); }

	@Override public String getNegotiatedSubprotocol() { return wrapped.getNegotiatedSubprotocol();}

	@Override
	public List<Extension> getNegotiatedExtensions() { return wrapped.getNegotiatedExtensions(); }

	@Override public boolean isSecure() { return wrapped.isSecure(); }

	@Override public boolean isOpen() { return wrapped.isOpen(); }

	@Override public long getMaxIdleTimeout() { return wrapped.getMaxIdleTimeout(); }

	@Override
	public void setMaxIdleTimeout(long milliseconds) { wrapped.setMaxIdleTimeout(milliseconds); }

	@Override public void setMaxBinaryMessageBufferSize(int length) {
		wrapped.setMaxBinaryMessageBufferSize(length);
	}

	@Override
	public int getMaxBinaryMessageBufferSize() { return wrapped.getMaxBinaryMessageBufferSize(); }

	@Override public void setMaxTextMessageBufferSize(int length) {
		wrapped.setMaxTextMessageBufferSize(length);
	}

	@Override
	public int getMaxTextMessageBufferSize() { return wrapped.getMaxTextMessageBufferSize(); }

	@Override public Async getAsyncRemote() { return wrapped.getAsyncRemote(); }

	@Override public Basic getBasicRemote() { return wrapped.getBasicRemote(); }

	@Override public String getId() { return wrapped.getId(); }

	@Override public void close() throws IOException { wrapped.close(); }

	@Override
	public void close(CloseReason closeReason) throws IOException { wrapped.close(closeReason); }

	@Override public URI getRequestURI() { return wrapped.getRequestURI(); }

	@Override public Map<String, List<String>> getRequestParameterMap() {
		return wrapped.getRequestParameterMap();
	}

	@Override public String getQueryString() { return wrapped.getQueryString(); }

	@Override public Map<String, String> getPathParameters() { return wrapped.getPathParameters(); }

	@Override public Map<String, Object> getUserProperties() { return wrapped.getUserProperties(); }

	@Override public Principal getUserPrincipal() { return wrapped.getUserPrincipal(); }
}
