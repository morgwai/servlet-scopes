# Servlet and Websocket Guice Scopes

Servlet and websocket Guice scopes, that are automatically transferred when dispatching work to other threads.<br/>
<br/>
**latest release: [1.0](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/1.0/jar)**
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/1.0))


## OVERVIEW

Provides the below Guice scopes built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java).

### requestScope
Scopes bindings to either an `HttpServletRequest` or a websocket event (connection opened/closed, message received, error occured).<br/>
Spans over a single container initiated invocation of servlet or websocket endpoint method (`Servlet.doXXX` methods, endpoint methods annotated with `@OnOpen`, `@OnMessage`, `@OnError`, `@OnClose`, methods overriding those of `javax.websocket.Endpoint` and methods of registered `MessageHandler`s).<br/>
Having a common scope for servlet requests and websocket events allows the same binding to be available both in servlets and endpoints.

### websocketConnectionScope
Scopes bindings to a websocket connection (`javax.websocket.Session`).<br/>
Spans over a lifetime of a given endpoint instance: all calls to life-cycle methods of a given endpoint instance (annotated with `@OnOpen`, `@OnMessage`, `@OnError`, `@OnClose`, or overriding those of `javax.websocket.Endpoint` together with methods of registered `MessageHandler`s) are executed within the same associated `websocketConnectionScope`.

### httpSessionScope
Scopes bindings to a given `HttpSession`. Available both to servlets and websocket endpoints.


## MAIN USER CLASSES

### [ServletModule](src/main/java/pl/morgwai/base/servlet/scopes/ServletModule.java)
Contains the above `Scope`s, `ContextTracker`s and some helper methods.

### [GuiceServletContextListener](src/main/java/pl/morgwai/base/servlet/scopes/GuiceServletContextListener.java)
Base class for app's `ServletContextListener`. Creates and configures apps Guice `Injector` and the above `ServletModule`. Provides also some helper methods.

### [GuiceServerEndpointConfigurator](src/main/java/pl/morgwai/base/servlet/scopes/GuiceServerEndpointConfigurator.java)
A websocket endpoint `Configurator` that automatically injects dependencies of newly created endpoint instances and decorates their methods to automatically create context for websocket connections and events.

### [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java)
A `ThreadPoolExecutor` that upon dispatching automatically updates which thread runs within which `Context` (Request, Message, Session). Instances should usually be obtained using helper methods from the above `ServletModule`.<br/>
(this class actually comes from [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes)).


## USAGE

```java
@WebListener
public class ServletContextListener extends GuiceServletContextListener {

	@Override
	protected LinkedList<Module> configureInjections() {
		LinkedList<Module> modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(MyService.class).in(servletModule.requestScope);
				// @Inject Provider<MyService> myServiceProvider;
				// will now work both in servlets and endpoints
			// more bindings here...
		});
		return modules;
	}

	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		addServlet("myServlet", MyServlet.class, "/myServlet");  // will have its fields injected
		// more servlets/filters here...
	}
}
```

```java
@ServerEndpoint(
	value = "/websocket/mySocket",
	configurator = GuiceServerEndpointConfigurator.class)
public class MyEndpoint {
	// endpoint implementation here...
}
// MyEndpoint will have its fields injected. Methods onOpen, onClose, onError and registered
// MessageHandlers will run within requestScope, websocketConnectionScope and httpSessionScope
```


## EXAMPLES
[a trivial sample app](sample)<br/>
[a more complex sample app](https://github.com/morgwai/guiced-servlet-jpa/tree/master/sample) from derived [guiced-servlet-jpa lib](https://github.com/morgwai/guiced-servlet-jpa).


## FAQ

**Why isn't this built on top of [official servlet scopes lib](https://github.com/google/guice/wiki/Servlets)?**
* it would not be possible to reuse `ContextTrackingExecutor` and it would need to be rewritten.
* in order to extend the official Guice-servlet lib to support websockets, the code would need to pretend that everything is an `HttpServletRequest` (websocket events and websocket connections would need to be wrapped in some fake `HttpSevletRequest` wrappers), which seems awkward.
* `guice-context-scopes` is thread-safe: a single request can be handled by multiple threads (as long as accessed scoped objects are thread-safe or properly synchronized).
* `guice-context-scopes` allows to remove objects from scopes.
