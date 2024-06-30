// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.concurrent.*;

import pl.morgwai.base.guice.scopes.ContextBinder;
import pl.morgwai.base.guice.scopes.ContextBoundRunnable;
import pl.morgwai.base.utils.concurrent.Awaitable;



// todo: javadoc
public class ExecutorManager {



	final List<ServletContextTrackingExecutor> executors = new LinkedList<>();
	final ContextBinder ctxBinder;



	public ExecutorManager(ContextBinder ctxBinder) {
		this.ctxBinder = ctxBinder;
	}



	/** List of all {@code Executors} created by this {@code ExecutorManager}. */
	public List<ServletContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}



	/**
	 * Constructs a fixed size, {@link ServletContextTrackingExecutor} that uses an unbound
	 * {@link LinkedBlockingQueue} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named {@code name}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or a frontend proxy) should be used.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		final var executor = new ServletContextTrackingExecutor(name, ctxBinder, poolSize);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs a fixed size, {@link ServletContextTrackingExecutor} that uses a
	 * {@link LinkedBlockingQueue} of size {@code queueSize}, the default
	 * {@link RejectedExecutionHandler} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named {@code name}.
	 * <p>
	 * The default {@link RejectedExecutionHandler} throws a {@link RejectedExecutionException} if
	 * the queue is full or the {@code Executor} is shutting down. It should usually be handled by
	 * sending {@link javax.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} /
	 * {@link javax.websocket.CloseReason.CloseCodes#TRY_AGAIN_LATER} to the client.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		int queueSize
	) {
		final var executor =
				new ServletContextTrackingExecutor(name, ctxBinder, poolSize, queueSize);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs a new {@link ServletContextTrackingExecutor}.
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 *     ThreadFactory) ThreadPoolExecutor constructor docs for param details
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory
	) {
		final var executor = new ServletContextTrackingExecutor(
			name,
			ctxBinder,
			corePoolSize,
			maxPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory
		);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs a new context tracking {@link ServletContextTrackingExecutor}.
	 * <p>
	 * {@code rejectionHandler} will receive a task wrapped with a {@link ContextBoundRunnable}.</p>
	 * <p>
	 * In order for {@link ServletContextTrackingExecutor#execute(
	 * javax.servlet.http.HttpServletResponse, Runnable)} and
	 * {@link ServletContextTrackingExecutor#execute(javax.websocket.Session, Runnable)} to work
	 * properly, the {@code rejectionHandler} must throw a {@link RejectedExecutionException}.</p>
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 *     ThreadFactory, RejectedExecutionHandler) ThreadPoolExecutor constructor docs for param
	 *     details
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int corePoolSize,
		int maxPoolSize,
		long keepAliveTime,
		TimeUnit unit,
		BlockingQueue<Runnable> workQueue,
		ThreadFactory threadFactory,
		RejectedExecutionHandler handler
	) {
		final var executor = new ServletContextTrackingExecutor(
			name,
			ctxBinder,
			corePoolSize,
			maxPoolSize,
			keepAliveTime,
			unit,
			workQueue,
			threadFactory,
			handler
		);
		executors.add(executor);
		return executor;
	}



	/** Shutdowns all executors obtained from this {@code ExecutorManager}. */
	public void shutdownAllExecutors() {
		for (var executor: executors) executor.shutdown();
	}



	/**
	 * {@link ServletContextTrackingExecutor#toAwaitableOfEnforcedTermination() Enforces
	 * termination} of all {@code Executors} obtained from this {@code ExecutorManager}.
	 * @return an empty list if all {@code Executors} were terminated, a list of unterminated ones
	 *     otherwise.
	 */
	public List<ServletContextTrackingExecutor> enforceTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			executors.stream().map(Awaitable.entryMapper(
					ServletContextTrackingExecutor::toAwaitableOfEnforcedTermination))
		);
	}



	/**
	 * {@link ServletContextTrackingExecutor#toAwaitableOfTermination() Awaits for termination} of
	 * all {@code Executors} obtained from this {@code ExecutorManager}.
	 * @return an empty list if all {@code Executors} were terminated, a list of unterminated ones
	 *     otherwise.
	 */
	public List<ServletContextTrackingExecutor> awaitTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			executors.stream().map(Awaitable.entryMapper(
					ServletContextTrackingExecutor::toAwaitableOfTermination))
		);
	}



	/**
	 * {@link ServletContextTrackingExecutor#awaitTermination() Awaits for termination} of all
	 * {@code Executors} obtained from this {@code ExecutorManager}.
	 */
	public void awaitTerminationOfAllExecutors() throws InterruptedException {
		for (var executor: executors) executor.awaitTermination();
	}



	/**
	 * Creates {@link Awaitable.WithUnit} of
	 * {@link #enforceTerminationOfAllExecutors(long, TimeUnit)}.
	 */
	public Awaitable.WithUnit toAwaitableOfEnforcedTerminationOfAllExecutors() {
		return (timeout, unit) -> enforceTerminationOfAllExecutors(timeout, unit).isEmpty();
	}



	/**
	 * Creates {@link Awaitable.WithUnit} of
	 * {@link #awaitTerminationOfAllExecutors(long, TimeUnit)}.
	 */
	public Awaitable.WithUnit toAwaitableOfTerminationOfAllExecutors() {
		return (timeout, unit) -> awaitTerminationOfAllExecutors(timeout, unit).isEmpty();
	}
}
