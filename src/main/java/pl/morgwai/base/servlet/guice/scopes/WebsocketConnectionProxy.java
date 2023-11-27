// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.security.Principal;
import java.util.*;

import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.RemoteEndpoint.Basic;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Decorates {@link MessageHandler}s passed to {@link #addMessageHandler(MessageHandler)} method
 * family with {@link WebsocketEventContext} tracking.
 * This is an internal class and users of the library don't need to deal with it directly. Also,
 * the amount of necessary boilerplate will make your eyes burn and heart cry: you've been
 * warned ;-]
 */
class WebsocketConnectionProxy implements Session {



	final Session wrappedConnection;
	final ContextTracker<ContainerCallContext> ctxTracker;
	final HttpSession httpSession;

	/**
	 * Set by
	 * {@link WebsocketConnectionContext#WebsocketConnectionContext(WebsocketConnectionProxy)
	 * WebsocketConnectionContext's constructor}.
	 */
	WebsocketConnectionContext connectionCtx;



	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void addMessageHandler(MessageHandler handler) {
		final var messageClass = getHandlerMessageClass(handler);
		if (handler instanceof MessageHandler.Partial) {
			addMessageHandler(messageClass, (MessageHandler.Partial) handler);
		} else {
			addMessageHandler(messageClass, (MessageHandler.Whole) handler);
		}
	}

	static Class<?> getHandlerMessageClass(MessageHandler handler) {
		try {
			ParameterizedType handlerType = null;
			for (final var implementedInterface: handler.getClass().getGenericInterfaces()) {
				if ( !(implementedInterface instanceof ParameterizedType)) continue;
				final var parameterizedInterface = (ParameterizedType) implementedInterface;
				final var parameterizedInterfaceClass = parameterizedInterface.getRawType();
				if (
					parameterizedInterfaceClass.equals(MessageHandler.Whole.class)
					|| parameterizedInterfaceClass.equals(MessageHandler.Partial.class)
				) {
					if (handlerType != null) throw new IllegalArgumentException();
					handlerType = parameterizedInterface;
				}
			}
			return (Class<?>) handlerType.getActualTypeArguments()[0];
		} catch (IllegalArgumentException | NullPointerException | ClassCastException e) {
			throw new IllegalArgumentException("cannot determine handler type");
		}
	}



	@Override
	public <T> void addMessageHandler(Class<T> messageClass, MessageHandler.Whole<T> handler) {
		wrappedConnection.addMessageHandler(
			messageClass,
			new WholeMessageHandlerDecorator<>(handler)
		);
	}



	@Override
	public <T> void addMessageHandler(Class<T> messageClass, MessageHandler.Partial<T> handler) {
		wrappedConnection.addMessageHandler(
			messageClass,
			new PartialMessageHandlerDecorator<>(handler)
		);
	}


	// todo: don't cluster tyrus flag

	boolean tyrus;

	WebsocketConnectionProxy(
		Session connection,
		ContextTracker<ContainerCallContext> containerCallContextTracker
	) {
		this(connection, containerCallContextTracker, false);
	}

	WebsocketConnectionProxy(
		Session connection,
		ContextTracker<ContainerCallContext> containerCallContextTracker,
		boolean remote
	) {
		this.wrappedConnection = connection;
		this.ctxTracker = containerCallContextTracker;
		this.httpSession = (HttpSession) (
			remote ? null : wrappedConnection.getUserProperties().get(HttpSession.class.getName())
		);
		try {
			wrappedConnection.getClass().getMethod("getDistributedProperties");
			tyrus = true;
		} catch (NoSuchMethodException e) {
			tyrus = false;
		}
	}

	/**
	 * Called by
	 * {@link WebsocketConnectionContext#WebsocketConnectionContext(WebsocketConnectionProxy)
	 * WebsocketConnectionContext's constructor}.
	 */
	void setConnectionCtx(WebsocketConnectionContext ctx) {
		this.connectionCtx = ctx;
		getUserProperties().put(
			WebsocketConnectionContext.class.getName(),
			connectionCtx
		);
	}

