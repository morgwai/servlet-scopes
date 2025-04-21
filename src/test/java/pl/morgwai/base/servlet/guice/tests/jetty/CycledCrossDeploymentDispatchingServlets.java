// Copyright 2025 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.*;

import pl.morgwai.base.servlet.guice.tests.servercommon.*;

import static javax.servlet.DispatcherType.FORWARD;



/**
 * Servlets for performing
 * {@link pl.morgwai.base.servlet.guice.tests.JettyTests#testCycledCrossDeploymentForwarding()} and
 * {@link pl.morgwai.base.servlet.guice.tests.JettyTests#testCycledCrossDeploymentIncluding()}.
 */
public interface CycledCrossDeploymentDispatchingServlets {



	/**
	 * First receives a normal {@link HttpServletRequest} and
	 * {@link javax.servlet.RequestDispatcher dispatches} it to {@link SecondDeploymentServlet} in
	 * {@link MultiAppServer#SECOND_APP_PATH the second deployment}, that dispatches it back to this
	 * {@code Servlet} (on another {@link Thread}), that then
	 * {@link #verifyScoping(String, HttpServletRequest) verifies scoping}.
	 */
	class FirstDeploymentServlet extends TestServlet {

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws ServletException, IOException {
			final var dispatcherType = request.getDispatcherType();
			switch (dispatcherType) {
				case REQUEST: {  // init and dispatch to the 2nd deployment
					request.setAttribute(THREAD_ATTR, Thread.currentThread());
					request.setAttribute(Service.CONTAINER_CALL, requestScopedProvider.get());
					request.setAttribute(Service.HTTP_SESSION, sessionScopedProvider.get());
					final var dispatcher = request.getServletContext()
						.getContext(MultiAppServer.SECOND_APP_PATH)
						.getRequestDispatcher("/" + SecondDeploymentServlet.class.getSimpleName());
					if (
						FORWARD.toString().equals(
								request.getParameter(DispatcherType.class.getSimpleName()))
					) {
						dispatcher.forward(request, response);
					} else {
						dispatcher.include(request, response);
					}
					return;
				}
				case FORWARD:
				case INCLUDE: {  // verify scoping and finish
					if (Thread.currentThread() == request.getAttribute(THREAD_ATTR)) {
						throw new ServletException(
							"cycled dispatch running on the same Thread as the initial request: "
									+ "cannot perform the test: try again?");
					}
					verifyScoping(
						"async container Thread in the 1st deployment handling a cycled dispatch",
						request
					);
					sendScopedObjectHashes(request, response);
					return;
				}
				default: throw new AssertionError("unexpected DispatcherType: " + dispatcherType);
			}
		}

		public static final String THREAD_ATTR = "pl.morgwai.base.servet.guice.test.Thread";
	}



	/**
	 * Receives a cross-deployment {@link javax.servlet.RequestDispatcher dispatch} from
	 * {@link FirstDeploymentServlet}, performs internal
	 * {@link javax.servlet.AsyncContext#dispatch(String) async dispatch} to change the handling
	 * {@link Thread} and {@link javax.servlet.RequestDispatcher dispatches} the request back to
	 * {@link FirstDeploymentServlet} in {@link Server#TEST_APP_PATH the first deployment}.
	 */
	class SecondDeploymentServlet extends HttpServlet {

		@Override
		protected void doGet(HttpServletRequest request, HttpServletResponse response)
				throws ServletException, IOException {
			final var dispatcherType = request.getDispatcherType();
			switch (dispatcherType) {
				case FORWARD:
				case INCLUDE: {  // ASYNC dispatch to self to force Thread change
					request.setAttribute(DispatcherType.class.getSimpleName(), dispatcherType);
					final var asyncCtx = request.startAsync();
					asyncCtx.setTimeout(1800L * 1000L);
					new Thread(
						() -> asyncCtx.dispatch("/" + SecondDeploymentServlet.class.getSimpleName())
					).start();
					return;
				}
				case ASYNC: {  // dispatch back to the 1st deployment
					final var dispatcher = request.getServletContext()
						.getContext(Server.TEST_APP_PATH)
						.getRequestDispatcher("/" + FirstDeploymentServlet.class.getSimpleName());
					if (request.getAttribute(DispatcherType.class.getSimpleName()) == FORWARD) {
						dispatcher.forward(request, response);
					} else {
						dispatcher.include(request, response);
					}
					return;
				}
				default: throw new AssertionError("unexpected DispatcherType: " + dispatcherType);
			}
		}
	}
}
