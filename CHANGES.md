# Summaries of visible changes between releases

### 17.2
- Update [servlet-utils](https://github.com/morgwai/servlet-utils) dependency to 6.3.

### 17.1
- Update [servlet-utils](https://github.com/morgwai/servlet-utils) dependency to 6.2.
- Update [guice-context-scopes](https://github.com/morgwai/guice-context-scopes) dependency to 12.0.

### 17.0
- Better client-side support: split `Configurator`s into a common and a server parts and introduce client-side annotations `GuiceClientEndpoint` and `PingingClientEndpoint`.
- Update [guice-context-scopes](https://github.com/morgwai/guice-context-scopes) dependency to 11.1.
- Reorganize `Module`s to better support client apps and make them extend newly introduced [ScopeModule](https://javadoc.io/static/pl.morgwai.base/guice-context-scopes/11.1/pl/morgwai/base/guice/scopes/ScopeModule.html) where appropriate.
- Remove `ServletContextTrackingExecutor` as [ContextTrackingExecutor](https://javadoc.io/static/pl.morgwai.base/guice-context-scopes/11.1/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.html) decorator was introduced.
- Support for `Context` transferring on `FORWARD`, `INCLUDE` and `ERROR` dispatching, including cross-deployment dispatching.
- Support for websocket `Context` nesting (for example when a client connection is made from a server `Endpoint`).
- Update [servlet-utils](https://github.com/morgwai/servlet-utils) dependency to 6.0.
- Remove compile dependency on `java-utils`.
- Refactor `WebsocketConnectionProxy` constructors.
