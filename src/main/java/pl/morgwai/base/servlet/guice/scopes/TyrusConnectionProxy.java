// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * todo
 */
class TyrusConnectionProxy extends WebsocketConnectionProxy {


	// todo: spi

	static final String TYRUS_SESSION_CLASS_NAME = "org.glassfish.tyrus.core.TyrusSession";

	static final Method getRemoteSessions;
	static final Method getDistributedProperties;



	static {
		Method localGetRemoteSessions;
		Method localGetDistributedProperties;
		try {
			localGetRemoteSessions = Class
				.forName(TYRUS_SESSION_CLASS_NAME)
				.getMethod("getRemoteSessions");
			localGetDistributedProperties = Class
				.forName("org.glassfish.tyrus.core.cluster.DistributedSession")
				.getMethod("getDistributedProperties");
		} catch (NoSuchMethodException | ClassNotFoundException ignored) {
			localGetRemoteSessions = null;
			localGetDistributedProperties = null;
		}
		getRemoteSessions = localGetRemoteSessions;
		getDistributedProperties = localGetDistributedProperties;
	}



	TyrusConnectionProxy(
		Session connection,
		ContextTracker<ContainerCallContext> containerCallContextTracker,
		boolean remote
	) {
		super(connection, containerCallContextTracker, remote);
	}



	@Override
	public Set<Session> getOpenSessions() {
		try {
			final var localPeerConnections = wrappedConnection.getOpenSessions();
			@SuppressWarnings("unchecked")
			final var remotePeerConnections = (Collection<? extends Session>)
					getRemoteSessions.invoke(wrappedConnection);
			final var proxies = new HashSet<Session>(
					localPeerConnections.size() + remotePeerConnections.size(), 1.0f);

			// local node connections
			for (final var peerConnection: localPeerConnections) {
				final var peerConnectionCtx = ((WebsocketConnectionContext)
						getDistributedProperties(peerConnection)
								.get(WebsocketConnectionContext.class.getName()));
				proxies.add(peerConnectionCtx.getConnection());
			}

			// remote connections from other nodes
			for (var peerConnection: remotePeerConnections) {
				final var peerConnectionCtx = ((WebsocketConnectionContext)
						getDistributedProperties(peerConnection)
								.get(WebsocketConnectionContext.class.getName()));
				final var proxy = new TyrusConnectionProxy(peerConnection, ctxTracker, true);
				peerConnectionCtx.connectionProxy = proxy;
				proxies.add(proxy);
			}

			return proxies;
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		} catch (IllegalAccessException neverHappens) {
			throw new RuntimeException(neverHappens);
		}
	}



	@Override
	public Map<String, Object> getUserProperties() {
		try {
			return getDistributedProperties(wrappedConnection);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		} catch (IllegalAccessException neverHappens) {
			throw new RuntimeException(neverHappens);
		}
	}



	static Map<String, Object> getDistributedProperties(Session tyrusConnection)
			throws InvocationTargetException, IllegalAccessException {
		@SuppressWarnings("unchecked")
		final var userProperties = (Map<String, Object>)
				getDistributedProperties.invoke(tyrusConnection);
		return userProperties;
	}
}
