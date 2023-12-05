// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus;

import java.util.*;

import javax.websocket.Session;

import org.glassfish.tyrus.core.TyrusSession;
import org.glassfish.tyrus.core.cluster.DistributedSession;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.servlet.guice.scopes.*;
import pl.morgwai.base.servlet.guice.scopes.WebsocketConnectionProxy.Factory.SupportedSessionType;



/**
 * Merges remote {@link Session connections} from other cluster nodes obtained via
 * {@link TyrusSession#getRemoteSessions()} into {@link #getOpenSessions()} and makes
 * {@link #getUserProperties()} return {@link TyrusSession#getDistributedProperties()}.
 */
class TyrusConnectionProxy extends WebsocketConnectionProxy {



	@SupportedSessionType(TyrusSession.class)
	public static class Factory implements WebsocketConnectionProxy.Factory {

		@Override
		public WebsocketConnectionProxy newProxy(
			Session connection,
			ContextTracker<ContainerCallContext> containerCallContextTracker
		) {
			return new TyrusConnectionProxy(connection, containerCallContextTracker, false);
		}
	}



	public TyrusConnectionProxy(
		Session connection,
		ContextTracker<ContainerCallContext> containerCallContextTracker,
		boolean remote
	) {
		super(connection, containerCallContextTracker, remote);
	}



	/**
	 * Union of {@link TyrusSession#getOpenSessions()} and {@link TyrusSession#getRemoteSessions()}.
	 */
	@Override
	public Set<Session> getOpenSessions() {
		@SuppressWarnings("unchecked")
		final var localPeerConnections = (Set<DistributedSession>) ((Set<? extends Session>)
				wrappedConnection.getOpenSessions());
		final var remotePeerConnections = ((TyrusSession) wrappedConnection).getRemoteSessions();
		final var proxies = new HashSet<Session>(
				localPeerConnections.size() + remotePeerConnections.size(), 1.0f);

		// local node connections
		for (final var peerConnection: localPeerConnections) {
			final var peerConnectionCtx = ((WebsocketConnectionContext)
					peerConnection.getDistributedProperties()
							.get(WebsocketConnectionContext.class.getName()));
			proxies.add(peerConnectionCtx.getConnection());
		}

		// remote connections from other nodes
		for (var peerConnection: remotePeerConnections) {
			proxies.add(new TyrusConnectionProxy(peerConnection, ctxTracker, true));
		}

		return proxies;
	}



	/** {@link TyrusSession#getDistributedProperties()}. */
	@Override
	public Map<String, Object> getUserProperties() {
		return ((DistributedSession) wrappedConnection).getDistributedProperties();
	}
}
