// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

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
 * <p>
 * Instances can be created using {@link ServletModule#newContextTrackingExecutor(String, int)}
 * helper methods family.</p>
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



	ContextTrackingExecutor(String name, int poolSize, ContextTracker<?>... trackers) {
		super(name, poolSize, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, trackers);
	}



	ContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			ContextTracker<?>... trackers) {
		super(name, poolSize, workQueue, threadFactory, trackers);
	}



	ContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize,
			ContextTracker<?>... trackers) {
		super(name, backingExecutor, poolSize, trackers);
	}
}
