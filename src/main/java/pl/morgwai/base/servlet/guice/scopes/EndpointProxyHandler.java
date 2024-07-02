// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.servlet.http.HttpSession;
import javax.websocket.Session;

import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Executes each call to its wrapped {@code Endpoint} within websocket {@code Contexts}.
 * Creates a new separate {@link WebsocketEventContext} for each method invocation with references
 * to the current {@link WebsocketConnectionContext} and {@link HttpSessionContext} (if present).
 */
class EndpointProxyHandler implements InvocationHandler {



	final InvocationHandler wrappedEndpoint;
	final ContextTracker<ContainerCallContext> ctxTracker;



	EndpointProxyHandler(
		InvocationHandler endpointToWrap,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		this.wrappedEndpoint = endpointToWrap;
		this.ctxTracker = ctxTracker;
	}



	// the below 3 are created/retrieved in initialize(...) below
	WebsocketConnectionProxy connectionProxy;
	WebsocketConnectionContext connectionCtx;
	HttpSession httpSession;



	/**
	 * Initializes state using {@code connection}.
	 * Called by {@link #invoke(Object, Method, Object[])} when {@code onOpen(...)} call is
	 * intercepted.
	 */
	void initialize(Session connection) {
		final var userProperties = connection.getUserProperties();
		httpSession = (HttpSession) userProperties.get(HttpSession.class.getName());
		connectionProxy = WebsocketConnectionProxy.newProxy(connection, ctxTracker);
		connectionCtx = new WebsocketConnectionContext(connectionProxy);
	}



	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		// replace the original wsConnection (Session) arg with connectionProxy
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					if (connectionProxy == null) initialize((Session) args[i]);  // onOpen(...)
					args[i] = connectionProxy;
					break;
				}
			}
		}

		if (connectionCtx == null) {
			// the first call to this Endpoint and it's NOT onOpen(...) : probably a debugger call,
			// most usually toString(). initializeOnOpen(...) hasn't been called yet: call the
			// method outside of contexts and hope for the best...
			logManualCallWarning(proxy.getClass().getSimpleName() + '.' + method.getName());
			return wrappedEndpoint.invoke(proxy, method, args);
		}

		// execute the method within contexts
		return new WebsocketEventContext(connectionCtx, httpSession, ctxTracker).executeWithinSelf(
			() -> {
				try {
					return wrappedEndpoint.invoke(proxy, method, args);
				} catch (Error | Exception e) {
					throw e;
				} catch (Throwable neverHappens) {
					throw new Exception(neverHappens);  // result of mis-designed invoke() signature
				}
			}
		);
	}

	void logManualCallWarning(String source) {
		final var manualCallWarningMessage = source + MANUAL_CALL_WARNING;
		log.warning(manualCallWarningMessage);
		System.err.println(manualCallWarningMessage);
	}

	static final Logger log = Logger.getLogger(EndpointProxyHandler.class.getName());
	static final String MANUAL_CALL_WARNING = ": calling manually methods of Endpoints, that were "
			+ "designed to run within contexts, may lead to an OutOfScopeException";
}
