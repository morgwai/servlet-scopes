# Servlet and Websocket Guice Scopes

`containerCallScope` (either a `HttpServletRequest` or a websocket `Endpoint` event), `websocketConnectionScope` (`javax.websocket.Session`) and `httpSessionScope` for use in `Servlet`/websocket server containers and websocket client containers.<br/>
Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0<br/>
<br/>
**latest release: 16.3**<br/>
[javax flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/16.3-javax/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/16.3-javax)) - supports Servlet `4.0.1` and Websocket `1.1` APIs<br/>
[jakarta flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/16.3-jakarta/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/16.3-jakarta)) - supports Servlet `5.0.0` to at least `6.0.0` and Websocket `2.0.0` to at least `2.1.1` APIs


## OVERVIEW

Provides the below Guice scopes:

### [containerCallScope](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletModule.html#containerCallScope)
Scopes bindings to either an `HttpServletRequest` or a websocket event (connection opened/closed, message received, error occurred).<br/>
Spans over a single container-initiated call to either one of servlet's `doXXX(...)` methods or to a websocket endpoint life-cycle method (annotated with one of the websocket annotations or overriding those of `javax.websocket.Endpoint` or of registered `javax.websocket.MessageHandler`s).<br/>
Having a common `Scope` for servlet requests and websocket events allows to inject scoped objects both in servlets and endpoints without a need for 2 separate bindings in user `Module`s.
This `Scope` may be used both on a client and on a server side.

### [websocketConnectionScope](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletModule.html#websocketConnectionScope)
Scopes bindings to a websocket connection (`javax.websocket.Session`).<br/>
Spans over a lifetime of a given endpoint instance: all calls to life-cycle methods of a given endpoint instance (annotated with `@OnOpen`, `@OnMessage`, `@OnError`, `@OnClose`, or overriding those of `javax.websocket.Endpoint` together with methods of registered `MessageHandler`s) are executed within the same associated `websocketConnectionScope`.
This `Scope` may be used both on a client and on a server side.

### [httpSessionScope](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletModule.html#httpSessionScope)
Scopes bindings to a given `HttpSession`. Available only on a server side both to servlets and websocket endpoints.

All the above scopes are built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes), so they are automatically transferred to a new thread when dispatching using `AsyncContext.dispatch()` or `ServletContextTrackingExecutor` (see below).



## MAIN USER CLASSES

### [ServletModule](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletModule.html)
Contains the above `Scope`s, related `ContextTracker`s and some helper methods.

### [GuiceServerEndpointConfigurator](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/GuiceServerEndpointConfigurator.html)
Websocket `ServerEndpoint` `Configurator` that ensures that `Endpoint` instances have their dependencies injected and that their methods run within websocket contexts, so that the above `Scope`s work properly.

### [GuiceServletContextListener](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/GuiceServletContextListener.html)
Base class for app `ServletContextListener`s. Creates and configures an app-wide Guice `Injector` instance, the above mentioned `ServletModule` and performs bookkeeping related to `GuiceServerEndpointConfigurator` and `ServletContextTrackingExecutor`s. Also provides helper methods for creating and configuring programmatic `Servlet`s, `Filter`s and `Endpoint`s.

