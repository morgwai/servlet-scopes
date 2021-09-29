// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * A {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor} with additional
 * {@link #execute(HttpServletResponse, Runnable)} and {@link #execute(Session, Runnable)} methods
 * that send {@link HttpServletResponse#SC_SERVICE_UNAVAILABLE}, {@link CloseCodes#TRY_AGAIN_LATER}
 * respectively if this executor rejects a task.
 */
public class ContextTrackingExecutor extends pl.morgwai.base.guice.scopes.ContextTrackingExecutor {



	/**
	 * Calls {@link #execute(Runnable) execute(task)} and if it's rejected sends
	 * {@link HttpServletResponse#SC_SERVICE_UNAVAILABLE} to {@code response}.
	 */
	public void execute(HttpServletResponse response, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			try {
				response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
			} catch (IOException e1) {}  // broken connection or status already sent
		}
	}



	/**
	 * Calls {@link #execute(Runnable) execute(task)} and if it's rejected closes {@code connection}
	 * with {@link CloseCodes#TRY_AGAIN_LATER}.
	 */
	public void execute(Session connection, Runnable task) {
		try {
			execute(task);
		} catch (RejectedExecutionException e) {
			try {
				connection.close(new CloseReason(CloseCodes.TRY_AGAIN_LATER, "service overloaded"));
			} catch (IOException e1) {}  // broken connection
		}
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses a
	 * {@link NamedThreadFactory} and an unbound {@link LinkedBlockingQueue}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or frontend) should be used.</p>
	 */
	public ContextTrackingExecutor(String name, int poolSize, ContextTracker<?>... trackers) {
		super(name, poolSize, trackers);
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses a
	 * {@link NamedThreadFactory}.
	 * <p>
	 * {@link #execute(Runnable)} throws a {@link RejectedExecutionException} if {@code workQueue}
	 * is full. It should usually be handled by sending status {@code 503 Service Unavailable} to
	 * the client.</p>
	 */
	public ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, trackers);
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor}.
	 * <p>
	 * {@link #execute(Runnable)} throws a {@link RejectedExecutionException} if {@code workQueue}
	 * is full. It should usually be handled by sending status {@code 503 Service Unavailable} to
	 * the client.</p>
	 */
	public ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, threadFactory, trackers);
	}



	/**
	 * Constructs an instance backed by {@code backingExecutor}.
	 * <p>
	 * <b>NOTE:</b> {@code backingExecutor.execute(task)} must throw
	 * {@link RejectedExecutionException} in case of rejection for
	 * {@link #execute(HttpServletResponse, Runnable)} to work properly.</p>
	 * <p>
	 * {@code poolSize} is informative only, to be returned by {@link #getPoolSize()}.</p>
	 */
	public ContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			ContextTracker<?>... trackers) {
		super(name, backingExecutor, poolSize, trackers);
	}
}
