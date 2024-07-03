// Copyright 2023 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.tests;

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
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import org.eclipse.jetty.server.session.*;
import org.eclipse.jetty.server.session.JDBCSessionDataStore.SessionTableSchema;
import pl.morgwai.base.servlet.guice.tests.jetty.JettyNode;

import static java.util.logging.Level.*;
import static org.junit.Assert.assertEquals;
import static pl.morgwai.base.jul.JulConfigurator.*;
import static pl.morgwai.base.servlet.guice.tests.jetty.JettyNode.*;



public class HttpSessionContextReplicationTests {



	static final String FIRST_NODE_ID = "first";
	static final String SECOND_NODE_ID = "second";
	JettyNode firstNode, secondNode;

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
		if (
			firstNode != null
			&& ( !firstNode.isStopping())
			&& ( !firstNode.isStopped())
		) {
			try {
				firstNode.stop();
			} catch (Exception ignored) {}  // don't prevent secondNode shutdown
		}
		if (
			secondNode != null
			&& ( !secondNode.isStopping())
			&& ( !secondNode.isStopped())
		) {
			secondNode.stop();
		}
	}



	void testHttpSessionContextReplication(
		SessionDataStore firstNodeStore,
		SessionDataStore secondNodeStore,
		boolean startSecondNodeRightAway,
		boolean customSerialization
	) throws Exception {
		firstNode = new JettyNode(0, FIRST_NODE_ID, firstNodeStore, customSerialization);
		if (startSecondNodeRightAway) {
			// FileSessionDataStore's files may be accessed by only 1 node process at a time, so
			// the start of secondNode needs to be delayed in such case, otherwise
			// (JDBCSessionDataStore case) start it right away
			secondNode = new JettyNode(0, SECOND_NODE_ID, secondNodeStore, customSerialization);
		}
		final var firstNodeResponse = sendRequestAndParseResponse(firstNode);
		firstNode.stop();

		if (secondNode == null) {
			secondNode = new JettyNode(0, SECOND_NODE_ID, secondNodeStore, customSerialization);
		}
		final var secondNodeResponse = sendRequestAndParseResponse(secondNode);

		// sanity checks
		assertEquals(
			"session should be replicated between nodes",
			firstNodeResponse.getProperty(SESSION_ID_PROPERTY),
			secondNodeResponse.getProperty(SESSION_ID_PROPERTY)
		);
		assertEquals(
			"serializable session attribute should be replicated",
			FIRST_NODE_ID,
			secondNodeResponse.getProperty(SESSION_STORED_NODE_ID_PROPERTY)
		);

		// verify Context data replication
		assertEquals(
			"serializable session-scoped object should be replicated",
			FIRST_NODE_ID,
			secondNodeResponse.getProperty(CONTEXT_STORED_NODE_ID_PROPERTY)
		);
		assertEquals(
			"non-serializable session-scoped object should NOT be replicated",
			SECOND_NODE_ID,
			secondNodeResponse.getProperty(CONTEXT_STORED_NON_SERIALIZABLE_NODE_ID_PROPERTY)
		);
	}

	Properties sendRequestAndParseResponse(JettyNode node) throws Exception {
		final var url = URL_PREFIX + node.getPort() + APP_PATH + NODE_INFO_SERVLET_PATH;
		final var request = HttpRequest.newBuilder(URI.create(url))
			.GET()
			.timeout(Duration.ofSeconds(2))
			.build();
		final var rawResponse = httpClient.send(request, BodyHandlers.ofInputStream());
		final var response = new Properties();
		response.load(rawResponse.body());
		return response;
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
