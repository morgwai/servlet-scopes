package pl.morgwai.base.servlet.guice.scopes;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.HashMap;

import javax.servlet.http.HttpSession;
import javax.websocket.Session;

import org.junit.Test;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;

import static pl.morgwai.base.guice.scopes.ContextSerializationTestUtils.testContextSerialization;
import static pl.morgwai.base.servlet.guice.scopes.HttpSessionContext.CUSTOM_SERIALIZATION_PARAM;



public class ContextSerializationTests {



	@Test
	public void testWebsocketConnectionContextSerialization() throws IOException {
		final var connectionProperties = new HashMap<String, Object>();
		final Session connectionMock = (Session) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[] {Session.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getUserProperties")) return connectionProperties;
				throw new UnsupportedOperationException();
			}
		);
		final var connectionProxy = new WebsocketConnectionProxy(connectionMock, null, true);
		final var ctx = new WebsocketConnectionContext(connectionProxy);
		testContextSerialization(ctx);
	}



	public void testHttpSessionContextSerialization(boolean customSerialization) throws IOException
	{
		final var servletContextMock = new StandaloneWebsocketContainerServletContext("");
		servletContextMock.setInitParameter(
			CUSTOM_SERIALIZATION_PARAM,
			String.valueOf(customSerialization)
		);
		final HttpSession sessionMock = (HttpSession) Proxy.newProxyInstance(
			getClass().getClassLoader(),
			new Class<?>[] {HttpSession.class},
			(proxy, method, args) -> {
				if (method.getName().equals("getServletContext")) return servletContextMock;
				throw new UnsupportedOperationException();
			}
		);
		final var ctx = new HttpSessionContext(sessionMock);
		testContextSerialization(ctx);
	}

	@Test
	public void testHttpSessionContextSerializationWithCustomSerialization() throws IOException {
		testHttpSessionContextSerialization(true);
	}

	@Test
	public void testHttpSessionContextSerializationWithStandardSerialization() throws IOException {
		testHttpSessionContextSerialization(false);
	}
}
