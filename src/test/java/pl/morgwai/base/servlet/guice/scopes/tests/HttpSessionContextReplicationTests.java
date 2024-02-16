// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes.tests;

import java.io.File;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.logging.ConsoleHandler;

import org.eclipse.jetty.server.session.*;
import org.eclipse.jetty.server.session.JDBCSessionDataStore.SessionTableSchema;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import pl.morgwai.base.servlet.guice.scopes.tests.jetty.JettyNode;

import static java.util.logging.Level.*;

import static org.junit.Assert.assertEquals;
import static pl.morgwai.base.jul.JulConfigurator.*;
import static pl.morgwai.base.servlet.guice.scopes.tests.jetty.JettyNode.*;



public class HttpSessionContextReplicationTests {



	static final String NODE1_ID = "node1";
	static final String NODE2_ID = "node2";
	JettyNode node1;
	JettyNode node2;

	static final String URL_PREFIX = "http://localhost:";
	CookieManager cookieManager;
	HttpClient httpClient;

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();



	@Before
	public void setup() {
		cookieManager = new CookieManager();
		httpClient = HttpClient.newBuilder().cookieHandler(cookieManager).build();
	}



	@After
	public void shutdown() throws Exception {
		if (node1 != null) {
			if ( !node1.isStopped()) node1.stop();
			node1.join();
			node1.destroy();
		}
		if (node2 != null) {
			if ( !node2.isStopped()) node2.stop();
			node2.join();
			node2.destroy();
		}
	}



	void testHttpSessionContextReplication(
		SessionDataStore store1,
		SessionDataStore store2,
		boolean startNode2rightAway,
		boolean customSerialization
	) throws Exception {
		node1 = new JettyNode(0, NODE1_ID, store1, customSerialization);
		if (startNode2rightAway) {
			// FileSessionDataStore's files may be accessed by only 1 node process at a time, so
			// the start of node2 needs to be delayed in such case, otherwise start it right away
			node2 = new JettyNode(0, NODE2_ID, store2, customSerialization);
		}
		final var url1 = URL_PREFIX + node1.getPort() + APP_PATH + NODE_INFO_SERVLET_PATH;
		final var request1 = HttpRequest.newBuilder(URI.create(url1))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var responseNode1 = new Properties();
		responseNode1.load(httpClient.send(request1, BodyHandlers.ofInputStream()).body());
		node1.stop();

		if (node2 == null) node2 = new JettyNode(0, NODE2_ID, store2, customSerialization);
		final var url2 = URL_PREFIX + node2.getPort() + APP_PATH + NODE_INFO_SERVLET_PATH;
		final var request2 = HttpRequest.newBuilder(URI.create(url2))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var responseNode2 = new Properties();
		responseNode2.load(httpClient.send(request2, BodyHandlers.ofInputStream()).body());
		assertEquals(
			"session should be replicated between nodes",
			responseNode1.getProperty(SESSION_ID_PROPERTY),
			responseNode2.getProperty(SESSION_ID_PROPERTY)
		);
		assertEquals("serializable session attribute should be replicated",
				NODE1_ID, responseNode2.getProperty(SESSION_NODE_ID_PROPERTY));
		assertEquals("serializable session-scoped object should be replicated",
				NODE1_ID, responseNode2.getProperty(CONTEXT_NODE_ID_PROPERTY));
		assertEquals("non-serializable session-scoped object should NOT be replicated",
				NODE2_ID, responseNode2.getProperty(NON_SERIALIZABLE_CONTEXT_NODE_ID_PROPERTY));
	}



	@Test
	public void testHttpSessionContextReplicationWithFileStoreAndStandardSerialization()
			throws Exception {
		testHttpSessionContextReplicationWithFileStore(false);
	}

	@Test
	public void testHttpSessionContextReplicationWithFileStoreAndCustomSerialization()
			throws Exception {
		testHttpSessionContextReplicationWithFileStore(true);
	}

	void testHttpSessionContextReplicationWithFileStore(boolean customSerialization)
			throws Exception {
		final var sessionFolder = temporaryFolder.getRoot();
		testHttpSessionContextReplication(
			createFileSessionStore(sessionFolder),
			createFileSessionStore(sessionFolder),
			false,
			customSerialization
		);
	}

	SessionDataStore createFileSessionStore(File sessionFolder) {
		final var sessionStore = new FileSessionDataStore();
		sessionStore.setStoreDir(sessionFolder);
		sessionStore.setGracePeriodSec(10);
		sessionStore.setSavePeriodSec(0);
		return sessionStore;
	}



	@Test
	public void testHttpSessionContextReplicationWithJdbcStoreAndStandardSerialization()
			throws Exception {
		testHttpSessionContextReplicationWithJdbcStore(false);
	}

	@Test
	public void testHttpSessionContextReplicationWithJdbcStoreAndCustomSerialization()
			throws Exception {
		testHttpSessionContextReplicationWithJdbcStore(true);
	}

	void testHttpSessionContextReplicationWithJdbcStore(boolean customSerialization)
			throws Exception {
		final var adaptor = new DatabaseAdaptor();
		adaptor.setDriverInfo(
			org.h2.Driver.class.getName(),
			"jdbc:h2:mem:servlet-scopes-test-sessions;DB_CLOSE_DELAY=-1"
		);
		testHttpSessionContextReplication(
			createJdbcSessionStore(adaptor),
			createJdbcSessionStore(adaptor),
			true,
			customSerialization
		);
	}

	SessionDataStore createJdbcSessionStore(DatabaseAdaptor adaptor) {
		final var schema = new SessionTableSchema();
		final var sessionStore = new JDBCSessionDataStore();
		sessionStore.setDatabaseAdaptor(adaptor);
		sessionStore.setSessionTableSchema(schema);
		sessionStore.setGracePeriodSec(10);
		sessionStore.setSavePeriodSec(0);
		return sessionStore;
	}



	@BeforeClass
	public static void setupLogging() {
		addOrReplaceLoggingConfigProperties(Map.of(
			"pl.morgwai.level", SEVERE.toString(),
			LEVEL_SUFFIX, WARNING.toString(),
			ConsoleHandler.class.getName() + LEVEL_SUFFIX, FINEST.toString()
		));
		overrideLogLevelsWithSystemProperties("pl.morgwai");
	}
}
