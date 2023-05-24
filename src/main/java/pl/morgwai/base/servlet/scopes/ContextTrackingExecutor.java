// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.Session;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * A {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor} with additional
 * {@link #execute(HttpServletResponse, Runnable) execute(httpResponse, task)} and
 * {@link #execute(Session, Runnable) execute(wsConnection, task)} methods
 * that send {@link HttpServletResponse#SC_SERVICE_UNAVAILABLE 503} /
 * {@link CloseCodes#TRY_AGAIN_LATER TRY_AGAIN_LATER} if the task is rejected.
 * This can happen due to an overload or a shutdown.
 * <p>
 * Instances can be created using {@link ServletModule#newContextTrackingExecutor(String, int)
 * ServletModule.newContextTrackingExecutor(...)} helper methods family.</p>
 */
public class ContextTrackingExecutor extends pl.morgwai.base.guice.scopes.ContextTrackingExecutor {



	/**
	 * Calls {@link #execute(Runnable) execute(task)} and if it's rejected, sends
	 * {@link HttpServletResponse#SC_SERVICE_UNAVAILABLE} to {@code response}.
	 */
	public void execute(HttpServletResponse response, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			try {
				if ( !response.isCommitted()) {
					response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				}
			} catch (IOException ignored) {}  // broken connection
		}
	}



	/**
	 * Calls {@link #execute(Runnable) execute(task)} and if it's rejected, closes
	 * {@code connection} with {@link CloseCodes#TRY_AGAIN_LATER}.
	 */
	public void execute(Session connection, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			try {
				if (connection.isOpen()) {
					connection.close(new CloseReason(
							CloseCodes.TRY_AGAIN_LATER, "service overloaded or restarting"));
				}
			} catch (IOException ignored) {}  // broken connection
		}
	}



	public Awaitable.WithUnit awaitableOfAwaitTermination() {
		return this::awaitTermination;
	}



	/**
	 * @deprecated This method will throw {@link RuntimeException}. Executors obtained from
	 *     {@link ServletModule} are shutdown automatically at app shutdown.
	 */
	@Override @Deprecated
	public void shutdown() {
		throw new RuntimeException(
				"executors obtained from ServletModule are shutdown automatically at app shutdown");
	}

	/**
	 * @deprecated This method will throw {@link RuntimeException}. Executors obtained from
	 *     {@link ServletModule} are shutdown automatically at app shutdown.
	 */
	@Override @Deprecated
	public Optional<List<Runnable>> enforceTermination(long timeout, TimeUnit unit) {
		throw new RuntimeException(
				"executors obtained from ServletModule are shutdown automatically at app shutdown");
	}

	/**
	 * @deprecated This method will throw {@link RuntimeException}. Executors obtained from
	 *     {@link ServletModule} are shutdown automatically at app shutdown.
	 */
	@Override @Deprecated
	public List<Runnable> shutdownNow() {
		throw new RuntimeException(
				"executors obtained from ServletModule are shutdown automatically at app shutdown");
	}

	void shutdownInternal() {
		super.shutdown();
	}



	Awaitable.WithUnit awaitableOfEnforceTermination() {
		return (timeout, unit) -> super.enforceTermination(timeout, unit).isEmpty();
	}



	ContextTrackingExecutor(String name, int poolSize, List<ContextTracker<?>> trackers) {
		super(name, poolSize, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			List<ContextTracker<?>> trackers) {
		super(name, poolSize, workQueue, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			List<ContextTracker<?>> trackers) {
		super(name, poolSize, workQueue, threadFactory, trackers);
	}



	ContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			List<ContextTracker<?>> trackers) {
		super(name, backingExecutor, poolSize, trackers);
	}
}
