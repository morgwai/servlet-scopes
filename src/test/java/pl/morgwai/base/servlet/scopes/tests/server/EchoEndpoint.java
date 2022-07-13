// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import jakarta.websocket.EndpointConfig;
import jakarta.websocket.RemoteEndpoint.Async;
import jakarta.websocket.Session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class EchoEndpoint {



	public static final String WELCOME_MESSAGE = "welcome :)";



	Session connection;

	@Inject @Named(ServletContextListener.CONTAINER_CALL)
	Provider<Service> eventScopedProvider;

	@Inject @Named(ServletContextListener.WEBSOCKET_CONNECTION)
	Provider<Service> connectionScopedProvider;

	@Inject @Named(ServletContextListener.HTTP_SESSION)
	Provider<Service> httpSessionScopedProvider;

	Async sender;



	public void onOpen(Session connection, EndpointConfig config) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5l * 60l * 1000l);
		sender = connection.getAsyncRemote();
		send(WELCOME_MESSAGE);
	}



	public void onMessage(String message) {
		StringBuilder formattedMessageBuilder = new StringBuilder(message.length() + 10);
		appendFiltered(message, formattedMessageBuilder);
		send(formattedMessageBuilder.toString());
	}



	/**
	 * Sends {@code message} to the peer together with scoped object hashes. The format is coherent
	 * with the one in {@link TestServlet}: 3rd line contains container call scoped object hash,
	 * 4th line contains HTTP session scoped object hash.<br/>
	 * The 1st line contains the messages with EOL characters replaced by space. The 5th line
	 * contains websocket connection scoped object hash.
	 *
	 * @see TestServlet#RESPONSE_FORMAT
	 */
	void send(String message) {
		sender.sendText(String.format(
				TestServlet.RESPONSE_FORMAT + "\nconnection=%d",
				message.replace('\n', ' '),
				eventScopedProvider.get().hashCode(),
				httpSessionScopedProvider.get().hashCode(),
				connectionScopedProvider.get().hashCode()));
	}



	public void onError(Session connection, Throwable error) {
		log.warn("error on connection " + connection.getId(), error);
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



	static final Logger log = LoggerFactory.getLogger(EchoEndpoint.class.getName());
}
