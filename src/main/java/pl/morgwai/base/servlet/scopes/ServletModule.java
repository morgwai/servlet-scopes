/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.base.servlet.scopes;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;

import pl.morgwai.base.guice.scopes.ContextScope;
import pl.morgwai.base.guice.scopes.ContextTracker;
import pl.morgwai.base.guice.scopes.ContextTrackingExecutor;
import pl.morgwai.base.guice.scopes.ServerSideContext;



/**
 * Servlet Guice <code>Scope</code>s, <code>ContextTracker</code>s and some helper methods.
 */
public class ServletModule implements Module {



	public final ContextTracker<RequestContext> requestContextTracker =
			new InternalContextTracker<>();
	public final Scope requestScope = new ContextScope<>("REQUEST_sCOPE", requestContextTracker);



	public final Scope httpSessionScope = new Scope() {

		@Override
		public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
			return () -> {
				ServerSideContext sessionContext =
						requestContextTracker.getCurrentContext().getHttpSessionContext();
				@SuppressWarnings("unchecked")
				T instance = (T) sessionContext.getAttribute(key);
				if (instance == null) {
					instance = unscoped.get();
					sessionContext.setAttribute(key, instance);
				}
				return instance;
			};
		}

		@Override
		public String toString() {
			return "HTTP_SESSION_SCOPE";
		}
	};



	public final ContextTracker<WebsocketConnectionContext> websocketConnectionContextTracker =
			new InternalContextTracker<>();
	public final Scope websocketConnectionScope =
			new ContextScope<>("WEBSOCKET_CONNECTION_SCOPE", websocketConnectionContextTracker);



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
	 * Convenience "constructor" for <code>ContextTrackingExecutor</code>. (I really miss method
	 * extensions in Java)
	 */
	public ContextTrackingExecutor newContextTrackingExecutor(
			String name,
			int poolSize,
			BlockingQueue<Runnable> workQueue,
			ThreadFactory threadFactory,
			RejectedExecutionHandler handler) {
		return new ContextTrackingExecutor(
				name, poolSize, workQueue, threadFactory, handler,
				requestContextTracker, websocketConnectionContextTracker);
	}
}
