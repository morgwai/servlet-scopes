// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests.servercommon;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.websocket.*;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.RemoteEndpoint.Async;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;

import static pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Service.*;



/** Implementation of echoing. All other valid {@code Endpoints} either extend or wrap this one. */
public class EchoEndpoint {



	public static final String WELCOME_MESSAGE = "welcome :)";
	public static final String CLOSE_MESSAGE = "close";
	public static final String MESSAGE_PROPERTY = "message";



	Session connection;

	@Inject @Named(CONTAINER_CALL)
	Provider<Service> eventScopedProvider;

	@Inject @Named(WEBSOCKET_CONNECTION)
	Provider<Service> connectionScopedProvider;

	@Inject @Named(HTTP_SESSION)
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
				log.log(Level.WARNING, "exception while closing " + connection.getId(), e);
			}
		} else {
			send(filter(message));
		}
	}

	// Adapted from
	// github.com/apache/tomcat/blob/trunk/webapps/examples/WEB-INF/classes/util/HTMLFilter.java
	public static String filter(String message) {
		if (message == null) return "";
		final var builder = new StringBuilder(message.length() + 10);
		char[] messageChars = new char[message.length()];
		message.getChars(0, message.length(), messageChars, 0);
		for (char c: messageChars) {
			switch (c) {
				case '<':
					builder.append("&lt;");
					break;
				case '>':
					builder.append("&gt;");
					break;
				case '&':
					builder.append("&amp;");
					break;
				case '"':
					builder.append("&quot;");
					break;
				case '\'':
					builder.append("&apos;");
					break;
				default:
					builder.append(c);
			}
		}
		return builder.toString();
	}



	/** Calls {@link #send(String, String) send(MESSAGE_PROPERTY, message)}. */
	void send(String message) {
		send(MESSAGE_PROPERTY, message);
	}



	/**
	 * Sends a {@link Properties} object as text to the peer.
	 * The following {@link Properties#setProperty(String, String) properties will be set}:
	 * <ul>
	 *   <li>{@code name} - {@code value}</li>
	 *   <li>{@link Service#CONTAINER_CALL} - hash of the
	 *       {@link pl.morgwai.base.servlet.guice.scopes.ServletModule#containerCallScope
	 *       containerCallScope}d instance of {@link Service}</li>
	 *   <li>{@link Service#WEBSOCKET_CONNECTION} - hash of the
	 *       {@link pl.morgwai.base.servlet.guice.scopes.ServletModule#websocketConnectionScope
	 *       websocketConnectionScope}d instance of {@link Service}</li>
	 *   <li>{@link Service#HTTP_SESSION} - hash of the
	 *       {@link pl.morgwai.base.servlet.guice.scopes.ServletModule#httpSessionScope
	 *       httpSessionScope}d instance of {@link Service}</li>
	 * </ul>
	 */
	void send(String name, String value) {
		final var response = new Properties();
		response.setProperty(name, value);
		response.setProperty(
			CONTAINER_CALL,
			String.valueOf(eventScopedProvider.get().hashCode())
		);
		response.setProperty(
			WEBSOCKET_CONNECTION,
			String.valueOf(connectionScopedProvider.get().hashCode())
		);
		response.setProperty(
			HTTP_SESSION,
			String.valueOf(httpSessionScopedProvider.get().hashCode())
		);
		try (
			final var stringWriter = new StringWriter();
		) {
			response.store(stringWriter, null);
			connector.sendText(stringWriter.toString());
		} catch (IOException neverHappens) {
			throw new RuntimeException(neverHappens);
		}
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



	static final Logger log = Logger.getLogger("pl.morgwai.base.servlet.guice.scopes.tests");
}
