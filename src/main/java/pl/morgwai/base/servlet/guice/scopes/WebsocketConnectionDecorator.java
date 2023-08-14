// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.security.Principal;
import java.util.*;

import jakarta.servlet.http.HttpSession;
import jakarta.websocket.*;
import jakarta.websocket.RemoteEndpoint.Async;
import jakarta.websocket.RemoteEndpoint.Basic;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Decorates {@link MessageHandler}s passed to {@link #addMessageHandler(MessageHandler)} family
 * method with {@link WebsocketEventContext} tracking.
 * This is an internal class and users of the library don't need to deal with it directly. Also,
 * the amount of necessary boilerplate will make your eyes burn and heart cry: you've been
 * warned ;-]
 */
class WebsocketConnectionDecorator implements Session {



	final Session wrappedConnection;
	final ContextTracker<ContainerCallContext> containerCallContextTracker;
	final HttpSession httpSession;

	/**
	 * Set by
	 * {@link WebsocketConnectionContext#WebsocketConnectionContext(WebsocketConnectionDecorator)
	 * WebsocketConnectionContext's constructor}.
	 */
	void setConnectionCtx(WebsocketConnectionContext ctx) { this.connectionCtx = ctx; }
	WebsocketConnectionContext connectionCtx;



	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void addMessageHandler(MessageHandler handler) {
		final Class<?> messageClass;
		try {
			final var handlerType = Arrays.stream(handler.getClass().getGenericInterfaces())
				.filter((type) -> type instanceof ParameterizedType)
				.map((type) -> (ParameterizedType) type)
				.filter(
					(parameterizedType) -> (
						parameterizedType.getRawType().equals(MessageHandler.Whole.class)
						|| parameterizedType.getRawType().equals(MessageHandler.Partial.class)
					)
				).findAny()
				.orElseThrow();
			messageClass = (Class<?>) handlerType.getActualTypeArguments()[0];
		} catch (Exception e) {
			throw new IllegalArgumentException("cannot determine handler type");
		}
		if (handler instanceof MessageHandler.Partial) {
			addMessageHandler(messageClass, (MessageHandler.Partial) handler);
		} else {
			addMessageHandler(messageClass, (MessageHandler.Whole) handler);
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



	@Override
	public Set<Session> getOpenSessions() {
		Set<Session> result = new HashSet<>();
		for (final var connection: wrappedConnection.getOpenSessions()) {
			final var connectionCtx = (WebsocketConnectionContext)
					connection.getUserProperties().get(WebsocketConnectionContext.class.getName());
			result.add(connectionCtx.getConnection());
		}
		return result;
	}



	@Override
	public boolean equals(Object other) {
		if (other == null) return false;
		if ( !WebsocketConnectionDecorator.class.isAssignableFrom(other.getClass())) return false;
		return wrappedConnection.equals(((WebsocketConnectionDecorator) other).wrappedConnection);
	}

	@Override
	public int hashCode() {
		return wrappedConnection.hashCode();
	}



	WebsocketConnectionDecorator(
		Session connection,
		ContextTracker<ContainerCallContext> containerCallContextTracker
	) {
		this.wrappedConnection = connection;
		this.containerCallContextTracker = containerCallContextTracker;
		this.httpSession = (HttpSession)
				wrappedConnection.getUserProperties().get(HttpSession.class.getName());
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
			new WebsocketEventContext(connectionCtx, httpSession, containerCallContextTracker)
					.executeWithinSelf(() -> wrappedHandler.onMessage(message));
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
			new WebsocketEventContext(connectionCtx, httpSession, containerCallContextTracker)
					.executeWithinSelf(() -> wrappedHandler.onMessage(message, last));
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

	@Override public Map<String, Object> getUserProperties() {
		return wrappedConnection.getUserProperties();
	}

	@Override public Principal getUserPrincipal() { return wrappedConnection.getUserPrincipal(); }
}
