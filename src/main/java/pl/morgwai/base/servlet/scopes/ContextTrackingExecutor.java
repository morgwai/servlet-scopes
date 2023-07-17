// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.util.concurrent.Awaitable;



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

	void packageProtectedShutdown() {
		super.shutdown();
	}

	Awaitable.WithUnit toAwaitableOfEnforceTermination() {
		return (timeout, unit) -> super.enforceTermination(timeout, unit).isEmpty();
	}



	ContextTrackingExecutor(String name, int poolSize, List<ContextTracker<?>> trackers) {
		super(name, poolSize, trackers);
	}

	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		BlockingQueue<Runnable> workQueue
	) {
		super(name, poolSize, trackers, workQueue);
	}

	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ? super ContextTrackingExecutor> rejectionHandler
	) {
		super(
			name,
			poolSize,
			trackers,
			workQueue,
			(task, executor) -> rejectionHandler.accept(task, (ContextTrackingExecutor) executor)
		);
	}

	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ? super ContextTrackingExecutor> rejectionHandler,
		ThreadFactory threadFactory
	) {
		super(
			name,
			poolSize,
			trackers,
			workQueue,
			(task, executor) -> rejectionHandler.accept(task, (ContextTrackingExecutor) executor),
			threadFactory
		);
	}

	ContextTrackingExecutor(
		String name,
		int poolSize,
		List<ContextTracker<?>> trackers,
		ExecutorService backingExecutor
	) {
		super(name, poolSize, trackers, backingExecutor);
	}
}
