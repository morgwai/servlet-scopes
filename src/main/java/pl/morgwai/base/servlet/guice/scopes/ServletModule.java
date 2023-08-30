// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.concurrent.*;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;
import pl.morgwai.base.utils.concurrent.Awaitable;



/**
 * Contains servlet and websocket Guice {@link Scope}s, {@link ContextTracker}s and some helper
 * methods. A single app-wide instance is created at app startup:
 * {@link GuiceServletContextListener#servletModule}.
 */
public class ServletModule implements Module {



	/** Allows tracking of {@link ServletRequestContext}s and {@link WebsocketEventContext}s. */
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
	 * Scopes bindings to the context of a given {@link jakarta.servlet.http.HttpSession}. Available
	 * both to servlets and websocket endpoints.
	 * <p>
	 * <b>NOTE:</b> there's no way to create an {@link jakarta.servlet.http.HttpSession} from the
	 * websocket endpoint layer if it does not exist yet. To safely use this scope in websocket
	 * endpoints, other layers must ensure that a session exists (for example a
	 * {@link jakarta.servlet.Filter} targeting URL patterns of websockets can be used).</p>
	 * <p>
	 * <b>NOTE:</b> similarly as with
	 * {@link jakarta.servlet.http.HttpSession#setAttribute(String, Object) session attributes}, it is
	 * recommended for session-scoped objects to be {@link java.io.Serializable}.</p>
	 */
	public final Scope httpSessionScope = new InducedContextScope<>(
		"HTTP_SESSION_SCOPE",
		containerCallContextTracker,
		ContainerCallContext::getHttpSessionContext
	);



	/**
	 * Scopes bindings to the {@link WebsocketConnectionContext context of a websocket connection
	 * (jakarta.websocket.Session)}.
	 */
	public final Scope websocketConnectionScope = new InducedContextScope<>(
		"WEBSOCKET_CONNECTION_SCOPE",
		containerCallContextTracker,
		containerCallCtx -> ((WebsocketEventContext) containerCallCtx).getConnectionContext()
	);



	/**
	 * Contains all trackers. {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it
	 * for use with {@link ContextTracker#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(containerCallContextTracker);



	static final TypeLiteral<ContextTracker<ContainerCallContext>> containerCallContextTrackerType =
			new TypeLiteral<>() {};
	/** Guice {@code Key} for {@link #containerCallContextTracker}. For internal purposes mostly .*/
	public static final Key<ContextTracker<ContainerCallContext>> containerCallContextTrackerKey =
			Key.get(containerCallContextTrackerType);



	/** Set in {@link GuiceServletContextListener#contextInitialized(ServletContextEvent)}. */
	ServletContext appDeployment;

	/** For {@link GuiceServletContextListener#servletModule}. */
	ServletModule() {}

	/**
	 * Creates a new module. For usage in apps that don't use {@link GuiceServletContextListener}.
	 */
	public ServletModule(ServletContext appDeployment) {
		this.appDeployment = appDeployment;
	}



	/**
	 * Creates infrastructure bindings. Specifically, binds the following:
	 * <ul>
	 *   <li>{@link ServletContext}</li>
	 *   <li>Their respective types to {@link #containerCallContextTracker} and all 3 contexts</li>
	 *   <li>{@code List<ContextTracker<?>>} to {@link #allTrackers}</li>
	 *   <li>{@link ContextBinder} to {@code new ContextBinder(allTrackers)}</li>
	 * </ul>
	 */
	@Override
	public void configure(Binder binder) {
		binder.bind(ServletContext.class).toInstance(appDeployment);
		binder.bind(containerCallContextTrackerKey).toInstance(containerCallContextTracker);
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
		binder.bind(ContextBinder.class).toInstance(new ContextBinder(allTrackers));
	}



	/** List of all executors created by this module. */
	public List<ServletContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}

	final List<ServletContextTrackingExecutor> executors = new LinkedList<>();



