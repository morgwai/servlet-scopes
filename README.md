# Servlet and Websocket Guice Scopes

`containerCallScope` (either a `HttpServletRequest` or a websocket `Endpoint` event), `websocketConnectionScope` (`javax.websocket.Session`) and `httpSessionScope` for use in servlet+websocket apps and standalone websocket apps (both client and server).<br/>
Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0<br/>
<br/>
**latest release: 17.1**<br/>
[javax flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/17.1-javax/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/17.1-javax)) - supports Servlet `4.0.1` and Websocket `1.1` APIs<br/>
[jakarta flavor](https://search.maven.org/artifact/pl.morgwai.base/servlet-scopes/17.1-jakarta/jar)
([javadoc](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/17.1-jakarta)) - supports Servlet `5.0.0` to at least `6.0.0` and Websocket `2.0.0` to at least `2.1.1` APIs<br/>
<br/>
See [CHANGES](CHANGES.md) for the summary of changes between releases. If the major version of a subsequent release remains unchanged, it is supposed to be backwards compatible in terms of API and behaviour with previous ones with the same major version (meaning that it should be safe to just blindly update in dependent projects and things should not break under normal circumstances).



## OVERVIEW

Provides the below Guice `Scope`s:

### [containerCallScope](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/WebsocketModule.html#containerCallScope)
Scopes bindings to either an `HttpServletRequest` or a websocket event (connection opened/closed, message received, error occurred).<br/>
Spans over a single container-initiated call to either one of `Servlet`'s `doXXX(...)` methods or to a websocket `Endpoint` life-cycle method (annotated with one of the websocket annotations or overriding those of `javax.websocket.Endpoint` or of registered `javax.websocket.MessageHandler`s).<br/>
Having a common `Scope` for servlet requests and websocket events allows to inject scoped objects both in `Servlet`s and `Endpoint`s without a need for 2 separate bindings in user `Module`s.
This `Scope` may be used in all 3 container types.

### [websocketConnectionScope](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/WebsocketModule.html#websocketConnectionScope)
Scopes bindings to a websocket connection (`javax.websocket.Session`).<br/>
Spans over a lifetime of a given endpoint instance: all calls to life-cycle methods of a given `Endpoint` instance (annotated with `@OnOpen`, `@OnMessage`, `@OnError`, `@OnClose`, or overriding those of `javax.websocket.Endpoint` together with methods of registered `MessageHandler`s) are executed within the same associated `websocketConnectionScope`.
This `Scope` may be used in websocket containers both on a client and on a server side.

### [httpSessionScope](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletWebsocketModule.html#httpSessionScope)
Scopes bindings to a given `HttpSession`. Available only in `Servlet` containers to `Servlet`s and
optionally server `Endpoint`s.

All the above scopes are built using [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes), so they are automatically transferred when dispatching using `AsyncContext`, `RequestDispatcher` or `ContextTrackingExecutor`.



## MAIN USER CLASSES

<br/>

- **BASE WEBSOCKET STUFF:**

### [GuiceEndpointConfigurator](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/GuiceEndpointConfigurator.html)
Obtains `Endpoint` instances from Guice and ensures their methods run within websocket `Context`s by wrapping them with context-aware proxies. May be used directly to obtain client `Endpoint` instances.

### [GuiceClientEndpoint](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/GuiceClientEndpoint.html)
Annotation for client `Endpoint`s that should be injected using a `GuiceEndpointConfigurator`.

### [WebsocketModule](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/WebsocketModule.html)
Defines `containerCallScope` and `websocketConnectionScope`, configures `GuiceEndpointConfigurator`. Necessary in all 3 container types.

### [GuiceServerEndpointConfigurator](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/GuiceServerEndpointConfigurator.html)
`ServerEndpointConfig.Configurator` (for use in `@ServerEndpoint` annotations as `configurator` argument) that obtains server `Endpoint` instances from a `GuiceEndpointConfigurator`.

### [StandaloneWebsocketServerModule](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/StandaloneWebsocketServerModule.html)
`Module` for standalone websocket server apps. Initializes `GuiceServerEndpointConfigurator`.

<br/>

- **MIXED SERVLET-WEBSOCKET APPS:**

### [ServletWebsocketModule](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/ServletWebsocketModule.html)
`Module` for mixed Servlet-websocket apps. Embeds a `WebsocketModule` and defines `httpSessionScope`.

### [GuiceServletContextListener](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/scopes/GuiceServletContextListener.html)
Base class for app `ServletContextListener`s, creates and configures the app-wide `Injector` and `ServletWebsocketModule`, initializes `GuiceServerEndpointConfigurator`. Provides helper methods for programmatically adding `Servlet`s, `Filter`s and websocket `Endpoint`s.

<br/>

- **MISC STUFF:**

### [ContextBinder](https://javadoc.io/doc/pl.morgwai.base/guice-context-scopes/latest/pl/morgwai/base/guice/scopes/ContextBinder.html)
Binds closures (`Runnable`s, `Consumer`s, `Callable`s etc) to `Context`s that were active at the time of a given binding. This can be used to transfer `Context`s semi-automatically when manually switching `Thread`s, for example when passing callbacks to async functions.

### [ContextTrackingExecutor](https://javadoc.io/doc/pl.morgwai.base/guice-context-scopes/latest/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.html)
Interface and decorator for an `Executor` that automatically transfers active `Contexts` using its associated `ContextBinder` when executing tasks.

<br/>

- **INTEGRATION WITH [WebsocketPingerService](https://javadoc.io/doc/pl.morgwai.base/servlet-utils/latest/pl/morgwai/base/servlet/utils/WebsocketPingerService.html):**

### [PingingEndpointConfigurator](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingServerEndpointConfigurator.html)
`GuiceEndpointConfigurator` that additionally automatically registers and deregisters created `Endpoint`s to its associated `WebsocketPingerService`.

### [PingingClientEndpoint](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingClientEndpoint.html)
Annotation for client `Endpoint`s that should be injected using a `PingingEndpointConfigurator`.

### [PingingWebsocketModule](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingWebsocketModule.html)
Subclass of `WebsocketModule` that allows to automatically register `Endpoint` instances to a `WebsocketPingerService` using `PingingEndpointConfigurator`.

### [PingingServerEndpointConfigurator](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingServerEndpointConfigurator.html)
`GuiceServerEndpointConfigurator` that uses `PingingEndpointConfigurator`.

### [PingingServletContextListener](https://javadoc.io/doc/pl.morgwai.base/servlet-scopes/latest/pl/morgwai/base/servlet/guice/utils/PingingServletContextListener.html)
`GuiceServletContextListener` that uses `PingingWebsocketModule` and `PingingEndpointConfigurator`. Creates and configures  the app-wide `WebsocketPingerService`.



## USAGE

### Adding Guice `Modules` and programmatic `Servlet`s and `Endpoint`s in a `ServletContextListener`
```java
@WebListener
public class MyServletContextListener extends GuiceServletContextListener {
                          // ...or `extends PingingServletContextListener {`

    @Override
    protected LinkedList<Module> configureInjections() {
        final var modules = new LinkedList<Module>();
        modules.add((binder) -> {
            binder.bind(SomeService.class).to(MyService.class).in(containerCallScope);
                // @Inject Provider<SomeService> myServiceProvider;
                // will now work both in servlets and endpoints
            // more bindings here...
        });
        return modules;
    }

    @Override
    protected void addServletsFiltersEndpoints() throws ServletException, DeploymentException {
        addEnsureSessionFilter("/websocket/*");

        // MyServlet and MyProgrammaticEndpoint instances will have their dependencies injected
        addServlet("myServlet", MyServlet.class, "/myServlet");
        addEndpoint(MyProgrammaticEndpoint.class, "/websocket/myProgrammaticSocket");
        // more servlets / filters / endpoints here...
    }
}
```
**NOTE:** If the servlet container being used uses mechanism other than the standard Java Serialization to persist/replicate `HttpSession`s, then a deployment [init-param](https://javadoc.io/static/jakarta.servlet/jakarta.servlet-api/5.0.0/jakarta/servlet/ServletContext.html#setInitParameter-java.lang.String-java.lang.String-) named `pl.morgwai.base.servlet.guice.scopes.HttpSessionContext.customSerialization` must be set to `true` either in `web.xml` or programmatically before any request is served (for example in `ServletContextListener.contextInitialized(event)`).

### Using annotated server `Endpoints`
```java
@ServerEndpoint(
    value = "/websocket/myAnnotatedSocket",
    configurator = GuiceServerEndpointConfigurator.class  // ...or PingingServerEndpointConfigurator
)
public class MyAnnotatedEndpoint {

    @Inject Provider<SomeService> myServiceProvider;  // will be injected automatically

    // endpoint implementation here...
}
```
Note: in case of annotated `Endpoints`, it is also necessary either for app's `ServletContextListener` to extend `GuiceServletContextListener` / `PingingServletContextListener` or to perform the setup manually as explained before.

### Websocket client app sample
```java
public class MyWebsocketClientApp {

    static final String SERVER_URL = "url";
    static final String REQUEST = "request";

    @Inject @Named(SERVER_URL) String serverUrl;
    @Inject @Named(REQUEST) String request;
    @Inject @GuiceClientEndpoint MyClientEndpoint endpoint;
    @Inject WebSocketContainer container;

    @ClientEndpoint
    public static class MyClientEndpoint {

        @Inject ResponseProcessor responseProcessor;
        Session connection;
        final CountDownLatch connectionClosed = new CountDownLatch(1);
        
        @OnOpen public void onOpen(Session connection) {
            this.connection = connection;
        }

        @OnMessage public void onMessage(String serverReply) {
            try {
                responseProcessor.process(serverReply);
            } finally {
                try {
                    connection.close();
                } catch (IOException ignored) {}
            }
        }
        
        @OnClose public void onClose() {
            connectionClosed.countDown();
        }

        void awaitClosure(long timeout, TimeUnit unit) throws InterruptedException {
            connectionClosed.await(timeout, unit);
        }
    }

    void startAndAwait(long timeout, TimeUnit unit) throws Exception {
        try( 
            final var connection = container.connectToServer(endpoint, URI.create(serverUrl));
        ) {
            connection.getBasicRemote().sendText(request);
            endpoint.awaitClosure(timeout, unit);
        }
    }

    public static void main(String[] args) throws Exception {
        final var modules = new ArrayList<Module>();
        final var websocketModule = new WebsocketModule(false, MyClientEndpoint.class);
        modules.add(websocketModule);
        modules.add((binder) -> {
            binder.bind(WebSocketContainer.class)
                .toInstance(createClientWebsocketContainer());
            binder.bind(String.class)
                .annotatedWith(named(SERVER_URL))
                .toInstance(args[0]);
            binder.bind(String.class)
                .annotatedWith(named(REQUEST))
                .toInstance(args[1]);
            binder.bind(ResponseProcessor.class);  // has default or @inject constructor
            binder.bind(SomeService.class)
                .to(MyService.class)
                .in(websocketModule.containerCallScope);
            // more bindings here...
        });
        // more modules here...
        final var injector = Guice.createInjector(modules);
        final var myApp = injector.getInstance(MyWebsocketClientApp.class);
        myApp.startAndAwait(10, SECONDS);
    }

    static WebSocketContainer createClientWebsocketContainer() {
        // container specific code
    }
}
```

### Websocket standalone server container sample
```java
public class MyWebsocketServer {

    public static void main(String[] args) throws Exception {
        final var port = Integer.parseInt(args[0]);
        final var deploymentPath = args[1];
        final var modules = new ArrayList<Module>();
        final var websocketModule = new WebsocketModule(false);
        final var serverModule = new StandaloneWebsocketServerModule(deploymentPath);
        modules.add(websocketModule);
        modules.add(serverModule);
        modules.add((binder) -> {
            binder.bind(SomeService.class)
                .to(MyService.class)
                .in(websocketModule.containerCallScope);
            // more bindings here...
        });
        // more modules here...
        final var injector = Guice.createInjector(modules);
        final var server = createServer(port, deploymentPath, Config.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            GuiceServerEndpointConfigurator.deregisterInjector(injector);
        }));
        server.awaitTermination();
    }

    public static class Config implements ServerApplicationConfig {

        @Override
        public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
            return Set.of(MyAnnotatedEndpoint.class);
        }

        @Override
        public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> s) {
            return Set.of();
        }
    }

    Server createServer(int port, String deploymentPath, Class<?>... configs) {
        // container specific code
    }
}
```

### Dependency management
Dependencies of this jar on [guice](https://search.maven.org/artifact/com.google.inject/guice) is declared as optional, so that apps can use any version with compatible API.

Standalone websocket apps must include `servlet-api` in their dependencies ([javax](https://central.sonatype.com/artifact/javax.servlet/javax.servlet-api) or [jakarta](https://central.sonatype.com/artifact/jakarta.servlet/jakarta.servlet-api) respectively).

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
