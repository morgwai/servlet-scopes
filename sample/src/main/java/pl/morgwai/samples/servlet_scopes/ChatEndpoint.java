/*
 * Copyright (c) 2020 Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.servlet_scopes;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import pl.morgwai.base.servlet.scopes.GuicifiedServerEndpointConfigurator;

import javax.websocket.CloseReason.CloseCodes;



@ServerEndpoint(
		value = ChatEndpoint.PATH,
		configurator = GuicifiedServerEndpointConfigurator.class
)
public class ChatEndpoint {



	public static final String PATH = "/websocket/chat";



	static final Set<ChatEndpoint> endpoints = new CopyOnWriteArraySet<>();
	static boolean isShutdown = false;



	String nickname;
	Session connection;

	@Inject
	@Named(ServletContextListener.REQUEST)
	Provider<Service> wsEventScopedProvider;

	@Inject
	@Named(ServletContextListener.WS_CONNECTION)
	Provider<Service> connectionScopedProvider;

	@Inject
	@Named(ServletContextListener.HTTP_SESSION)
	Provider<Service> httpSessionScopedProvider;



	@OnOpen
	public void onOpen(Session connection) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5l * 60l * 1000l);
		nickname = "user-" + connection.getId();
		var asyncRemote = connection.getAsyncRemote();
		asyncRemote.sendText(String.format("### assigned nickname: %s", nickname));
		asyncRemote.sendText(String.format(
				"### event service hash: %d", wsEventScopedProvider.get().hashCode()));
		asyncRemote.sendText(String.format(
				"### connection service hash: %d", connectionScopedProvider.get().hashCode()));
		asyncRemote.sendText(String.format(
				"### HTTP session service hash: %d", httpSessionScopedProvider.get().hashCode()));
		endpoints.add(this);
		broadcast(String.format("### %s has joined", nickname));
	}



	@OnMessage
	public void onMessage(String message) {
		StringBuilder formattedMessageBuilder =
			new StringBuilder(nickname.length() + message.length() + 10);
		formattedMessageBuilder.append(nickname).append(": ");
		appendFiltered(message, formattedMessageBuilder);
		var asyncRemote = connection.getAsyncRemote();
		asyncRemote.sendText(String.format(
				"### event service hash: %d", wsEventScopedProvider.get().hashCode()));
		asyncRemote.sendText(String.format(
				"### connection service hash: %d", connectionScopedProvider.get().hashCode()));
		asyncRemote.sendText(String.format(
				"### HTTP session service hash: %d", httpSessionScopedProvider.get().hashCode()));
		broadcast(formattedMessageBuilder.toString());
	}



	@OnClose
	public void onClose(CloseReason reason) {
		endpoints.remove(this);
		broadcast(String.format("### %s has disconnected", nickname));
	}



	@OnError
	public void onError(Throwable error) {
		log.warning("error on connection " + connection.getId() + ": " + error);
		error.printStackTrace();
	}



	static void broadcast(String msg) {
		if (isShutdown) return;
		for (ChatEndpoint connection : endpoints) {
			if (connection.connection.isOpen()) {
				connection.connection.getAsyncRemote().sendText(msg);
			}
		}
	}



	static void shutdown() {
		isShutdown = true;
		for (ChatEndpoint endpoint : endpoints) {
			try {
				endpoint.connection.close(new CloseReason(CloseCodes.GOING_AWAY, "bye!"));
			} catch (IOException e) {}  // not worth logging as the server is shutting down
		}
		System.out.println("ChatEndpoint shutdown completed");
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
