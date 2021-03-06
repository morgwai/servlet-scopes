// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

import com.google.inject.*;
import com.google.inject.Module;

import pl.morgwai.base.concurrent.Awaitable;
import pl.morgwai.base.concurrent.Awaitable.AwaitInterruptedException;
import pl.morgwai.base.guice.scopes.*;



/**
 * Contains servlet and websocket Guice {@link Scope}s, {@link ContextTracker}s and some helper
 * methods. A single app-wide instance is created at app startup:
 * {@link GuiceServletContextListener#servletModule}.
 */
public class ServletModule implements Module {



	/**
	 * Allows tracking of {@link ServletRequestContext}s and {@link WebsocketEventContext}s.
	 */
	public final ContextTracker<ContainerCallContext> containerCallContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes bindings to either a {@link ServletRequestContext} or a {@link WebsocketEventContext}.
	 * Objects bound in this scope can be obtained both in servlets and endpoints.
	 * @see ContainerCallContext
	 */
	public final Scope containerCallScope =
			new ContextScope<>("CONTAINER_CALL_SCOPE", containerCallContextTracker);



	/**
	 * Scopes bindings to the context of a given {@link javax.servlet.http.HttpSession}. Available
	 * both to servlets and websocket endpoints.
	 * <p>
	 * <b>NOTE:</b> there's no way to create an {@link javax.servlet.http.HttpSession} from the
	 * websocket endpoint layer if it does not exist yet. To safely use this scope in websocket
	 * endpoints, other layers must ensure that a session exists (for example a
	 * {@link javax.servlet.Filter} targeting URL patterns of websockets can be used).</p>
	 */
	public final Scope httpSessionScope = new InducedContextScope<>(
			"HTTP_SESSION_SCOPE",
			containerCallContextTracker,
			ContainerCallContext::getHttpSessionContext);



	/**
	 * Scopes bindings to the {@link WebsocketConnectionContext context of a websocket connection
	 * (javax.websocket.Session)}.
	 */
	public final Scope websocketConnectionScope = new InducedContextScope<>(
		"WEBSOCKET_CONNECTION_SCOPE",
		containerCallContextTracker,
		containerCallCtx -> ((WebsocketEventContext) containerCallCtx).getConnectionContext()
	);



	/**
	 * Contains all trackers. {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it
	 * for use with {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(containerCallContextTracker);



	/**
	 * Binds {@link #containerCallContextTracker} and all contexts for injection.
	 * Binds {@code List<ContextTracker<?>>} to {@link #allTrackers} that contains all trackers for
	 * use with {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<ContainerCallContext>> containerCallContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(containerCallContextTrackerType).toInstance(containerCallContextTracker);
		binder.bind(ContainerCallContext.class)
				.toProvider(containerCallContextTracker::getCurrentContext);
		binder.bind(HttpSessionContext.class).toProvider(
				() -> containerCallContextTracker.getCurrentContext().getHttpSessionContext());
		binder.bind(WebsocketConnectionContext.class).toProvider(
			() -> (
				((WebsocketEventContext) containerCallContextTracker.getCurrentContext())
						.getConnectionContext()
			)
		);

		TypeLiteral<List<ContextTracker<?>>> trackersType = new TypeLiteral<>() {};
		binder.bind(trackersType).toInstance(allTrackers);
	}



	final List<ContextTrackingExecutor> executors = new LinkedList<>();



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor} that uses a
	 * {@link ContextTrackingExecutor.NamedThreadFactory NamedThreadFactory} and an unbound
	 * {@link java.util.concurrent.LinkedBlockingQueue}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or frontend) should be used.</p>
	 * <p>
	 * Returned executor will be shutdown automatically at app shutdown.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		var executor = new ContextTrackingExecutor(name, poolSize, allTrackers);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor} that uses a
	 * {@link ContextTrackingExecutor.NamedThreadFactory NamedThreadFactory}.
	 * <p>
	 * {@link ContextTrackingExecutor#execute(Runnable)} throws a
	 * {@link java.util.concurrent.RejectedExecutionException} if {@code workQueue} is full. It
	 * should usually be handled by sending
	 * {@link javax.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} to the client.</p>
	 * <p>
	 * Returned executor will be shutdown automatically at app shutdown.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue) {
		var executor = new ContextTrackingExecutor(name, poolSize, workQueue, allTrackers);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor}.
	 * <p>
	 * {@link ContextTrackingExecutor#execute(Runnable)} throws a
	 * {@link java.util.concurrent.RejectedExecutionException} if {@code workQueue} is full. It
	 * should usually be handled by sending
	 * {@link javax.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} to the client.</p>
	 * <p>
	 * Returned executor will be shutdown automatically at app shutdown.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory) {
		var executor =
				new ContextTrackingExecutor(name, poolSize, workQueue, threadFactory, allTrackers);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an executor backed by {@code backingExecutor}.
	 * <p>
	 * <b>NOTE:</b> {@code backingExecutor.execute(task)} must throw
	 * {@link java.util.concurrent.RejectedExecutionException} in case of rejection for
	 * {@link ContextTrackingExecutor#execute(javax.servlet.http.HttpServletResponse, Runnable)
	 * execute(httpResponse, task)} to work properly.</p>
	 * <p>
	 * {@code poolSize} is informative only, to be returned by
	 * {@link ContextTrackingExecutor#getPoolSize()}.</p>
	 * <p>
	 * Returned executor will be shutdown automatically at app shutdown.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize) {
		var executor = new ContextTrackingExecutor(name, backingExecutor, poolSize, allTrackers);
		executors.add(executor);
		return executor;
	}



	@SuppressWarnings("unchecked")
	List<ContextTrackingExecutor> shutdownAndEnforceTerminationOfAllExecutors(
			int timeoutSeconds) {
		for (var executor: executors) executor.shutdownInternal();
		try {
			return Awaitable.awaitMultiple(
					timeoutSeconds,
					TimeUnit.SECONDS,
					ContextTrackingExecutor::awaitableOfEnforceTermination,
					executors);
		} catch (AwaitInterruptedException e) {
			final List<ContextTrackingExecutor> unterminated =
					(List<ContextTrackingExecutor>) e.getFailed();
			unterminated.addAll((List<ContextTrackingExecutor>) e.getInterrupted());
			return unterminated;
		}
	}
}
