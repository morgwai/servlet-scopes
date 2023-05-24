# Servlet and Websocket Guice Scopes

`containerCallScope` (either a `HttpServletRequest` or a websocket endpoint event), `websocketConnectionScope` (`javax.websocket.Session`) and `httpSessionScope`.<br/>
<br/>
**latest release: 9.4**<br/>
[javax flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/9.4-javax/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/9.4-javax))<br/>
[jakarta flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/9.4-jakarta/jar) (experimental: see [notes](#notes-on-jakarta-support))
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/9.4-jakarta))



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

### [PingingEndpointConfigurator](src/main/java/pl/morgwai/base/servlet/guiced/utils/PingingEndpointConfigurator.java)
Subclass of `GuiceServerEndpointConfigurator` that additionally automatically registers/deregisters created endpoint instances to a [WebsocketPingerService](https://github.com/morgwai/servlet-utils#main-user-classes).

### [PingingServletContextListener](src/main/java/pl/morgwai/base/servlet/guiced/utils/PingingServletContextListener.java)
Subclass of `GuiceServletContextListener` that uses `PingingEndpointConfigurator`.


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

    @Inject Provider<MyService> myServiceProvider;

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
        someAsyncMethod(
            param,
            (callbackParam) -> ContextTrackingExecutor.executeWithinAll(activeCtxList, () -> {
                // callback code
            })
        );
    }
}
```

When dispatching work to servlet container threads using any of `AsyncContext.dispatch()` methods, the context is transferred automatically.

### Dependency management
Dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice) and [slf4j-api](https://search.maven.org/artifact/org.slf4j/slf4j-api) are declared as optional, so that apps can use any versions of these deps with compatible API.

There are 2 builds available:
- build with `shadedbytebuddy` classifier includes relocated dependency on [byte-buddy](https://search.maven.org/artifact/net.bytebuddy/byte-buddy). Most apps should use this build. To do so, add `<classifier>shadedbytebuddy</classifier>` to your dependency declaration.
- "default" build does not include any shaded dependencies and dependency on `byte-buddy` is marked as `optional`. This is useful for apps that also depend on `byte-buddy` and need to save space (`byte-buddy` is over 3MB in size). Note that the version provided by the app needs to be compatible with the version that `servlet-scopes` depends on (in regard to features used by `servlet-scopes`). If this is not the case, then `shadedbytebuddy` build should be used.


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

Jakarta flavor is currently based on [repackaged version of Guice](https://github.com/GedMarc/GuicedEE-Services) from [GuicedEE project](https://guicedee.com/), that has some [unresolved](https://github.com/GedMarc/GuicedEE-Services/issues/16) [issues](https://github.com/GedMarc/GuicedEE-Services/issues/17). It seems to work ok for simple cases, but more testing is needed.<br/>
`servlet-scopes` should however work properly with any other jakarta-repackaged build of Guice with compatible API provided at assemble or runtime. "Compatible API" means the only difference from the upstream API is an exact renaming of all `javax` references to `jakarta`.
