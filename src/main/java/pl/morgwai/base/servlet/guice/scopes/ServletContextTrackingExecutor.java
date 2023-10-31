// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextBoundRunnable;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;
import pl.morgwai.base.utils.concurrent.TaskTrackingThreadPoolExecutor;



/**
 * {@link TaskTrackingThreadPoolExecutor} that wraps tasks with {@link ContextBoundRunnable}
 * decorator to automatically transfer {@code Contexts}.
 * <p>
 * Instances should usually be created using
 * {@link ServletModule#newContextTrackingExecutor(String, int)
 * ServletModule.newContextTrackingExecutor(...)} helper methods family, so that they are
 * automatically terminated at the app shutdown.</p>
 */
public class ServletContextTrackingExecutor extends TaskTrackingThreadPoolExecutor {



	public String getName() { return name; }
	final String name;

	final List<ContextTracker<?>> trackers;



	public ServletContextTrackingExecutor(
			String name, List<ContextTracker<?>> trackers, int poolSize) {
		this(name, trackers, poolSize, new LinkedBlockingQueue<>());
	}



	@Override
	public void execute(Runnable task) {
		super.execute(new ContextBoundRunnable(ContextTracker.getActiveContexts(trackers), task));
	}



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



	@Override
	public String toString() {
		return "ServletContextTrackingExecutor { name=\"" + name + "\" }";
	}



	/** See {@link ServletModule#newContextTrackingExecutor(String, int, int)}. */
	public ServletContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		int queueSize
	) {
		this(name, trackers, poolSize, new LinkedBlockingQueue<>(queueSize));
	}

	public ServletContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		super(poolSize, poolSize, 0L, TimeUnit.DAYS, workQueue, new NamingThreadFactory(name));
		this.name = name;
		this.trackers = trackers;
	}

	/**
	 * See {@link ServletModule#newContextTrackingExecutor(String, int, BlockingQueue,
	 * RejectedExecutionHandler)}.
	 */
	public ServletContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler rejectionHandler
	) {
		this(
			name,
			trackers,
			poolSize,
			poolSize,
			0L,
			TimeUnit.SECONDS,
			workQueue,
			new NamingThreadFactory(name),
			rejectionHandler
		);
	}

	/**
	 * See {@link ServletModule#newContextTrackingExecutor(String, int, int, long, TimeUnit,
	 * BlockingQueue, ThreadFactory, RejectedExecutionHandler)}.
	 */
	public ServletContextTrackingExecutor(
		String name,
		List<ContextTracker<?>> trackers,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
		this.name = name;
		this.trackers = trackers;
	}

}
