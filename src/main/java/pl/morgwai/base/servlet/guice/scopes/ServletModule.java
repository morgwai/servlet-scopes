// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import com.google.inject.Module;
import com.google.inject.*;
import pl.morgwai.base.guice.scopes.*;
import pl.morgwai.base.utils.concurrent.Awaitable;



/**
 * Contains {@code Servlet} and websocket Guice {@link Scope}s, {@link ContextTracker}s and some
 * helper methods.
 * Usually a single app-wide instance is created at the app startup.
 * @see GuiceServletContextListener#servletModule
 */
public class ServletModule implements Module {



	/** Allows tracking of {@link ServletRequestContext}s and {@link WebsocketEventContext}s. */
	public final ContextTracker<ContainerCallContext> containerCallContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes objects to the {@code Context} of either an
	 * {@link ServletRequestContext HttpServletRequests}
	 * or a {@link WebsocketEventContext websocket events}, depending within which type a given
	 * {@code  Thread} runs (as returned by {@link #containerCallContextTracker}).
	 */
	public final Scope containerCallScope =
			new ContextScope<>("containerCallScope", containerCallContextTracker);



	/**
	 * Scopes objects to the {@link HttpSessionContext Context of an HttpSessions}.
	 * This {@code Scope} is induced by {@link ContainerCallContext}s obtained from
	 * {@link #containerCallContextTracker}, so it may be active both within
	 * {@link ServletRequestContext}s and {@link WebsocketEventContext}s.
	 * <p>
	 * <b>NOTE:</b> there's no way to create an {@link javax.servlet.http.HttpSession} from the
	 * websocket {@code Endpoint} layer if it didn't exist before. To safely use this {@code Scope}
	 * in websocket {@code Endpoints}, other layers must ensure that a {@code Session} exists (for
	 * example a {@link javax.servlet.Filter} targeting URL patterns of websockets can be used).</p>
	 * <p>
	 * <b>NOTE:</b> similarly as with
	 * {@link javax.servlet.http.HttpSession#setAttribute(String, Object) Session attributes},
	 * session-scoped objects must be {@link java.io.Serializable} if they need to be transferred
	 * between cluster nodes.</p>
	 * @see GuiceServletContextListener#addEnsureSessionFilter(String...)
	 */
	public final Scope httpSessionScope = new InducedContextScope<>(
		"httpSessionScope",
		containerCallContextTracker,
		ContainerCallContext::getHttpSessionContext
	);



	/**
	 * Scopes objects to the {@link WebsocketConnectionContext Context of a websocket connections
	 * (javax.websocket.Session)}.
	 * This {@code Scope} is induced by and active within <b>only</b>
	 * {@link WebsocketEventContext}s.
	 */
	public final Scope websocketConnectionScope = new InducedContextScope<>(
		"websocketConnectionScope",
		containerCallContextTracker,
		getWebsocketConnectionContext
	);

	static final Function<ContainerCallContext, WebsocketConnectionContext>
	getWebsocketConnectionContext = (eventCtx) -> {
		try {
			return ((WebsocketEventContext) eventCtx).getConnectionContext();
		} catch (ClassCastException e) {
			throw new OutOfScopeException("cannot provide a websocketConnectionScope bound "
					+ "object within a ServletRequestContext");
		}
	};



	/**
	 * Singleton of {@link #containerCallContextTracker}.
	 * Type {@code List<ContextTracker<?>>} is bound to it in {@link #configure(Binder)} method.
	 */
	public final List<ContextTracker<?>> allTrackers = List.of(containerCallContextTracker);

	/** {@code ContextBinder} created with {@link #allTrackers}. */
	public final ContextBinder contextBinder = new ContextBinder(allTrackers);

	/** Calls {@link ContextTracker#getActiveContexts(List) getActiveContexts(allTrackers)}. */
	public List<TrackableContext<?>> getActiveContexts() {
		return ContextTracker.getActiveContexts(allTrackers);
	}



	static final TypeLiteral<ContextTracker<ContainerCallContext>> containerCallContextTrackerType =
			new TypeLiteral<>() {};
	static final TypeLiteral<List<ContextTracker<?>>> allTrackersType = new TypeLiteral<>() {};
	/** {@code Key} of {@link #containerCallContextTracker}. */
	public static final Key<ContextTracker<ContainerCallContext>> containerCallContextTrackerKey =
			Key.get(containerCallContextTrackerType);
	/** {@code Key} of {@link #allTrackers}. */
	public static final Key<List<ContextTracker<?>>> allTrackersKey = Key.get(allTrackersType);

	/** Set in {@link GuiceServletContextListener#contextInitialized(ServletContextEvent)}. */
	ServletContext appDeployment;



	/** Creates a new module. For use in apps that don't use {@link GuiceServletContextListener}. */
	public ServletModule(ServletContext appDeployment) {
		this.appDeployment = appDeployment;
	}

	/** For {@link GuiceServletContextListener#servletModule}. */
	ServletModule() {}



	/**
	 * Creates infrastructure bindings.
	 * Specifically binds the following:
	 * <ul>
	 *   <li>{@link #allTrackersKey} to {@link #allTrackers}</li>
	 *   <li>{@link ContextBinder} to {@link #contextBinder}</li>
	 *   <li>{@link ServletContext} to {@link #appDeployment}</li>
	 *   <li>{@link #containerCallContextTrackerKey} to {@link #containerCallContextTracker}</li>
	 *   <li>
	 *       {@link ContainerCallContext}, {@link WebsocketConnectionContext} and
	 *       {@link HttpSessionContext} to {@link Provider}s returning instances current for the
	 *       calling {@code Thread}
	 *   </li>
	 * </ul>
	 */
	@Override
	public void configure(Binder binder) {
		binder.bind(ServletContext.class).toInstance(appDeployment);
		binder.bind(allTrackersKey).toInstance(allTrackers);
		binder.bind(ContextBinder.class).toInstance(contextBinder);
		binder.bind(containerCallContextTrackerKey).toInstance(containerCallContextTracker);
		binder.bind(ContainerCallContext.class).toProvider(
				containerCallContextTracker::getCurrentContext);
		binder.bind(HttpSessionContext.class).toProvider(
			() -> containerCallContextTracker.getCurrentContext().getHttpSessionContext()
		);
		binder.bind(WebsocketConnectionContext.class).toProvider(
			() -> getWebsocketConnectionContext.apply(
				containerCallContextTracker.getCurrentContext()
			)
		);
	}



	final List<ServletContextTrackingExecutor> executors = new LinkedList<>();



	/** List of all {@code Executors} created by this {@code Module}. */
	public List<ServletContextTrackingExecutor> getExecutors() {
		return Collections.unmodifiableList(executors);
	}



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
	 * sending {@link javax.servlet.http.HttpServletResponse#SC_SERVICE_UNAVAILABLE} /
	 * {@link javax.websocket.CloseReason.CloseCodes#TRY_AGAIN_LATER} to the client.</p>
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
	 * Constructs a context tracking executor.
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
			allTrackers,
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
	 * Constructs a context tracking executor.
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
			executors.stream().map(Awaitable.entryMapper(
					ServletContextTrackingExecutor::toAwaitableOfEnforcedTermination))
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
			executors.stream().map(Awaitable.entryMapper(
					ServletContextTrackingExecutor::toAwaitableOfTermination))
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
