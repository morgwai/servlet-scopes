// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.InducedContextScope;



/**
 * Servlet and websocket Guice {@link Scope}s, {@link ContextTracker}s and some helper methods.
 * A single app-wide instance is created at app startup:
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
	 * Scopes bindings to the context of a given {@link jakarta.servlet.http.HttpSession}. Available
	 * both to servlets and websocket endpoints.
	 * <p>
	 * <b>NOTE:</b> there's no way to create an {@link jakarta.servlet.http.HttpSession} from the
	 * websocket endpoint layer if it does not exist yet. To safely use this scope in websocket
	 * endpoints, other layers must ensure that a session exists (for example a
	 * {@link jakarta.servlet.Filter} targeting URL patterns of websockets can be used).</p>
	 */
	public final Scope httpSessionScope = new InducedContextScope<>(
			"HTTP_SESSION_SCOPE",
			containerCallContextTracker,
			ContainerCallContext::getHttpSessionContext);



	/**
	 * Allows tracking of the {@link WebsocketConnectionContext context of a websocket connection
	 * (jakarta.websocket.Session)}.
	 */
	public final ContextTracker<WebsocketConnectionContext> websocketConnectionContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes bindings to the {@link WebsocketConnectionContext context of a websocket connection
	 * (jakarta.websocket.Session)}.
	 */
	public final Scope websocketConnectionScope =
			new ContextScope<>("WEBSOCKET_CONNECTION_SCOPE", websocketConnectionContextTracker);



	/**
	 * Contains all trackers. {@link #configure(Binder)} binds {@code List<ContextTracker<?>>} to it
	 * for use with {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	public final List<ContextTracker<?>> allTrackers =
			List.of(websocketConnectionContextTracker, containerCallContextTracker);



	/**
	 * Binds {@link #containerCallContextTracker} and {@link #websocketConnectionContextTracker} and
	 * corresponding contexts for injection. Binds {@code List<ContextTracker<?>>} to
	 * {@link #allTrackers} that contains all trackers for use with
	 * {@link ContextTrackingExecutor#getActiveContexts(List)}.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<ContainerCallContext>> requestContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(requestContextTrackerType).toInstance(containerCallContextTracker);
		binder.bind(ContainerCallContext.class)
				.toProvider(containerCallContextTracker::getCurrentContext);

		TypeLiteral<ContextTracker<WebsocketConnectionContext>>
				websocketConnectionContextTrackerType = new TypeLiteral<>() {};
		binder.bind(websocketConnectionContextTrackerType)
				.toInstance(websocketConnectionContextTracker);
		binder.bind(WebsocketConnectionContext.class).toProvider(
				websocketConnectionContextTracker::getCurrentContext);

		TypeLiteral<List<ContextTracker<?>>> trackersType = new TypeLiteral<>() {};
		binder.bind(trackersType).toInstance(allTrackers);
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor} that uses a
	 * {@link ContextTrackingExecutor.NamedThreadFactory NamedThreadFactory} and an unbound
	 * {@link java.util.concurrent.LinkedBlockingQueue}.
	 * <p>
	 * To avoid {@link OutOfMemoryError}s, an external mechanism that limits maximum number of tasks
	 * (such as a load balancer or frontend) should be used.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		return new ContextTrackingExecutor(name, poolSize, allTrackers);
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor} that uses a
	 * {@link ContextTrackingExecutor.NamedThreadFactory NamedThreadFactory}.
	 * <p>
	 * {@link ContextTrackingExecutor#execute(Runnable)} throws a
	 * {@link java.util.concurrent.RejectedExecutionException} if {@code workQueue} is full. It
	 * should usually be handled by sending
	 * {@link jakarta.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} to the client.
	 * </p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, allTrackers);
	}



	/**
	 * Constructs an executor backed by a new fixed size
	 * {@link java.util.concurrent.ThreadPoolExecutor}.
	 * <p>
	 * {@link ContextTrackingExecutor#execute(Runnable)} throws a
	 * {@link java.util.concurrent.RejectedExecutionException} if {@code workQueue} is full. It
	 * should usually be handled by sending
	 * {@link jakarta.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} to the client.
	 * </p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory) {
		return new ContextTrackingExecutor(name, poolSize, workQueue, threadFactory, allTrackers);
	}



	/**
	 * Constructs an executor backed by {@code backingExecutor}.
	 * <p>
	 * <b>NOTE:</b> {@code backingExecutor.execute(task)} must throw
	 * {@link java.util.concurrent.RejectedExecutionException} in case of rejection for
	 * {@link ContextTrackingExecutor#execute(jakarta.servlet.http.HttpServletResponse, Runnable)
	 * execute(httpResponse, task)} to work properly.</p>
	 * <p>
	 * {@code poolSize} is informative only, to be returned by
	 * {@link ContextTrackingExecutor#getPoolSize()}.</p>
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			ExecutorService backingExecutor,
			int poolSize) {
		return new ContextTrackingExecutor(name, backingExecutor, poolSize, allTrackers);
	}
}
