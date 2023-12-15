package pl.morgwai.base.servlet.guice.scopes.tests.tyrus;

import javax.websocket.Session;

import pl.morgwai.base.servlet.guice.scopes.tests.MultiAppWebsocketTests;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.MultiAppServer;
import pl.morgwai.base.servlet.guice.scopes.tests.servercommon.Server;



public class MultiAppTyrusTests extends MultiAppWebsocketTests {



	@Override
	protected MultiAppServer createServer() throws Exception {
		return new TwoNodeTyrusFarm(-1, -1, Server.APP_PATH, MultiAppServer.SECOND_APP_PATH);
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
