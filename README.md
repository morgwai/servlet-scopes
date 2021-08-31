# Servlet and Websocket Guice Scopes

Servlet and websocket Guice scopes, that are automatically transferred when dispatching work to other threads.<br/>
<br/>
**latest release: [1.0-alpha6](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/1.0-alpha6/jar)**


## OVERVIEW

Provides the below Guice scopes built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java).

### requestScope

Scopes bindings to either an `HttpServletRequest` or a websocket event (connection opened/closed, message received, error occured).<br/>
Spans over a single invocations of a method (`Servlet.doXXX` or one of websocket endpoint's methods associated with a given event).<br/>
Having a common scope for servlet requests and websocket events allows instances from a single request scoped binding to be obtained both in servlets and endpoints without a need for 2 separate bindings with different `@Named` annotation value.


### websocketConnectionScope

Scopes bindings to a websocket connection (`javax.websocket.Session`).<br/>
Spans over a lifetime of a given endpoint instance. Specifically, all calls to given endpoint's annotated methods (from `@OnOpen`, across all calls to `@OnMessage` and `@OnError` until and including `@OnClose`) or methods overriding those of `javax.websocket.Endpoint` together with methods of registered `MessageHandler`s are executed within a single `websocketConnectionScope`.


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

* [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) was first developed for [gRPC scopes](https://github.com/morgwai/grpc-scopes). After that, it was easier to reuse it, rather than pretend that everything is an `HttpServletRequest` (ie: in order to extend the official Guice-servlet lib to support websockets, websocket events and websocket connections would need to be wrapped in some fake `HttpSevletRequest` wrappers) and re-developing `ContextTrackingExecutor`.

* this implementation is thread-safe: a single request can be handled by multiple threads (as long as accessed scoped objects are thread-safe or properly synchronized)
