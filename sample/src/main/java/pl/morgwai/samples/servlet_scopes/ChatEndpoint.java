// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * A simple chat-over-websocket endpoint that demonstrates use of scopes. It gets injected 3
 * instances of {@link Service}, each in different scope and returns their hashcode to every
 * message received. The 1 {@link pl.morgwai.base.servlet.scopes.ServletModule#containerCallScope}d
 * will change every time, the 1
 * {@link pl.morgwai.base.servlet.scopes.ServletModule#websocketConnectionScope}d will remain the
 * same within each browser tab/window (but will be different for each tab/window), the 1
 * {@link pl.morgwai.base.servlet.scopes.ServletModule#httpSessionScope}d will remain the same
 * across all windows/tabs of a given browser session, but will be different if the app is opened
 * in 2 different browsers.
 */
public class ChatEndpoint extends Endpoint {



	static boolean isShutdown = false;



	String nickname;
	Session connection;



	@Inject @Named(ServletContextListener.CONTAINER_CALL)
	Provider<Service> eventScopedProvider;

	@Inject @Named(ServletContextListener.WS_CONNECTION)
	Provider<Service> connectionScopedProvider;

	@Inject @Named(ServletContextListener.HTTP_SESSION)
	Provider<Service> httpSessionScopedProvider;



	@Override
	public void onOpen(Session connection, EndpointConfig config) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5l * 60l * 1000l);
		nickname = "user-" + connection.getId();
		connection.addMessageHandler(String.class, this::onMessage);
		var asyncRemote = connection.getAsyncRemote();
		synchronized (connection) {
			asyncRemote.sendText(String.format("### assigned nickname: %s", nickname));
			asyncRemote.sendText(String.format(
					"### service hashCodes: event=%d, connection=%d, httpSession=%d",
					eventScopedProvider.get().hashCode(),
					connectionScopedProvider.get().hashCode(),
					httpSessionScopedProvider.get().hashCode()));
		}
		broadcast(String.format("### %s has joined", nickname));
	}



	void onMessage(String message) {
		StringBuilder formattedMessageBuilder =
				new StringBuilder(nickname.length() + message.length() + 10);
		formattedMessageBuilder.append(nickname).append(": ");
		appendFiltered(message, formattedMessageBuilder);
		var asyncRemote = connection.getAsyncRemote();
		synchronized (connection) {
			asyncRemote.sendText(String.format(
					"### service hashCodes: event=%d, connection=%d, httpSession=%d",
					eventScopedProvider.get().hashCode(),
					connectionScopedProvider.get().hashCode(),
					httpSessionScopedProvider.get().hashCode()));
		}
		broadcast(formattedMessageBuilder.toString());
	}



	@Override
	public void onClose(Session connection, CloseReason reason) {
		broadcast(String.format("### %s has disconnected with code '%s'",
				nickname, reason.getCloseCode()));
	}



	@Override
	public void onError(Session connection, Throwable error) {
		log.warn("error on connection " + connection.getId(), error);
	}



	void broadcast(String msg) {
		if (isShutdown) return;
		for (Session peerConnection: connection.getOpenSessions()) {
			if (peerConnection.isOpen()) {
				synchronized (peerConnection) {
					peerConnection.getAsyncRemote().sendText(msg);
				}
			}
		}
	}



	static void shutdown() {
		isShutdown = true;
		log.info("ChatEndpoint shutdown");
	}



	// Adapted from
	// github.com/apache/tomcat/blob/trunk/webapps/examples/WEB-INF/classes/util/HTMLFilter.java
	public static void appendFiltered(String message, StringBuilder target) {
		if (message == null) return;
		char[] content = new char[message.length()];
		message.getChars(0, message.length(), content, 0);
		for (final char c: content) {
			switch (c) {
				case '<':
					target.append("&lt;");
					break;
				case '>':
					target.append("&gt;");
					break;
				case '&':
					target.append("&amp;");
					break;
				case '"':
					target.append("&quot;");
					break;
				case '\'':
					target.append("&apos;");
					break;
				default:
					target.append(c);
			}
		}
	}



	static final Logger log = LoggerFactory.getLogger(ChatEndpoint.class.getName());
}
