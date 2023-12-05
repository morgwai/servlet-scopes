// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.connectionproxy.tyrus.server;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.*;

import org.glassfish.tyrus.core.cluster.*;
import org.glassfish.tyrus.core.cluster.RemoteSession.DistributedMapKey;



public class InMemoryClusterContext extends ClusterContext {



	static class PathListener {

		final int nodeId;
		final SessionListener listener;

		PathListener(int nodeId, SessionListener listener) {
			this.nodeId = nodeId;
			this.listener = listener;
		}
	}

	static class SessionUserProperties {

		final int nodeId;
		final Map<String, Object> properties = new HashMap<>();

		SessionUserProperties(int nodeId) {
			this.nodeId = nodeId;
		}
	}


	static final AtomicInteger sessionIdSequence = new AtomicInteger(0);
	static final AtomicInteger connectionIdSequence = new AtomicInteger(0);

	static final ConcurrentMap<String, Set<PathListener>> pathListeners = new ConcurrentHashMap<>();
	static final ConcurrentMap<String, SessionEventListener> sessions = new ConcurrentHashMap<>();
	static final ConcurrentMap<String, Set<String>> pathSessions = new ConcurrentHashMap<>();
	static final ConcurrentMap<String, Map<DistributedMapKey, Object>> sessionSettings =
			new ConcurrentHashMap<>();
	static final ConcurrentMap<String, SessionUserProperties> sessionUserProperties =
			new ConcurrentHashMap<>();

	final int nodeId;



	public InMemoryClusterContext(int nodeId) {
		this.nodeId = nodeId;
	}



	@Override
	public String createSessionId() {
		return String.valueOf(sessionIdSequence.incrementAndGet());
	}



	static final Object lock = new Object();



	@Override
	public void registerSessionListener(String endpointPath, SessionListener listener) {
		synchronized (lock) {
			pathListeners.computeIfAbsent(
				endpointPath,
				(ignored) -> ConcurrentHashMap.newKeySet()
			).add(new PathListener(nodeId, listener));
		}
	}



	@Override
	public void registerSession(
		String sessionId,
		String endpointPath,
		SessionEventListener listener
	) {
		synchronized (lock) {
			sessions.put(sessionId, listener);
			pathSessions.computeIfAbsent(
				endpointPath,
				(ignored) -> ConcurrentHashMap.newKeySet()
			).add(sessionId);
			for (var pathListener: pathListeners.computeIfAbsent(
				endpointPath,
				(ignored) -> ConcurrentHashMap.newKeySet()
			)) {
				if (pathListener.nodeId != nodeId) pathListener.listener.onSessionOpened(sessionId);
			}
		}
	}



	@Override
	public void removeSession(String sessionId, String endpointPath) {
		synchronized (lock) {
			for (var pathListener : pathListeners.computeIfAbsent(
				endpointPath,
				(ignored) -> ConcurrentHashMap.newKeySet()
			)) {
				if (pathListener.nodeId != nodeId) pathListener.listener.onSessionClosed(sessionId);
			}
			sessions.remove(sessionId);
			pathSessions.computeIfAbsent(
				endpointPath,
				(ignored) -> ConcurrentHashMap.newKeySet()
			).remove(sessionId);
			sessionSettings.remove(sessionId);
		}
	}



	@Override
	public Set<String> getRemoteSessionIds(String endpointPath) {
		synchronized (lock) {
			return pathSessions.computeIfAbsent(
				endpointPath,
				(ignored) -> ConcurrentHashMap.newKeySet()
			);
		}
	}



	@Override
	public boolean isSessionOpen(String sessionId, String endpointPath) {
		synchronized (lock) {
			return sessions.containsKey(sessionId);
		}
	}



	@Override
	public Map<DistributedMapKey, Object> getDistributedSessionProperties(String sessionId) {
		return sessionSettings.computeIfAbsent(
			sessionId,
			(ignored) -> new ConcurrentHashMap<>()
		);
	}



	@Override
	public String createConnectionId() {
		return String.valueOf(connectionIdSequence.incrementAndGet());
	}



	@Override
	public Map<String, Object> getDistributedUserProperties(String connectionId) {
		final var properties = sessionUserProperties.computeIfAbsent(
			connectionId,
			(ignored) -> new SessionUserProperties(nodeId)
		);
		if (properties.nodeId == nodeId) return properties.properties;

		final var serializedBytesOutput = new ByteArrayOutputStream(500);
		try (
			serializedBytesOutput;
			final var serializedObjects = new ObjectOutputStream(serializedBytesOutput);
		) {
			serializedObjects.writeObject(properties.properties);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		try (
			final var serializedBytesInput =
					new ByteArrayInputStream(serializedBytesOutput.toByteArray());
			final var serializedObjects = new ObjectInputStream(serializedBytesInput);
		) {
			@SuppressWarnings("unchecked")
			final var deserializedProperties = (Map<String, Object>) serializedObjects.readObject();
			return deserializedProperties;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}



	@Override
	public void destroyDistributedUserProperties(String connectionId) {
		sessionUserProperties.remove(connectionId);
	}



	static CompletableFuture<Void> createRemoteExecution(Callable<Void> task) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return task.call();
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		});
	}



	@Override
	public Future<Void> sendText(String sessionId, String text) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onSendText(text);
			return null;
		});
	}



	@Override
	public Future<Void> sendText(String sessionId, String text, boolean isLast) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onSendText(text, isLast);
			return null;
		});
	}



	@Override
	public Future<Void> sendBinary(String sessionId, byte[] data) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onSendBinary(data);
			return null;
		});
	}



	@Override
	public Future<Void> sendBinary(String sessionId, byte[] data, boolean isLast) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onSendBinary(data, isLast);
			return null;
		});
	}



	@Override
	public Future<Void> sendPing(String sessionId, byte[] data) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onSendPing(data);
			return null;
		});
	}



	@Override
	public Future<Void> sendPong(String sessionId, byte[] data) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onSendPong(data);
			return null;
		});
	}



	@Override
	public void sendText(String sessionId, String text, SendHandler sendHandler) {
		createRemoteExecution(() -> {
			sessions.get(sessionId).onSendText(text);
			return null;
		}).whenComplete((result, exception) -> {
			sendHandler.onResult(exception == null ? new SendResult() : new SendResult(exception));
		});
	}



	@Override
	public void sendBinary(String sessionId, byte[] data, SendHandler sendHandler) {
		createRemoteExecution(() -> {
			sessions.get(sessionId).onSendBinary(data);
			return null;
		}).whenComplete((result, exception) -> {
			sendHandler.onResult(exception == null ? new SendResult() : new SendResult(exception));
		});
	}



	@Override
	public Future<Void> close(String sessionId) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onClose();
			return null;
		});
	}



	@Override
	public Future<Void> close(String sessionId, CloseReason closeReason) {
		return createRemoteExecution(() -> {
			sessions.get(sessionId).onClose(closeReason);
			return null;
		});
	}



	@Override
	public void shutdown() {
		for (var pathListeners: pathListeners.values()) {
			final var iterator = pathListeners.iterator();
			while (iterator.hasNext()) {
				if (iterator.next().nodeId == nodeId) {
					iterator.remove();
					break;
				}
			}
		}
	}



	@Override
	public void registerBroadcastListener(String endpointPath, BroadcastListener listener) {}

	@Override
	public void broadcastText(String endpointPath, String text) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void broadcastBinary(String endpointPath, byte[] data) {
		throw new UnsupportedOperationException();
	}
}
