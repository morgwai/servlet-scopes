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
 * This is an internal class and users of the library don't need to deal with it directly. Also,
 * the amount of necessary boilerplate will make your eyes burn and heart cry: you've been warned ;]
 */
class WebsocketConnectionWrapper implements Session {



	final Session wrappedConnection;
	final ContextTracker<ContainerCallContext> eventCtxTracker;
	final HttpSession httpSession;

	/**
	 * Set by {@link WebsocketConnectionContext#WebsocketConnectionContext(
	 * WebsocketConnectionWrapper, ContextTracker) WebsocketConnectionContext's constructor}.
	 */
	void setConnectionCtx(WebsocketConnectionContext ctx) { this.connectionCtx = ctx; }
	WebsocketConnectionContext connectionCtx;



	@Override
	public void addMessageHandler(MessageHandler handler) throws IllegalStateException {
		if (handler instanceof MessageHandler.Whole) {
			handler = new WholeMessageHandlerWrapper<>((MessageHandler.Whole<?>) handler);
		} else {
			handler = new PartialMessageHandlerWrapper<>((MessageHandler.Partial<?>) handler);
		}
		wrappedConnection.addMessageHandler(handler);
	}



	@Override
	public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Whole<T> handler) {
		wrappedConnection.addMessageHandler(
				clazz, new WebsocketConnectionWrapper.WholeMessageHandlerWrapper<>(handler));
	}



	@Override
	public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
		wrappedConnection.addMessageHandler(
				clazz, new WebsocketConnectionWrapper.PartialMessageHandlerWrapper<>(handler));
	}



	@Override
	public Set<Session> getOpenSessions() {
		Set<Session> result = new HashSet<>();
		for (Session connection: wrappedConnection.getOpenSessions()) {
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
		return wrappedConnection == ((WebsocketConnectionWrapper) other).wrappedConnection;
	}

	@Override public int hashCode() { return wrappedConnection.hashCode(); }



	WebsocketConnectionWrapper(
			Session connection, ContextTracker<ContainerCallContext> eventCtxTracker) {
		this.wrappedConnection = connection;
		this.eventCtxTracker = eventCtxTracker;
		this.httpSession = (HttpSession) wrappedConnection.getUserProperties().get(
				GuiceServerEndpointConfigurator.HTTP_SESSION_PROPERTY_NAME);
	}



	static abstract class MessageHandlerWrapper implements MessageHandler {

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

		MessageHandlerWrapper(MessageHandler handler) { this.wrapped = handler; }
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

		WholeMessageHandlerWrapper(MessageHandler.Whole<T> handler) {
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

		PartialMessageHandlerWrapper(MessageHandler.Partial<T> handler) {
			super(handler);
			this.wrapped = handler;
		}
	}



	// below only dumb delegations to wrappedConnection

	@Override public WebSocketContainer getContainer() { return wrappedConnection.getContainer(); }

	@Override public Set<MessageHandler> getMessageHandlers() {
		return wrappedConnection.getMessageHandlers();
	}

	@Override public void removeMessageHandler(MessageHandler handler) {
		wrappedConnection.removeMessageHandler(handler);
	}

	@Override public String getProtocolVersion() { return wrappedConnection.getProtocolVersion(); }

	@Override
	public String getNegotiatedSubprotocol() { return wrappedConnection.getNegotiatedSubprotocol();}

	@Override public List<Extension> getNegotiatedExtensions() {
		return wrappedConnection.getNegotiatedExtensions();
	}

	@Override public boolean isSecure() { return wrappedConnection.isSecure(); }

	@Override public boolean isOpen() { return wrappedConnection.isOpen(); }

	@Override public long getMaxIdleTimeout() { return wrappedConnection.getMaxIdleTimeout(); }

	@Override public void setMaxIdleTimeout(long milliseconds) {
		wrappedConnection.setMaxIdleTimeout(milliseconds);
	}

	@Override public void setMaxBinaryMessageBufferSize(int length) {
		wrappedConnection.setMaxBinaryMessageBufferSize(length);
	}

	@Override public int getMaxBinaryMessageBufferSize() {
		return wrappedConnection.getMaxBinaryMessageBufferSize();
	}

	@Override public void setMaxTextMessageBufferSize(int length) {
		wrappedConnection.setMaxTextMessageBufferSize(length);
	}

	@Override public int getMaxTextMessageBufferSize() {
		return wrappedConnection.getMaxTextMessageBufferSize();
	}

	@Override public Async getAsyncRemote() { return wrappedConnection.getAsyncRemote(); }

	@Override public Basic getBasicRemote() { return wrappedConnection.getBasicRemote(); }

	@Override public String getId() { return wrappedConnection.getId(); }

	@Override public void close() throws IOException { wrappedConnection.close(); }

	@Override public void close(CloseReason closeReason) throws IOException {
		wrappedConnection.close(closeReason);
	}

	@Override public URI getRequestURI() { return wrappedConnection.getRequestURI(); }

	@Override public Map<String, List<String>> getRequestParameterMap() {
		return wrappedConnection.getRequestParameterMap();
	}

	@Override public String getQueryString() { return wrappedConnection.getQueryString(); }

	@Override public Map<String, String> getPathParameters() {
		return wrappedConnection.getPathParameters();
	}

	@Override public Map<String, Object> getUserProperties() {
		return wrappedConnection.getUserProperties();
	}

	@Override public Principal getUserPrincipal() { return wrappedConnection.getUserPrincipal(); }
}
