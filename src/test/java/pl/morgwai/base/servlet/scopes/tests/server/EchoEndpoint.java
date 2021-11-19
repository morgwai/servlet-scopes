// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes.tests.server;

import javax.websocket.EndpointConfig;
import javax.websocket.Session;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class EchoEndpoint {



	Session connection;

	@Inject @Named(ServletContextListener.CONTAINER_CALL)
	Provider<Service> eventScopedProvider;

	@Inject @Named(ServletContextListener.WEBSOCKET_CONNECTION)
	Provider<Service> connectionScopedProvider;

	@Inject @Named(ServletContextListener.HTTP_SESSION)
	Provider<Service> httpSessionScopedProvider;



	public void onOpen(Session connection, EndpointConfig config) {
		this.connection = connection;
		connection.setMaxIdleTimeout(5l * 60l * 1000l);
		var asyncRemote = connection.getAsyncRemote();
		asyncRemote.sendText(String.format(
				"service hashCodes:\nevent=%d\nconnection=%d\nhttpSession=%d",
				eventScopedProvider.get().hashCode(),
				connectionScopedProvider.get().hashCode(),
				httpSessionScopedProvider.get().hashCode()));
	}



	public void onMessage(String message) {
		StringBuilder formattedMessageBuilder = new StringBuilder(message.length() + 10);
		appendFiltered(message, formattedMessageBuilder);
		var asyncRemote = connection.getAsyncRemote();
		asyncRemote.sendText(String.format(
				"%s\nservice hashCodes:\nevent=%d\nconnection=%d\nhttpSession=%d",
				formattedMessageBuilder.toString(),
				eventScopedProvider.get().hashCode(),
				connectionScopedProvider.get().hashCode(),
				httpSessionScopedProvider.get().hashCode()));
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
