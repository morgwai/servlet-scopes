// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.*;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.RemoteEndpoint.Async;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;



/** Implementation of echoing. All other valid {@code Endpoints} either extend or wrap this one. */
public class EchoEndpoint {



	public static final String WELCOME_MESSAGE = "welcome :)";
	public static final String CLOSE_MESSAGE = "close";

	/** Format for servlet and websocket responses. */
	public static final String RESPONSE_FORMAT = "%s\nservice hashCodes:\ncall=%d\nsession=%d";



	Session connection;

	@Inject @Named(Service.CONTAINER_CALL)
	Provider<Service> eventScopedProvider;

	@Inject @Named(Service.WEBSOCKET_CONNECTION)
	Provider<Service> connectionScopedProvider;

	@Inject @Named(Service.HTTP_SESSION)
	Provider<Service> httpSessionScopedProvider;

	Async connector;



	public void onOpen(Session connection, EndpointConfig config) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5L * 60L * 1000L);
		connector = connection.getAsyncRemote();
		send(WELCOME_MESSAGE);
	}



	public void onMessage(String message) {
		if (message.equals(CLOSE_MESSAGE)) {
			try {
				connection.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "bye"));
			} catch (IOException e) {
				log.log(Level.INFO, "exception while closing websocket " + connection.getId(), e);
			}
		} else {
			StringBuilder formattedMessageBuilder = new StringBuilder(message.length() + 10);
			appendFiltered(message, formattedMessageBuilder);
			send(formattedMessageBuilder.toString());
		}
	}



	/**
	 * Sends {@code message} to the peer followed by with scoped object hashes.
	 * The 1st line contains the messages with EOL characters replaced by space.
	 * The 3rd line contains container call scoped object hash.
	 * The 4th line contains HTTP session scoped object hash.
	 * The 5th line contains websocket connection scoped object hash.
	 */
	void send(String message) {
		connector.sendText(String.format(
			RESPONSE_FORMAT + "\nconnection=%d",
			message.replace('\n', ' '),
			eventScopedProvider.get().hashCode(),
			httpSessionScopedProvider.get().hashCode(),
			connectionScopedProvider.get().hashCode()
		));
	}



	public void onError(Session connection, Throwable error) {
		log.log(Level.WARNING, "error on connection " + connection.getId(), error);
		error.printStackTrace();
		try {
			connection.close(new CloseReason(CloseCodes.UNEXPECTED_CONDITION, error.toString()));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}



	public void onClose(CloseReason closeReason) {
		log.info("closing " + connection.getId() + ", code: " + closeReason.getCloseCode() +
				", reason: " + closeReason.getReasonPhrase());
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



	static final Logger log = Logger.getLogger("pl.morgwai.base.servlet.guice.scopes.tests");
}
