// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.util.concurrent.*;

import javax.servlet.http.HttpServletResponse;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextBinder;
import pl.morgwai.base.utils.concurrent.NamingThreadFactory;
import pl.morgwai.base.utils.concurrent.TaskTrackingThreadPoolExecutor;



/**
 * {@link TaskTrackingThreadPoolExecutor} that automatically transfer {@code Contexts} to worker
 * {@code Threads} using {@link ContextBinder}.
 * <p>
 * Instances should usually be created using
 * {@link ServletModule#newContextTrackingExecutor(String, int)
 * ServletModule.newContextTrackingExecutor(...)} helper methods family, so that they are
 * automatically terminated at the app shutdown.</p>
 */
public class ServletContextTrackingExecutor extends TaskTrackingThreadPoolExecutor {



	public String getName() { return name; }
	public final String name;

	final ContextBinder ctxBinder;



	/** See {@link ServletModule#newContextTrackingExecutor(String, int)}. */
	public ServletContextTrackingExecutor(String name, ContextBinder ctxBinder, int poolSize) {
		this(
			name,
			ctxBinder,
			poolSize,
			poolSize,
			0L,
			TimeUnit.DAYS,
			new LinkedBlockingQueue<>(),
			new NamingThreadFactory(name)
		);
	}



	@Override
	public void execute(Runnable task) {
		super.execute(ctxBinder.bindToContext(task));
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



	/**
	 * See {@link ServletModule#newContextTrackingExecutor(String, int, int, long, TimeUnit,
	 * BlockingQueue, ThreadFactory, RejectedExecutionHandler)}.
	 */
	public ServletContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
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
		this.ctxBinder = ctxBinder;
	}



	/**
	 * See {@link ServletModule#newContextTrackingExecutor(String, int, int, long, TimeUnit,
	 * BlockingQueue, ThreadFactory)}.
	 */
	public ServletContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory
	) {
		super(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue, threadFactory);
		this.name = name;
		this.ctxBinder = ctxBinder;
	}



	/** See {@link ServletModule#newContextTrackingExecutor(String, int, int)}. */
	public ServletContextTrackingExecutor(
		String name,
		ContextBinder ctxBinder,
		int poolSize,
		int queueSize
	) {
		this(
			name,
			ctxBinder,
			poolSize,
			poolSize,
			0L,
			TimeUnit.DAYS,
			new LinkedBlockingQueue<>(queueSize),
			new NamingThreadFactory(name)
		);
	}
}
