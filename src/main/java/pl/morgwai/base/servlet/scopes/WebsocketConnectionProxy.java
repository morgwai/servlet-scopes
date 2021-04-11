/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
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
 * Wrapper around <code>javax.websocket.Session</code> which decorates passed
 * <code>MessageHandler</code>s with {@link WebsocketEventContext} tracking.
 * This is an internal class and users of the library don't need to deal with it directly.
 */
class WebsocketConnectionProxy implements Session {



	Session wrapped;

	ContextTracker<RequestContext> eventCtxTracker;

	HttpSession httpSession;

	WebsocketConnectionContext connectionCtx;
	void setConnectionCtx(WebsocketConnectionContext ctx) { this.connectionCtx = ctx; }



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
				clazz, new WebsocketConnectionProxy.WholeMessageHandlerWrapper<>(handler));
	}



	@Override
	public <T> void addMessageHandler(Class<T> clazz, MessageHandler.Partial<T> handler) {
		wrapped.addMessageHandler(
				clazz, new WebsocketConnectionProxy.PartialMessageHandlerWrapper<>(handler));
	}



	@SuppressWarnings("unchecked")
	@Override
	public Set<Session> getOpenSessions() {
		Set<Session> result = new HashSet<>();
		for (Session connection: wrapped.getOpenSessions()) {
			result.add(
				// wrapped connection from connectionCtx from ctxs from userProperties
				((Map<Session, WebsocketConnectionContext>)
					connection.getUserProperties().get(
							GuiceServerEndpointConfigurator.CONNECTION_CTXS_PROPERTY_NAME)
				).get(connection).connection);
		}
		return result;
	}



	public WebsocketConnectionProxy(
			Session connection, ContextTracker<RequestContext> eventCtxTracker) {
		this.wrapped = connection;
		this.eventCtxTracker = eventCtxTracker;
		this.httpSession = (HttpSession) wrapped.getUserProperties().get(
				GuiceServerEndpointConfigurator.HTTP_SESSION_PROPERTY_NAME);
	}



	class MessageHandlerWrapper implements MessageHandler {

		MessageHandler wrapped;

		public boolean equals(Object o) {
			if (o instanceof WebsocketConnectionProxy.MessageHandlerWrapper) {
				return wrapped.equals(((WebsocketConnectionProxy.MessageHandlerWrapper)o).wrapped);
			}
			return wrapped.equals(o);
		}

		public int hashCode() { return wrapped.hashCode(); }

		public MessageHandlerWrapper(MessageHandler handler) { this.wrapped = handler; }
	}



	class WholeMessageHandlerWrapper<T>
			extends MessageHandlerWrapper implements MessageHandler.Whole<T> {

		MessageHandler.Whole<T> wrapped;

		@Override
		public void onMessage(T message) {
			connectionCtx.runWithinSelf(
					() -> new WebsocketEventContext(httpSession, eventCtxTracker).runWithinSelf(
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
			connectionCtx.runWithinSelf(
					() -> new WebsocketEventContext(httpSession, eventCtxTracker).runWithinSelf(
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
