// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.security.Principal;
import java.util.*;

import javax.servlet.http.HttpSession;
import javax.websocket.*;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.RemoteEndpoint.Basic;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.WebsocketConnectionProxy.Factory.SupportedSessionType;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toMap;



/**
 * Decorates {@link MessageHandler}s passed to {@link #addMessageHandler(MessageHandler)} method
 * family with {@link WebsocketEventContext} tracking.
 */
public class WebsocketConnectionProxy implements Session {



	/**
	 * {@link ServiceLoader SPI}-provided factory for proxies, that wrap specific implementation
	 * of {@link Session} to enable its specific features. {@code Factory}'s supported
	 * {@link Session} class must be indicated by annotating {@code Factory}'s class with a
	 * {@link SupportedSessionType}.
	 */
	public interface Factory {

		/** Indicates what type of {@link Session} given factory creates proxies for. */
		@Retention(RUNTIME)
		@Target(TYPE)
		@interface SupportedSessionType {
			String value();
// todo:			Class<? extends Session> value();
		}

		WebsocketConnectionProxy newProxy(
			Session connection,
			ContextTracker<ContainerCallContext> containerCallContextTracker
		);
	}



	static Map<Class<? extends Session>, Factory> proxyFactories =
			ServiceLoader.load(Factory.class).stream()
				.collect(toMap(
					(provider) -> {
						try {
							return (Class<? extends Session>) Class.forName(provider.type().getAnnotation(SupportedSessionType.class).value());
						} catch (ClassNotFoundException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					},
					ServiceLoader.Provider::get
				));



	public Session getWrappedConnection() { return wrappedConnection; }
	protected final Session wrappedConnection;
	protected final ContextTracker<ContainerCallContext> ctxTracker;
	protected final HttpSession httpSession;

	private WebsocketConnectionContext connectionCtx;



	/**
	 * Asks {@link ServiceLoader SPI}-provided factory to create a new proxy for {@code connection}.
	 * If there's no factory specific for the given {@link Session} implementation, uses
	 * {@link #WebsocketConnectionProxy(Session, ContextTracker, boolean)}.
	 */
	static WebsocketConnectionProxy newInstance(
		Session connection,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		final var proxyFactory = proxyFactories.get(connection.getClass());
		if (proxyFactory != null) return proxyFactory.newProxy(connection, ctxTracker);
		return new WebsocketConnectionProxy(connection, ctxTracker, false);
	}



	/**
	 * Constructs a new proxy for {@code connection}.
	 * @param remote weather {@code connection} is a remote {@link Session} from another cluster
	 *     node. In such case, there will be no attempt to retrieve {@link #httpSession} from
	 *     {@link Session#getUserProperties() userProperties}. This is useful when creating proxies
	 *     for remote {@link Session}s in {@link #getOpenSessions()} in a clustered environment.
	 */
	protected WebsocketConnectionProxy(
		Session connection,
		ContextTracker<ContainerCallContext> containerCallContextTracker,
		boolean remote
	) {
		this.wrappedConnection = connection;
		this.ctxTracker = containerCallContextTracker;
		this.httpSession = (HttpSession) (
				remote
					? null
					: wrappedConnection.getUserProperties().get(HttpSession.class.getName())
		);
	}



	/**
	 * Called by
	 * {@link WebsocketConnectionContext#WebsocketConnectionContext(WebsocketConnectionProxy)
	 * WebsocketConnectionContext's constructor}.
	 */
	void setConnectionCtx(WebsocketConnectionContext ctx) {
		assert this.connectionCtx == null : "connection context already set";
		this.connectionCtx = ctx;
		getUserProperties().put(
			WebsocketConnectionContext.class.getName(),
			ctx
		);
	}



	@Override
	public Set<Session> getOpenSessions() {
		final var rawPeerConnections = wrappedConnection.getOpenSessions();
		final var proxies = new HashSet<Session>(rawPeerConnections.size(), 1.0f);
		for (final var peerConnection: rawPeerConnections) {
			final var peerConnectionCtx = ((WebsocketConnectionContext)
					peerConnection.getUserProperties()
							.get(WebsocketConnectionContext.class.getName()));
			if (peerConnectionCtx.getConnection() == null) {
				// peerConnection from another cluster node, that supports userProperties clustering
				peerConnectionCtx.connectionProxy =
						new WebsocketConnectionProxy(peerConnection, ctxTracker, true);
			}
			proxies.add(peerConnectionCtx.getConnection());
		}
		return proxies;
	}



	@Override
	public Map<String, Object> getUserProperties() {
		return wrappedConnection.getUserProperties();
	}



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
