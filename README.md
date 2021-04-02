# Guice Servlet Scopes

Servlet Request+Session and Websocket Message+Session Guice Scopes



## MAIN USER CLASSES

### [ServletModule](src/main/java/pl/morgwai/base/servlet/scopes/ServletModule.java)

Guice Servlet `Scope`s, `ContextTracker`s and some helper methods.


### [GuiceServletContextListener](src/main/java/pl/morgwai/base/servlet/scopes/GuiceServletContextListener.java)

Base class for app's `ServletContextListener`. Creates and configures apps Guice `Injector` and the above `ServletModule`. Provides also some helper methods.


### [ContextTrackingExecutor](https://github.com/morgwai/guice-context-scopes/blob/master/src/main/java/pl/morgwai/base/guice/scopes/ContextTrackingExecutor.java)

A `ThreadPoolExecutor` that upon dispatching automatically updates which thread handles which `Context` (Request, Message, Session). Instances should usually be obtained using helper methods from the above `ServletModule`.<br/>
(this class actually comes from [guice-context-scopes lib](https://github.com/morgwai/guice-context-scopes) on top of which this one is built)



## USAGE

```java
@WebListener
public class ServletContextListener extends GuiceServletContextListener {

	@Override
	protected LinkedList<Module> configureInjections() {
		LinkedList<Module> modules = new LinkedList<Module>();
		modules.add((binder) -> {
			binder.bind(MyService.class).in(servletModule.requestScope);
			// more bindings here...
		});
		return modules;
	}

	@Override
	protected void configureServletsAndFilters() throws ServletException {
		addServlet("myServlet", MyServlet.class, "/myServlet");
		// more servlets here...
	}
}
```



## EXAMPLES

See [sample app](sample)
