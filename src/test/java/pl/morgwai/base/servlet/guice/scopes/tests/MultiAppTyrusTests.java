package pl.morgwai.base.servlet.guice.scopes.tests;

import jakarta.websocket.Session;

import org.junit.After;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.MultiAppServer;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server;
import pl.morgwai.base.servlet.guice.scopes.tests.tyrus.TwoNodeTyrusFarm;
import pl.morgwai.base.servlet.guice.scopes.tests.tyrus.TyrusServer;
import pl.morgwai.base.servlet.guice.utils.StandaloneWebsocketContainerServletContext;



public class MultiAppTyrusTests extends MultiAppWebsocketTests {



	StandaloneWebsocketContainerServletContext appDeployment1;
	StandaloneWebsocketContainerServletContext appDeployment2;



	@Override
	protected MultiAppServer createServer() throws Exception {
		appDeployment1 = TyrusServer.createDeployment(Server.APP_PATH);
		appDeployment2 = TyrusServer.createDeployment(MultiAppServer.SECOND_APP_PATH);
		return new TwoNodeTyrusFarm(-1, -1, Server.APP_PATH, MultiAppServer.SECOND_APP_PATH);
	}



	@After
	public void cleanupDeployments() {
		TyrusServer.cleanupDeployment(appDeployment1);
		TyrusServer.cleanupDeployment(appDeployment2);
	}



	@Override
	protected boolean isHttpSessionAvailable() {
		return false;
	}



	/** TyrusServer reports error only after send attempt and not even right away... */
	@Override
	protected Session testOpenConnectionToServerEndpoint(String type) throws Exception {
		final var connection = super.testOpenConnectionToServerEndpoint(type);
		Thread.sleep(100L);
		connection.getBasicRemote().sendText("yo");
		return connection;
	}



	/** TyrusServer does not support it. */
	@Override
	public void testAnnotatedExtendingEndpoint() {}



	/** TyrusServer does not support it. */
	@Override
	public void testAnnotatedExtendingEndpointOnSecondApp() {}
}