	@Override
	public Set<Session> getOpenSessions() {
		final var rawConnections = wrappedConnection.getOpenSessions();
		final var proxies = new HashSet<Session>(rawConnections.size(), 1.0f);
		for (final var connection: rawConnections) {
			final var userProperties =
					tyrus ? getDistributedProperties(connection) : connection.getUserProperties();
			final var connectionCtx = ((WebsocketConnectionContext)
					userProperties.get(WebsocketConnectionContext.class.getName()));
			if (connectionCtx.getConnection() == null) {
				// connection from another cluster node, that supports userProperties clustering
				connectionCtx.connectionProxy = new WebsocketConnectionProxy(connection, ctxTracker, true);
			}
			proxies.add(connectionCtx.getConnection());
		}
		if ( !tyrus) return proxies;

		try {
			// Tyrus clustering
			@SuppressWarnings("unchecked")
			final var remoteSessions = (Collection<? extends Session>)
					wrappedConnection
						.getClass()
						.getMethod("getRemoteSessions")
						.invoke(wrappedConnection);
			for (var remoteConnection: remoteSessions) {
				final var connectionCtx = ((WebsocketConnectionContext)
						getDistributedProperties(remoteConnection).get(WebsocketConnectionContext.class.getName()));
				final var proxy = new WebsocketConnectionProxy(remoteConnection, ctxTracker, true);
				connectionCtx.connectionProxy = proxy;
				proxies.add(proxy);
			}
			return proxies;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override public Map<String, Object> getUserProperties() {
		if ( !tyrus) {
			return wrappedConnection.getUserProperties();
		} else {
			return getDistributedProperties(wrappedConnection);
		}
	}

	static Map<String, Object> getDistributedProperties(Session tyrusConnection) {
		try {
			@SuppressWarnings("unchecked")
			final Map<String, Object> userProperties = (Map<String, Object>)
					tyrusConnection
						.getClass()
						.getMethod("getDistributedProperties")
						.invoke(tyrusConnection);
			return userProperties;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


	@Override
	public boolean equals(Object other) {
		if (other == null) return false;
		if ( !WebsocketConnectionProxy.class.isAssignableFrom(other.getClass())) return false;
		return wrappedConnection.equals(((WebsocketConnectionProxy) other).wrappedConnection);
	}



	@Override
	public int hashCode() {
		return wrappedConnection.hashCode();
	}



	static abstract class MessageHandlerDecorator implements MessageHandler {

		final MessageHandler wrappedHandler;

		@Override public boolean equals(Object other) {
			if (other == null) return false;
			if ( !MessageHandlerDecorator.class.isAssignableFrom(other.getClass())) return false;
			return wrappedHandler.equals(((MessageHandlerDecorator) other).wrappedHandler);
		}

		@Override public int hashCode() {
			return wrappedHandler.hashCode();
		}

		MessageHandlerDecorator(MessageHandler toWrap) {
			this.wrappedHandler = toWrap;
		}
	}



	class WholeMessageHandlerDecorator<T>
			extends MessageHandlerDecorator implements MessageHandler.Whole<T> {

		final MessageHandler.Whole<T> wrappedHandler;

		@Override public void onMessage(T message) {
			new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
					() -> wrappedHandler.onMessage(message));
		}

		WholeMessageHandlerDecorator(MessageHandler.Whole<T> toWrap) {
			super(toWrap);
			this.wrappedHandler = toWrap;
		}
	}



	class PartialMessageHandlerDecorator<T>
			extends MessageHandlerDecorator implements MessageHandler.Partial<T> {

		final MessageHandler.Partial<T> wrappedHandler;

		@Override public void onMessage(T message, boolean last) {
			new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
					() -> wrappedHandler.onMessage(message, last));
		}

		PartialMessageHandlerDecorator(MessageHandler.Partial<T> toWrap) {
			super(toWrap);
			this.wrappedHandler = toWrap;
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

	@Override public Principal getUserPrincipal() { return wrappedConnection.getUserPrincipal(); }
}
