// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.Provider;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * A simple chat-over-websocket endpoint that demonstrates use of scopes. It gets injected 3
 * instances of {@link Service}, each in different scope and returns their hashcode to every
 * message received. The 1 {@link pl.morgwai.base.servlet.scopes.ServletModule#requestScope}d will
 * change every time, the 1
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



	@Inject @Named(ServletContextListener.REQUEST)
	Provider<Service> wsEventScopedProvider;

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
		var basicRemote = connection.getBasicRemote();
		try {
			synchronized (connection) {
			basicRemote.sendText(String.format("### assigned nickname: %s", nickname));
			basicRemote.sendText(String.format(
					"### service hashCodes: event=%d, connection=%d, httpSession=%d",
					wsEventScopedProvider.get().hashCode(),
					connectionScopedProvider.get().hashCode(),
					httpSessionScopedProvider.get().hashCode()));
			}
			broadcast(String.format("### %s has joined", nickname));
		} catch (IOException e) {
			log.warn("", e);
		}
	}



	public void onMessage(String message) {
		StringBuilder formattedMessageBuilder =
				new StringBuilder(nickname.length() + message.length() + 10);
		formattedMessageBuilder.append(nickname).append(": ");
		appendFiltered(message, formattedMessageBuilder);
		var basicRemote = connection.getBasicRemote();
		try {
			synchronized (connection) {
			basicRemote.sendText(String.format(
					"### service hashCodes: event=%d, connection=%d, httpSession=%d",
					wsEventScopedProvider.get().hashCode(),
					connectionScopedProvider.get().hashCode(),
					httpSessionScopedProvider.get().hashCode()));
			}
			broadcast(formattedMessageBuilder.toString());
		} catch (IOException e) {
			log.warn("", e);
		}
	}



	@Override
	public void onClose(Session connection, CloseReason reason) {
		try {
			broadcast(String.format("### %s has disconnected", nickname));
		} catch (IOException e) {
			log.warn("", e);
		}
	}



	@Override
	public void onError(Session connection, Throwable error) {
		log.warn("error on connection " + connection.getId(), error);
	}



	void broadcast(String msg) throws IOException {
		if (isShutdown) return;
		for (Session peerConnection: connection.getOpenSessions()) {
			if (peerConnection.isOpen()) {
				synchronized (peerConnection) {
					peerConnection.getBasicRemote().sendText(msg);
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
