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
 * Creates a new separate {@link WebsocketEventContext} for method invocation with links to the
 * current {@link WebsocketConnectionContext} and {@link HttpSessionContext} if it's present.
 */
class EndpointProxyHandler implements InvocationHandler {



	final InvocationHandler wrappedEndpoint;
	final ContextTracker<ContainerCallContext> ctxTracker;



	EndpointProxyHandler(
		InvocationHandler endpointToWrap,
		ContextTracker<ContainerCallContext> containerCallContextTracker
	) {
		this.wrappedEndpoint = endpointToWrap;
		this.ctxTracker = containerCallContextTracker;
	}



	// the below 3 are created/retrieved when onOpen(...) call is intercepted
	WebsocketConnectionProxy connectionProxy;
	WebsocketConnectionContext connectionCtx;
	HttpSession httpSession;



	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

		// replace the original wsConnection (Session) arg with a ctx-aware proxy
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				if (args[i] instanceof Session) {
					if (connectionProxy == null) {
						// the first call to this Endpoint instance that has a Session param (most
						// commonly onOpen(...)) : proxy the intercepted Session, create a new
						// connectionCtx, retrieve the HttpSession from userProperties
						final var connection = (Session) args[i];
						final var userProperties = connection.getUserProperties();
						httpSession = (HttpSession) userProperties.get(HttpSession.class.getName());
						connectionProxy = WebsocketConnectionProxy.newProxy(connection, ctxTracker);
						connectionCtx = new WebsocketConnectionContext(connectionProxy);
					}
					args[i] = connectionProxy;
					break;
				}
			}
		}

		if (connectionCtx == null) {
			// the first call to this Endpoint instance and it is NOT onOpen(...) : this is usually
			// a call from a debugger, most usually toString(). Session has not been intercepted
			// yet, so contexts couldn't have been created: just call the method outside of contexts
			// and hope for the best...
			final var manualCallWarningMessage =
					proxy.getClass().getSimpleName() + '.' + method.getName() + MANUAL_CALL_WARNING;
			log.warning(manualCallWarningMessage);
			System.err.println(manualCallWarningMessage);
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



	static final Logger log = Logger.getLogger(EndpointProxyHandler.class.getName());
	static final String MANUAL_CALL_WARNING = ": calling manually methods of Endpoints, that were "
			+ "designed to run within contexts, may lead to an OutOfScopeException";
}
