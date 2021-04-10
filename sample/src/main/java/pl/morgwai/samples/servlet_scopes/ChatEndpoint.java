/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;



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
		connection.addMessageHandler(String.class, (message) -> this.onMessage(message));
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
			log.warning("error while sending message: " + e);
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
			log.warning("error while sending message: " + e);
		}
	}



	@Override
	public void onClose(Session connection, CloseReason reason) {
		try {
			broadcast(String.format("### %s has disconnected", nickname));
		} catch (IOException e) {
			log.warning("error while sending message: " + e);
		}
	}



	@Override
	public void onError(Session connection, Throwable error) {
		log.warning("error on connection " + connection.getId() + ": " + error);
		error.printStackTrace();
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
	}



	// Adapted from
	// github.com/apache/tomcat/blob/trunk/webapps/examples/WEB-INF/classes/util/HTMLFilter.java
	public static void appendFiltered(String message, StringBuilder target) {
		if (message == null) return;
		char content[] = new char[message.length()];
		message.getChars(0, message.length(), content, 0);
		for (int i = 0; i < content.length; i++) {
			switch (content[i]) {
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
					target.append(content[i]);
			}
		}
	}



	static final Logger log = Logger.getLogger(ChatEndpoint.class.getName());
}