### [PingingEndpointConfigurator](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingEndpointConfigurator.html)
Subclass of `GuiceServerEndpointConfigurator` that additionally automatically registers and deregisters created `Endpoint` instances to its associated [WebsocketPingerService](https://javadoc.io/doc/pl.morgwai.base/servlet-utils/latest/pl/morgwai/base/servlet/utils/WebsocketPingerService.html).

### [PingingServletContextListener](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingServletContextListener.html)
Subclass of `GuiceServletContextListener` that uses `PingingEndpointConfigurator` for programmatic `Endpoint`s and configures app's `WebsocketPingerService`.

### [ServletContextTrackingExecutor](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletContextTrackingExecutor.html)
A `ThreadPoolExecutor` that upon dispatching a task, automatically transfers all the active `Context`s to the thread running the task.

### [ContextBinder](https://javadoc.io/doc/pl.morgwai.base/guice-context-scopes/latest/pl/morgwai/base/guice/scopes/ContextBinder.html)
Binds tasks and callbacks (`Runnable`s, `Consumer`s, `BiConsumer`s, `Function`s and `BiFunction`s) to contexts that were active at the time of binding. This can be used to transfer `Context`s **almost** fully automatically when it's not possible to use `GrpcContextTrackingExecutor` when switching threads (for example when providing callbacks as arguments to async functions). See a usage sample below.

### [ClientEndpointProxy](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ClientEndpointProxy.html)
Context-aware proxy for client `Endpoints`. Executes lifecycle methods of its wrapped `Endpoint` and of its registered `MessageHandlers` within websocket `Contexts`.

### [PingingClientEndpointProxy](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingClientEndpointProxy.html)
Subclass of `ClientEndpointProxy` that additionally automatically registers and deregisters its wrapped `Endpoint` to its associated [WebsocketPingerService](https://javadoc.io/doc/pl.morgwai.base/servlet-utils/latest/pl/morgwai/base/servlet/utils/WebsocketPingerService.html).


## USAGE

### Adding Guice `Modules` and programmatic `Servlet`s and `Endpoint`s in `ServletContextListener`
```java
@WebListener
public class ServletContextListener extends GuiceServletContextListener {
                          // ...or `extends PingingServletContextListener {`

    @Override
    protected LinkedList<Module> configureInjections() throws Exception {
        final var modules = new LinkedList<Module>();
        modules.add((binder) -> {
            binder.bind(MyService.class).in(containerCallScope);
                // @Inject Provider<MyService> myServiceProvider;
                // will now work both in servlets and endpoints
            // more bindings here...
        });
        return modules;
    }

    @Override
    protected void configureServletsFiltersEndpoints() throws ServletException, DeploymentException
    {
        addEnsureSessionFilter("/websocket/*");

        // MyServlet and MyProgrammaticEndpoint instances will have their dependencies injected
        addServlet("myServlet", MyServlet.class, "/myServlet");
        addEndpoint(MyProgrammaticEndpoint.class, "/websocket/myProgrammaticSocket");
        // more servlets / filters / unannotated endpoints here...
    }
}
```
**NOTE:** If the servlet container being used uses mechanism other than the standard Java Serialization to persist/replicate `HttpSession`s, then a deployment [init-param](https://javadoc.io/static/jakarta.servlet/jakarta.servlet-api/5.0.0/jakarta/servlet/ServletContext.html#setInitParameter-java.lang.String-java.lang.String-) named `pl.morgwai.base.servlet.guice.scopes.HttpSessionContext.customSerialization` must be set to `true` either in `web.xml` or programmatically before any request is served (for example in `ServletContextListener.contextInitialized(event)`).

Note: in cases where it is not possible to extend `GuiceServletContextListener`, all the setup required to use `ServletModule` (with all its `Scopes` etc) and `GuiceServerEndpointConfigurator` / `PingingEndpointConfigurator`, can be done manually: see an example in [ManualServletContextListener](src/test/java/pl/morgwai/base/servlet/guice/scopes/tests/jetty/ManualServletContextListener.java).

### Using annotated server `Endpoints`
```java
@ServerEndpoint(
    value = "/websocket/myAnnotatedSocket",
    configurator = GuiceServerEndpointConfigurator.class  // ...or PingingEndpointConfigurator
)
public class MyAnnotatedEndpoint {

    @Inject Provider<MyService> myServiceProvider;  // will be injected automatically

    // endpoint implementation here...
}
```
Note: in case of annotated `Endpoints`, it is still necessary either for app's `ServletContextListener` to extend `GuiceServletContextListener` / `PingingServletContextListener` or to perform the setup manually as explained before.

### Client websocket app sample
```java
public class MyWebsocketClientApp {

    public static void main(String[] args) throws Exception {
        final var modules = new ArrayList<Module>();
        final var servletModule = new ServletModule();
        modules.add(servletModule);
        modules.add((binder) -> {
            binder.bind(MyClientEndpointDependency.class).in(servletModule.containerCallScope);
            // more bindings here...
        });
        // more modules here...
        final var injector = Guice.createInjector(modules);
        final WebSocketContainer clientWebsocketContainer = createClientWebsocketContainer();
        final var myClientEndpoint = injector.getInstance(MyClientEndpoint.class);
        clientWebsocketContainer.connectToServer(
            new ClientEndpointProxy(myClientEndpoint, servletModule.containerCallContextTracker),
            null,
            URI.create("wss://someapp.example.com/websocket/someservice")
        );
        myClientEndpoint.awaitClosure(10, SECONDS);
    }

    static WebSocketContainer createClientWebsocketContainer() {/* ... */}
}
```

### Transferring contexts to callbacks using `ContextBinder`
```java
class MyComponent {

    @Inject ContextBinder ctxBinder;

    void methodThatCallsSomeAsyncMethod(/* ... */) {
        // other code here...
        someAsyncMethod(arg1, /* ... */ argN, ctxBinder.bindToContext((callbackParam) -> {
            // callback code here...
        }));
    }
}
```
**NOTE:** when dispatching work to servlet container threads using any of `AsyncContext.dispatch()` methods, the context is transferred **automatically**.

### Dependency management
Dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice) is declared as optional, so that apps can use any version with compatible API.

There are 2 builds available:
- build with `shadedbytebuddy` classifier includes relocated dependency on [byte-buddy](https://search.maven.org/artifact/net.bytebuddy/byte-buddy). Most apps should use this build. To do so, add `<classifier>shadedbytebuddy</classifier>` to your dependency declaration.
- "default" build does not include any shaded dependencies and dependency on `byte-buddy` is marked as `optional`. This is useful for apps that also depend on `byte-buddy` and need to save space (`byte-buddy` is over 3MB in size). Note that the version provided by the app needs to be compatible with the version that `servlet-scopes` depends on (in regard to features used by `servlet-scopes`). If this is not the case, then `shadedbytebuddy` build should be used.


## EXTENSIONS

[Tyrus connection proxy](https://github.com/morgwai/servlet-scopes-connection-proxy-tyrus) that provides unified, websocket API compliant access to clustered websocket connections and properties.


## EXAMPLES

[a trivial sample app built from the test code](sample).


## FAQ

**Why isn't this built on top of [official servlet scopes lib](https://github.com/google/guice/wiki/Servlets)?**
* the official Guice-servlet has some [serious issues](https://github.com/google/guice/blob/6.0.0/extensions/servlet/src/com/google/inject/servlet/ServletScopes.java#L158)
* in order to extend the official Guice-servlet lib to support websockets, the code would need to pretend that everything is an `HttpServletRequest` (websocket events and websocket connections would need to be wrapped in some fake `HttpSevletRequest` wrappers), which seems awkward.
* `guice-context-scopes` allows to remove objects from scopes.

**Why do I have to install myself a filter that creates HTTP session for websocket requests? Can't `addEnsureSessionFilter("/*")` be called automatically?**

Always enforcing a session creation is not acceptable in many cases, so this would limit applicability of this lib. Reasons may be technical (cookies disabled, non-browser clients that don't even follow redirections), legal (user explicitly refusing any data storage) and probably others. It's a sad trade-off between applicability and API safety.
