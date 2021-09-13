// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;



/**
 * Servlet Guice {@link Scope}s, {@link ContextTracker}s and some helper methods.
 * A single app-wide instance is created at app startup:
 * {@link GuiceServletContextListener#servletModule}
 */
public class ServletModule implements Module {



	public final ContextTracker<RequestContext> requestContextTracker = new ContextTracker<>();

	/**
	 * Scopes bindings to either a {@link ServletRequestContext} or a {@link WebsocketEventContext}
	 * (Objects bound to this scope can be obtained both in servlets and endpoints).
	 * @see RequestContext
	 */
	public final Scope requestScope = new ContextScope<>("REQUEST_sCOPE", requestContextTracker);



	/**
	 * Scopes bindings to a given <code>HttpSession</code>. Available both to servlets and websocket
	 * endpoints.<br/>
	 * <br/>
	 * <b>NOTE:</b> there's no way to create an <code>HttpSession</code> from the websocket endpoint
	 * layer if it does not exist before. To safely use this scope in websocket endpoints, other
	 * layers must ensure that a session exists (for example a <code>Filter</code> targeting URL
	 * patterns of websockets can be used).
	 */
	public final Scope httpSessionScope = new Scope() {

		@Override
		@SuppressWarnings("unchecked")
		public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
			return () -> {
				final var ctx = requestContextTracker.getCurrentContext();
				if (ctx == null) {
					throw new RuntimeException("no request context for thread "
							+ Thread.currentThread().getName());
				}
				return (T) ctx.getHttpSessionContextAttributes().computeIfAbsent(
						key, (ignored) -> unscoped.get());
			};
		}

		@Override
		public String toString() {
			return "HTTP_SESSION_SCOPE";
		}
	};



	public final ContextTracker<WebsocketConnectionContext> websocketConnectionContextTracker =
			new ContextTracker<>();

	/**
	 * Scopes bindings to a given {@link WebsocketConnectionContext}.
	 */
	public final Scope websocketConnectionScope =
			new ContextScope<>("WEBSOCKET_CONNECTION_SCOPE", websocketConnectionContextTracker);



	/**
	 * Binds {@link #requestContextTracker} and {@link #websocketConnectionContextTracker} and
	 * corresponding contexts for injection.
	 */
	@Override
	public void configure(Binder binder) {
		TypeLiteral<ContextTracker<RequestContext>> requestContextTrackerType =
				new TypeLiteral<>() {};
		binder.bind(requestContextTrackerType).toInstance(requestContextTracker);
		binder.bind(RequestContext.class).toProvider(
				() -> requestContextTracker.getCurrentContext());

		TypeLiteral<ContextTracker<WebsocketConnectionContext>>
				websocketConnectionContextTrackerType = new TypeLiteral<>() {};
		binder.bind(websocketConnectionContextTrackerType)
				.toInstance(websocketConnectionContextTracker);
		binder.bind(WebsocketConnectionContext.class).toProvider(
				() -> websocketConnectionContextTracker.getCurrentContext());
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>. (I really miss method
	 * extensions in Java)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(String name, int poolSize) {
		return new ContextTrackingExecutor(
				name, poolSize, requestContextTracker, websocketConnectionContextTracker);
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>.
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue) {
		return new ContextTrackingExecutor(
				name, poolSize, workQueue,
				requestContextTracker, websocketConnectionContextTracker);
	}



	/**
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>.
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int corePoolSize,
			int maximumPoolSize,
			long keepAliveTime,
			TimeUnit unit,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			RejectedExecutionHandler handler,
			ContextTracker<?>... trackers) {
		return new ContextTrackingExecutor(
				name,
				corePoolSize,
				maximumPoolSize,
				keepAliveTime,
				unit,
				workQueue,
				threadFactory,
				handler,
				requestContextTracker, websocketConnectionContextTracker);
	}
}
