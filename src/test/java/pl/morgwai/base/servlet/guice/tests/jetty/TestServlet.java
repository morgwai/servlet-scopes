// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests.jetty;

import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import pl.morgwai.base.servlet.guice.scopes.ServletWebsocketModule;
import pl.morgwai.base.servlet.guice.tests.servercommon.Service;



/** Base class for most other {@code Servlets} testing different ways of dispatching. */
public abstract class TestServlet extends HttpServlet {



	public static final String REPLYING_SERVLET = "replyingServlet";

	@Inject @Named(Service.CONTAINER_CALL)
	Provider<Service> requestScopedProvider;

	@Inject @Named(Service.HTTP_SESSION)
	Provider<Service> sessionScopedProvider;



	/**
	 * Verifies if objects obtained from {@link #requestScopedProvider} and
	 * {@link #sessionScopedProvider} are the same as the ones stored in
	 * {@link Service#CONTAINER_CALL request} {@link Service#HTTP_SESSION attributes} by the
	 * {@code Servlet} that initially received {@code request}.
	 * @throws ServletException if the verification fails.
	 */
	void verifyScoping(String threadDesignation, HttpServletRequest request)
			throws ServletException {
		if (
			request.getAttribute(Service.CONTAINER_CALL) != requestScopedProvider.get()
			|| request.getAttribute(Service.HTTP_SESSION) != sessionScopedProvider.get()
		) {
			throw new ServletException(getClass().getSimpleName() + ": scoping failure on "
					+ threadDesignation + " (" + Thread.currentThread().getName() + ')');
		}
	}

	public static final String INITIAL_THREAD_DESIGNATION = "the initial container Thread";



	/**
	 * Sends {@link Properties} containing scoped {@code Object} {@link Object#hashCode() hashes}.
	 * The following properties {@link Properties#setProperty(String, String) are set}:
	 * <ul>
	 *   <li>{@code prefix} + {@link #REPLYING_SERVLET} -> {@link Class#getSimpleName() simple name
	 *       of the Class} of the {@code Servlet} that actually produced the reply</li>
	 *   <li>{@code prefix} + {@link Service#CONTAINER_CALL} -> hash of the
	 *       {@link ServletWebsocketModule#containerCallScope
	 *       containerCallScope}d instance of {@link Service}</li>
	 *   <li>{@code prefix} + {@link Service#HTTP_SESSION} -> hash of the
	 *       {@link ServletWebsocketModule#httpSessionScope
	 *       httpSessionScope}d instance of {@link Service}</li>
	 * </ul>
	 */
	void sendScopedObjectHashes(
		HttpServletRequest request,
		HttpServletResponse response,
		String prefix
	) throws IOException {
		final var responseStream = response.getOutputStream();
		final var responseContent = new Properties(3);
		responseContent.setProperty(
			prefix + REPLYING_SERVLET,
			getClass().getSimpleName()
		);
		responseContent.setProperty(
			prefix + Service.CONTAINER_CALL,
			String.valueOf(request.getAttribute(Service.CONTAINER_CALL).hashCode())
		);
		responseContent.setProperty(
			prefix + Service.HTTP_SESSION,
			String.valueOf(request.getAttribute(Service.HTTP_SESSION).hashCode())
		);
		responseContent.store(responseStream, null);
	}

	/**
	 * Calls {@link #sendScopedObjectHashes(HttpServletRequest, HttpServletResponse, String)
	 * sendScopedObjectHashes(request, response, "")}.
	 */
	void sendScopedObjectHashes(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		sendScopedObjectHashes(request, response, "");
	}



	/**
	 * Calls {@link #verifyScoping(String, HttpServletRequest)} and
	 * {@link #sendScopedObjectHashes(HttpServletRequest, HttpServletResponse)}.
	 */
	void verifyScopingAndSendReply(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		verifyScoping("reply-sending container Thread", request);
		sendScopedObjectHashes(request, response);
	}



	/** Logs any {@code Exception} thrown during the processing. */
	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			super.service(request, response);
		} catch (Throwable e) {
			log.log(Level.SEVERE, "Exception", e);
			throw e;
		}
	}

	static final Logger log = Logger.getLogger(TestServlet.class.getName());
}