	/**
	 * Constructs a fixed size, context tracking executor that uses an unbound
	 * {@link LinkedBlockingQueue} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or a frontend proxy) should be used.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		final var executor = new ServletContextTrackingExecutor(name, allTrackers, poolSize);
		executors.add(executor);
		return executor;
	}

	/**
	 * Constructs a fixed size, context tracking executor that uses a {@link LinkedBlockingQueue} of
	 * size {@code queueSize}, the default {@link RejectedExecutionHandler} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * The default {@link RejectedExecutionHandler} throws a {@link RejectedExecutionException} if
	 * the queue is full or the executor is shutting down. It should usually be handled by
	 * sending {@link jakarta.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} /
	 * {@link jakarta.websocket.CloseReason.CloseCodes#TRY_AGAIN_LATER} to the client.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		int queueSize
	) {
		final var executor =
				new ServletContextTrackingExecutor(name, allTrackers, poolSize, queueSize);
		executors.add(executor);
		return executor;
	}

	/**
	 * Constructs a fixed size, context tracking executor that uses {@code workQueue},
	 * {@code rejectionHandler} and a new
	 * {@link pl.morgwai.base.utils.concurrent.NamingThreadFactory} named after this executor.
	 * <p>
	 * {@code rejectionHandler} will receive a task wrapped with a {@link ContextBoundRunnable}.</p>
	 * <p>
	 * In order for {@link ServletContextTrackingExecutor#execute(
	 * jakarta.servlet.http.HttpServletResponse, Runnable)} and
	 * {@link ServletContextTrackingExecutor#execute(jakarta.websocket.Session, Runnable)} to work
	 * properly, the {@code rejectionHandler} must throw a {@link RejectedExecutionException}.</p>
	 */
	public ServletContextTrackingExecutor newContextTrackingExecutor(
		String name,
		int poolSize,
		BlockingQueue<Runnable> workQueue,
		RejectedExecutionHandler rejectionHandler
	) {
		final var executor = new ServletContextTrackingExecutor(
				name, allTrackers, poolSize, workQueue, rejectionHandler);
		executors.add(executor);
		return executor;
	}

	/**
	 * Constructs a context tracking executor.
	 * @see ThreadPoolExecutor#ThreadPoolExecutor(int, int, long, TimeUnit, BlockingQueue,
	 *     ThreadFactory, RejectedExecutionHandler) ThreadPoolExecutor constructor docs for param
	 *     details
	 * @see #newContextTrackingExecutor(String, int, BlockingQueue, RejectedExecutionHandler)
	 *     notes on <code>rejectionHandler</code>
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
			allTrackers,
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



	/** Shutdowns all executors obtained from this module. */
	public void shutdownAllExecutors() {
		for (var executor: executors) executor.shutdown();
	}

	/**
	 * {@link ServletContextTrackingExecutor#toAwaitableOfEnforcedTermination() Enforces
	 * termination} of all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<ServletContextTrackingExecutor> enforceTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			ServletContextTrackingExecutor::toAwaitableOfEnforcedTermination,
			executors
		);
	}

	/**
	 * {@link ServletContextTrackingExecutor#toAwaitableOfTermination() Awaits for termination} of
	 * all executors obtained from this module.
	 * @return an empty list if all executors were terminated, list of unterminated otherwise.
	 */
	public List<ServletContextTrackingExecutor> awaitTerminationOfAllExecutors(
		long timeout,
		TimeUnit unit
	) throws InterruptedException {
		return Awaitable.awaitMultiple(
			timeout,
			unit,
			ServletContextTrackingExecutor::toAwaitableOfTermination,
			executors
		);
	}

	/**
	 * {@link ServletContextTrackingExecutor#awaitTermination() Awaits for termination} of all
	 * executors obtained from this module.
	 */
	public void awaitTerminationOfAllExecutors() throws InterruptedException {
		for (var executor: executors) executor.awaitTermination();
	}

	/**
	 * Creates {@link Awaitable.WithUnit} of
	 * {@link #enforceTerminationOfAllExecutors(long, TimeUnit)}.
	 */
	public Awaitable.WithUnit toAwaitableOfEnforcedTerminationOfAllExecutors() {
		shutdownAllExecutors();
		return (timeout, unit) -> enforceTerminationOfAllExecutors(timeout, unit).isEmpty();
	}

	/**
	 * Creates {@link Awaitable.WithUnit} of
	 * {@link #awaitTerminationOfAllExecutors(long, TimeUnit)}.
	 */
	public Awaitable.WithUnit toAwaitableOfTerminationOfAllExecutors() {
		shutdownAllExecutors();
		return (timeout, unit) -> awaitTerminationOfAllExecutors(timeout, unit).isEmpty();
	}
}
