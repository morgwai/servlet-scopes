# Servlet and Websocket Guice Scopes

Servlet and websocket Guice scopes, that are automatically transferred when dispatching work to other threads.<br/>
<br/>
**latest release: 6.1**<br/>
[javax flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/6.1-javax/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/6.1-javax))<br/>
[experimental jakarta flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/6.1-jakarta-experimental/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/6.1-jakarta-experimental))
([see notes](#notes-on-jakarta-support))


## OVERVIEW

Provides the below Guice scopes built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) which automatically transfers them to a new thread when dispatching using `ContextTrackingExecutor` (see below).

### containerCallScope
Scopes bindings to either an `HttpServletRequest` or a websocket event (connection opened/closed, message received, error occurred).<br/>
Spans over a single container-initiated call to either one of servlet's `doXXX(...)` methods or to a websocket endpoint life-cycle method (annotated with one of the websocket annotations or overriding those of `javax.websocket.Endpoint` or of registered `javax.websocket.MessageHandler`s).<br/>
Having a common scope for servlet requests and websocket events allows the same binding to be available both in servlets and endpoints.

### websocketConnectionScope
Scopes bindings to a websocket connection (`javax.websocket.Session`).<br/>
Spans over a lifetime of a given endpoint instance: all calls to life-cycle methods of a given endpoint instance (annotated with `@OnOpen`, `@OnMessage`, `@OnError`, `@OnClose`, or overriding those of `javax.websocket.Endpoint` together with methods of registered `MessageHandler`s) are executed within the same associated `websocketConnectionScope`.

### httpSessionScope
Scopes bindings to a given `HttpSession`. Available both to servlets and websocket endpoints.


## MAIN USER CLASSES

### [ServletModule](src/main/java/pl/morgwai/base/servlet/scopes/ServletModule.java)
Contains the above `Scope`s, `ContextTracker`s and some helper methods.

### [ContextTrackingExecutor](src/main/java/pl/morgwai/base/servlet/scopes/ContextTrackingExecutor.java)
An `Executor` (backed by a fixed size `ThreadPoolExecutor` by default) that upon dispatching automatically updates which thread runs within which `Context` (`ServletRequest`/`WebsocketEvent`, `WebsocketConnection`, `HttpSession`).<br/>
Instances should usually be created using helper methods from the above `ServletModule` and configured for named instance injection in user modules.

### [GuiceServerEndpointConfigurator](src/main/java/pl/morgwai/base/servlet/scopes/GuiceServerEndpointConfigurator.java)
A websocket endpoint `Configurator` that automatically injects dependencies of newly created endpoint instances and decorates their methods to automatically create context for websocket connections and events.

### [GuiceServletContextListener](src/main/java/pl/morgwai/base/servlet/scopes/GuiceServletContextListener.java)
Base class for app's `ServletContextListener`. Creates and configures apps Guice `Injector` and the above `ServletModule`. Provides also some helper methods.

### [PingingServletContextListener](src/main/java/pl/morgwai/base/servlet/guiced/utils/PingingServletContextListener.java)
Subclass of `GuiceServletContextListener` that additionally automatically registers/deregisters created endpoint instances to a [WebsocketPingerService](https://github.com/morgwai/servlet-utils#main-user-classes).


## USAGE

```java
@WebListener
public class ServletContextListener extends GuiceServletContextListener {  // ...or PingingServletContextListener

	@Override
	protected LinkedList<Module> configureInjections() {
		LinkedList<Module> modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(MyService.class).in(servletModule.containerCallScope);
				// @Inject Provider<MyService> myServiceProvider;
				// will now work both in servlets and endpoints
			// more bindings here...
		});
		return modules;
	}

	@Override
	protected void configureServletsFiltersEndpoints() throws ServletException {
		addServlet("myServlet", MyServlet.class, "/myServlet");  // will have its fields injected
		// more servlets / filters / unannotated endpoints here...
	}
}
```

```java
@ServerEndpoint(
	value = "/websocket/mySocket",
	configurator = GuiceServerEndpointConfigurator.class)  // ...or PingingEndpointConfigurator
public class MyEndpoint {

	@Inject Service service;

	// endpoint implementation here...
}
// MyEndpoint will have its fields injected. Methods onOpen, onClose, onError and registered
// MessageHandlers will run within containerCallScope, websocketConnectionScope and httpSessionScope
```

In cases when it's not possible to avoid thread switching without the use of `ContextTrackingExecutor` (for example when passing callbacks to some async calls), static helper methods `getActiveContexts(List<ContextTracker<?>>)` and `executeWithinAll(List<TrackableContext>, Runnable)` defined in `ContextTrackingExecutor` can be used to transfer context manually:

```java
class MyClass {

    @Inject List<ContextTracker<?>> allTrackers;

    void myMethod(Object param) {
        // myMethod code
        var activeCtxList = ContextTrackingExecutor.getActiveContexts(allTrackers);
        someAsyncMethod(param, (callbackParam) ->
            ContextTrackingExecutor.executeWithinAll(activeCtxList, () -> {
                // callback code
            })
        );
    }
}
```

When dispatching work to servlet container threads using any of `AsyncContext.dispatch()` methods, the context is transferred automatically.

Dependencies of this jar on `slf4j-api` and `guice` are declared with scope `provided`, so that apps can use any versions of these libs with compatible API.


## EXAMPLES
[a trivial sample app](sample)<br/>
[a more complex sample app](https://github.com/morgwai/guiced-servlet-jpa/tree/master/sample) from derived [guiced-servlet-jpa lib](https://github.com/morgwai/guiced-servlet-jpa).


## FAQ

**Why isn't this built on top of [official servlet scopes lib](https://github.com/google/guice/wiki/Servlets)?**
* it would not be possible to reuse `ContextTrackingExecutor` and it would need to be rewritten.
* in order to extend the official Guice-servlet lib to support websockets, the code would need to pretend that everything is an `HttpServletRequest` (websocket events and websocket connections would need to be wrapped in some fake `HttpSevletRequest` wrappers), which seems awkward.
* `guice-context-scopes` is thread-safe: a single request can be handled by multiple threads (as long as accessed scoped objects are thread-safe or properly synchronized).
* `guice-context-scopes` allows to remove objects from scopes.

**Why do I have to myself create a filter that automatically creates HTTP session for websockets? Can't this lib do it for me?**

Always enforcing a session creation is not acceptable in many cases, so this would limit applicability of this lib. Reasons may be technical (cookies disabled, non-browser clients that don't even follow redirections), legal (user explicitly refusing any data storage) and probably others. It's a sad trade-off between applicability and API safety.


## NOTES ON JAKARTA SUPPORT

Jakarta flavor is currently based on [repackaged version of Guice](https://github.com/GedMarc/GuicedEE-Services) from [GuicedEE project](https://guicedee.com/), that has some [unresolved](https://github.com/GedMarc/GuicedEE-Services/issues/16) [issues](https://github.com/GedMarc/GuicedEE-Services/issues/17). It seems to work ok for simple cases, but should not be considered production ready at this time.<br/>
`servlet-scopes` should however work properly with any other jakarta-repackaged build of Guice provided at runtime if and only if the difference from the upstream version is exact renaming of all `javax` references to `jakarta`.
