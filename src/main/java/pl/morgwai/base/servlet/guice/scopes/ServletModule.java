// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

import javax.servlet.http.HttpServletResponse;

import com.google.inject.Module;
import com.google.inject.*;

import pl.morgwai.base.guice.scopes.*;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor.DetailedRejectedExecutionException;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor.NamedThreadFactory;
import pl.morgwai.base.util.concurrent.Awaitable;
import pl.morgwai.base.util.concurrent.Awaitable.AwaitInterruptedException;



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
		ContainerCallContext::getHttpSessionContext
	);



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
	 * for use with {@link ServletContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(containerCallContextTracker);



	/**
	 * Binds {@link #containerCallContextTracker} and all contexts for injection.
	 * Binds {@code List<ContextTracker<?>>} to {@link #allTrackers} that contains all trackers for
	 * use with {@link ServletContextTrackingExecutor#getActiveContexts(List)}.
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



	final List<ServletContextTrackingExecutor> executors = new LinkedList<>();



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses an
	 * unbound {@link LinkedBlockingQueue} and a new {@link NamedThreadFactory}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or a frontend proxy) should be used.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		var executor = new ServletContextTrackingExecutor(name, poolSize, allTrackers);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses
	 * {@code workQueue}, the default {@link RejectedExecutionHandler} and a new
	 * {@link NamedThreadFactory} named after this executor.
	 * <p>
	 * The default {@link RejectedExecutionHandler} throws a
	 * {@link DetailedRejectedExecutionException} if {@code workQueue} is full or the executor is
	 * shutting down. It should usually be handled by sending
	 * {@link HttpServletResponse#SC_SERVICE_UNAVAILABLE} to the client.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue
	) {
		var executor = new ServletContextTrackingExecutor(name, poolSize, allTrackers, workQueue);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses
	 * {@code workQueue}, {@code rejectionHandler} and a new {@link NamedThreadFactory} named after
	 * this executor.
	 * <p>
	 * The first param of {@code rejectionHandler} is a rejected task: either {@link Runnable} or
	 * {@link Callable} depending whether {@link ServletContextTrackingExecutor#execute(Runnable)}
	 * or {@link ServletContextTrackingExecutor#execute(Callable)} was used.</p>
	 * <p>
	 * In order for {@link ServletContextTrackingExecutor#execute(HttpServletResponse, Runnable)} to
	 * work properly, the {@code rejectionHandler} must throw a {@link RejectedExecutionException}.
	 * </p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ? super ServletContextTrackingExecutor> rejectionHandler
	) {
		var executor = new ServletContextTrackingExecutor(
				name, poolSize, allTrackers, workQueue, rejectionHandler);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by a new fixed size {@link ThreadPoolExecutor} that uses
	 * {@code workQueue}, {@code rejectionHandler} and {@code threadFactory}.
	 * @see #newContextTrackingExecutor(String, int, BlockingQueue, BiConsumer)
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		BiConsumer<Object, ServletContextTrackingExecutor> rejectionHandler,
		ThreadFactory threadFactory
	) {
		var executor = new ServletContextTrackingExecutor(
				name, poolSize, allTrackers, workQueue, rejectionHandler, threadFactory);
		executors.add(executor);
		return executor;
	}



	/**
	 * Constructs an instance backed by {@code backingExecutor}. A {@link RejectedExecutionHandler}
	 * of the {@code backingExecutor} will receive a {@link Runnable} that consists of several
	 * layers of wrappers around the original task, use
	 * {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor#unwrapRejectedTask(Runnable)} to
	 * obtain the original task.
	 * @param poolSize informative only: to be returned by
	 *     {@link pl.morgwai.base.guice.scopes.ContextTrackingExecutor#getPoolSize()}.
	 * @see #newContextTrackingExecutor(String, int, BlockingQueue, BiConsumer)
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		ExecutorService backingExecutor
	) {
		final var executor =
				new ServletContextTrackingExecutor(name, poolSize, allTrackers, backingExecutor);
		executors.add(executor);
		return executor;
	}



	List<ServletContextTrackingExecutor> shutdownAndEnforceTerminationOfAllExecutors(
		int timeoutSeconds
	) {
		for (var executor: executors) executor.packageProtectedShutdown();
		try {
			return Awaitable.awaitMultiple(
				timeoutSeconds,
				TimeUnit.SECONDS,
				ServletContextTrackingExecutor::toAwaitableOfEnforceTermination,
				executors
			);
		} catch (AwaitInterruptedException e) {
			final var unterminated = new ArrayList<ServletContextTrackingExecutor>(
					e.getFailed().size() + e.getInterrupted().size());
			@SuppressWarnings("unchecked")
			final var failed = (List<ServletContextTrackingExecutor>) e.getFailed();
			unterminated.addAll(failed);
			@SuppressWarnings("unchecked")
			final var interrupted = (List<ServletContextTrackingExecutor>) e.getInterrupted();
			unterminated.addAll(interrupted);
			return unterminated;
		}
	}
}
