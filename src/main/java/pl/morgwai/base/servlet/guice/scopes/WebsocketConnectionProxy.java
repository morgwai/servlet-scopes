// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
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

import static java.util.stream.Collectors.toUnmodifiableMap;



/**
 * Decorates {@link MessageHandler}s passed to {@link #addMessageHandler(MessageHandler)} method
 * family with {@link WebsocketEventContext} tracking.
 */
public class WebsocketConnectionProxy implements Session {



	/**
	 * {@link ServiceLoader SPI}-provided factory for proxies, that wrap
	 * {@link #getSupportedConnectionType() specific implementation} of {@link Session} to enable
	 * its specific features.
	 */
	public interface Factory {

		WebsocketConnectionProxy newProxy(
			Session connectionToWrap,
			ContextTracker<ContainerCallContext> ctxTracker
		);

		/** Indicates what type of {@link Session} given factory creates proxies for. */
		Class<? extends Session> getSupportedConnectionType();
	}



	static Map<Class<? extends Session>, Factory> proxyFactories =
			ServiceLoader.load(Factory.class)
				.stream()
				.collect(toUnmodifiableMap(
					(provider) -> provider.get().getSupportedConnectionType(),
					ServiceLoader.Provider::get
				));



	public Session getWrappedConnection() { return wrappedConnection; }
	public final Session wrappedConnection;

	protected final ContextTracker<ContainerCallContext> ctxTracker;
	protected final HttpSession httpSession;

	private WebsocketConnectionContext connectionCtx;



	/**
	 * Creates a new {@code Proxy} for {@code connectionToWrap} using
	 * {@link ServiceLoader SPI}-provided {@link Factory} if available.
	 * If there's no factory specific for the given {@link Session} implementation, then
	 * {@link #WebsocketConnectionProxy(Session, ContextTracker)} is used.
	 */
	static WebsocketConnectionProxy newProxy(
		Session connectionToWrap,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		final var proxyFactory = proxyFactories.get(connectionToWrap.getClass());
		if (proxyFactory != null) return proxyFactory.newProxy(connectionToWrap, ctxTracker);
		return new WebsocketConnectionProxy(connectionToWrap, ctxTracker);
	}



	protected WebsocketConnectionProxy(
		Session connectionToWrap,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		this(
			connectionToWrap,
			ctxTracker,
			(HttpSession) connectionToWrap.getUserProperties().get(HttpSession.class.getName())
		);
	}



	/**
	 * Both for local connections and for those from other cluster nodes obtained by
	 * {@link #getOpenSessions()} in which case {@code httpSession} argument is {@code null}.
	 */
	private WebsocketConnectionProxy(
		Session connectionToWrap,
		ContextTracker<ContainerCallContext> ctxTracker,
		HttpSession httpSession
	) {
		this.wrappedConnection = connectionToWrap;
		this.ctxTracker = ctxTracker;
		this.httpSession = httpSession;
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
			final var peerConnectionCtx = (WebsocketConnectionContext)
					peerConnection.getUserProperties()
							.get(WebsocketConnectionContext.class.getName());
			if (peerConnectionCtx.getConnection() == null) {
				// peerConnection from another cluster node that supports userProperties clustering
				peerConnectionCtx.connectionProxy =
						new WebsocketConnectionProxy(peerConnection, ctxTracker, null);
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
			if ( !(other instanceof MessageHandlerDecorator)) return false;
			return wrappedHandler.equals(((MessageHandlerDecorator) other).wrappedHandler);
		}

		@Override public int hashCode() {
			return wrappedHandler.hashCode();
		}

		MessageHandlerDecorator(MessageHandler handlerToWrap) {
			this.wrappedHandler = handlerToWrap;
		}
	}



	class WholeMessageHandlerDecorator<T>
			extends MessageHandlerDecorator implements MessageHandler.Whole<T> {

		final MessageHandler.Whole<T> wrappedHandler;

		@Override public void onMessage(T message) {
			new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
					() -> wrappedHandler.onMessage(message));
		}

		WholeMessageHandlerDecorator(MessageHandler.Whole<T> handlerToWrap) {
			super(handlerToWrap);
			this.wrappedHandler = handlerToWrap;
		}
	}



	class PartialMessageHandlerDecorator<T>
			extends MessageHandlerDecorator implements MessageHandler.Partial<T> {

		final MessageHandler.Partial<T> wrappedHandler;

		@Override public void onMessage(T message, boolean last) {
			new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
					() -> wrappedHandler.onMessage(message, last));
		}

		PartialMessageHandlerDecorator(MessageHandler.Partial<T> handlerToWrap) {
			super(handlerToWrap);
			this.wrappedHandler = handlerToWrap;
		}
	}



	/**
	 * {@code WebsocketConnectionProxies} are equal iff they wrap the same connection, regardless
	 * if they are of different subclasses.
	 */
	@Override
	public final boolean equals(Object other) {
		if ( !(other instanceof WebsocketConnectionProxy)) return false;
		return wrappedConnection.equals(((WebsocketConnectionProxy) other).wrappedConnection);
	}



	@Override
	public final int hashCode() {
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
