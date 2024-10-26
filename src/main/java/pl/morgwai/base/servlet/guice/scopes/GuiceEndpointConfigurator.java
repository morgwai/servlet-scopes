// Copyright 2021 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.scopes;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

import com.google.inject.*;
import com.google.inject.name.Named;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import pl.morgwai.base.guice.scopes.ContextTracker;

import static com.google.inject.name.Names.named;



/**
 * Obtains {@code Endpoint} instances from {@link Injector#getInstance(Class) Guice} and ensures
 * their methods
 * {@link WebsocketEventContext#executeWithinSelf(Runnable) run within websocket Contexts}.
 * This ensures that all dependencies are injected and {@link Scope}s from {@link WebsocketModule}
 * and {@link ServletWebsocketModule} ({@link WebsocketModule#containerCallScope},
 * {@link WebsocketModule#websocketConnectionScope} and
 * {@link ServletWebsocketModule#httpSessionScope}) work properly.
 * <p>
 * To use this class for client {@code Endpoints}, first a {@link WebsocketModule} must be passed to
 * {@link Guice#createInjector(com.google.inject.Module...)}. After that context-aware client
 * {@code Endpoints} may be obtained in 1 of the below ways:</p>
 * <ul>
 *   <li>by obtaining {@link GuiceEndpointConfigurator} from the created {@link Injector} and
 *       calling either {@link #getProxiedEndpointInstance(Class)} or
 *       {@link #getProxyForEndpoint(Object)}</li>
 *   <li>if some {@code endpointClass} was passed to {@link WebsocketModule}'s
 *       {@link WebsocketModule#WebsocketModule(boolean, java.util.Set)}  constructor}, then it will
 *       be injected to fields and params annotated with @{@link GuiceClientEndpoint}</li>
 * </ul>
 * <p>
 * To use this class for server {@code Endpoints}, see {@link GuiceServerEndpointConfigurator}.</p>
 * <p>
 * <b>NOTE:</b> annotated {@code Endpoints} that need to be created using this {@code Configurator}
 * <b>must</b> have a method annotated with @{@link OnOpen} that <b>must</b> have a {@link Session}
 * param.</p>
 * <p>
 * <b>NOTE:</b> due to the way many debuggers work, it is <b>strongly</b> recommended for
 * {@link Object#toString() toString()} methods of {@code Endpoints} to work properly even when
 * called outside of any {@code Context}.</p>
 * @see pl.morgwai.base.servlet.guice.utils.PingingEndpointConfigurator
 */
public class GuiceEndpointConfigurator {



	protected final Injector injector;
	protected final ContextTracker<ContainerCallContext> ctxTracker;

	/** Controls verification style of {@link #checkIfRequiredEndpointMethodsPresent(Class)}. */
	protected final boolean requireTopLevelMethodAnnotations;
	/**
	 * {@link Key#getAnnotation() Binding} {@link Named name} for
	 * {@link #requireTopLevelMethodAnnotations}.
	 */
	public static final String REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_NAME =
			".requireTopLevelMethodAnnotations";
	/** Binding {@code Key} for {@link #requireTopLevelMethodAnnotations}. */
	public static final Key<Boolean> REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_KEY =
			Key.get(Boolean.class, named(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_NAME));
	/**
	 * Name of the
	 * {@link javax.servlet.ServletContext#getInitParameter(String) deployment init-param} that may
	 * contain a value for {@link #requireTopLevelMethodAnnotations}.
	 * @see GuiceServletContextListener#contextInitialized(javax.servlet.ServletContextEvent)
	 */
	public static final String REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_INIT_PARAM =
			GuiceEndpointConfigurator.class.getName() + REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_NAME;



	@Inject
	public GuiceEndpointConfigurator(
		Injector injector,
		ContextTracker<ContainerCallContext> ctxTracker,
		@Named(REQUIRE_TOP_LEVEL_METHOD_ANNOTATIONS_NAME) boolean requireTopLevelMethodAnnotations
	) {
		this.injector = injector;
		this.ctxTracker = ctxTracker;
		this.requireTopLevelMethodAnnotations = requireTopLevelMethodAnnotations;
	}



	/**
	 * Calls {@link #getProxyForEndpoint(Object) getProxyForEndpoint}<code>(
	 * {@link #injector}.{@link Injector#getInstance(Key) getInstance}(endpointClass))</code>.
	 */
	public <EndpointT> EndpointT getProxiedEndpointInstance(Class<EndpointT> endpointClass)
			throws InvocationTargetException {
		return getProxyForEndpoint(injector.getInstance(endpointClass));
	}



	/**
	 * Creates a {@link #getProxyClass(Class) dynamic context-aware proxy} for {@code endpoint}.
	 * @return an instance of the dynamic proxy subclass of {@code endpoint}'s class that wraps
	 *     {@code endpoint}.
	 */
	public <EndpointT> EndpointT getProxyForEndpoint(EndpointT endpoint)
			throws InvocationTargetException {
		@SuppressWarnings("unchecked")
		final var endpointClass = (Class<EndpointT>) endpoint.getClass();
		final var proxyClass = getProxyClass(endpointClass);
		try {
			final EndpointT endpointProxy = createEndpointProxyInstance(proxyClass);
			final var endpointProxyHandler = new EndpointProxyHandler(
				getAdditionalDecorator(endpoint),
				ctxTracker
			);
			proxyClass.getDeclaredField(INVOCATION_HANDLER_FIELD_NAME)
					.set(endpointProxy, endpointProxyHandler);
			return endpointProxy;
		} catch (NoSuchFieldException | IllegalAccessException | InstantiationException e) {
			throw new IllegalArgumentException(e);
		}
	}



	static final String INVOCATION_HANDLER_FIELD_NAME =
			GuiceEndpointConfigurator.class.getPackageName().replace('.', '_')
					+ "_invocationHandler";



	/**
	 * Creates an instance of {@code proxyClass}.
	 * @return by default {@code proxyClass.getConstructor().newInstance()}, in instances wrapped
	 *     by {@link GuiceServerEndpointConfigurator}s it must be
	 *     {@link GuiceServerEndpointConfigurator#newGuiceEndpointConfigurator(Injector) overridden}
	 *     to return an instance {@link
	 *     javax.websocket.server.ServerEndpointConfig.Configurator#getEndpointInstance(Class)
	 *     obtained} from container's default
	 *     {@link javax.websocket.server.ServerEndpointConfig.Configurator}.
	 */
	protected  <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
			throws InstantiationException, InvocationTargetException {
		try {
			return proxyClass.getConstructor().newInstance();
		} catch (IllegalAccessException | NoSuchMethodException e) {
			throw new IllegalArgumentException(e);
		}
	}



	/**
	 * Returns a dynamically created class of a context-aware proxy for {@code endpointClass}.
	 * The proxy ensures that {@code endpoint} lifecycle methods are executed within
	 * {@link WebsocketEventContext}, {@link WebsocketConnectionContext} and if an
	 * {@link javax.servlet.http.HttpSession} is present, then also {@link HttpSessionContext}.
	 * <p>
	 * This method is usually called by {@link #getProxyForEndpoint(Object)}. Nevertheless, once
	 * built, a proxy class is cached for subsequent requests for the same {@code endpointClass},
	 * thus this method may be also called directly during an app's initialization to pre-build the
	 * dynamic proxy classes.</p>
	 */
	public <EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
		@SuppressWarnings("unchecked")
		final Class<? extends EndpointT> proxyClass = (Class<? extends EndpointT>)
				proxyClasses.computeIfAbsent(endpointClass, this::createProxyClass);
		return proxyClass;
	}

	static final ConcurrentMap<Class<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();



	/** Creates a new dynamic class of a context-aware proxy for {@code endpointClass}. */
	<EndpointT> Class<? extends EndpointT> createProxyClass(Class<EndpointT> endpointClass) {
		if ( !Endpoint.class.isAssignableFrom(endpointClass)) {
			checkIfRequiredEndpointMethodsPresent(endpointClass);
		}
		DynamicType.Builder<EndpointT> proxyClassBuilder = new ByteBuddy()
			.subclass(endpointClass)
			.name(
				GuiceEndpointConfigurator.class.getPackageName() + ".ProxyFor_"
						+ endpointClass.getName().replace('.', '_').replace('$', '_') + '_'
						+ (endpointClass.hashCode() & Integer.MAX_VALUE)  // strictlyPositive(hash)
			)
			.defineField(
				INVOCATION_HANDLER_FIELD_NAME,
				EndpointProxyHandler.class,
				Visibility.PACKAGE_PRIVATE
			)
			.method(ElementMatchers.any())
				.intercept(InvocationHandlerAdapter.toField(INVOCATION_HANDLER_FIELD_NAME));
		final ServerEndpoint serverAnnotation = endpointClass.getAnnotation(ServerEndpoint.class);
		if (serverAnnotation != null) {
			proxyClassBuilder = proxyClassBuilder.annotateType(serverAnnotation);
		}
		final ClientEndpoint clientAnnotation = endpointClass.getAnnotation(ClientEndpoint.class);
		if (clientAnnotation != null) {
			proxyClassBuilder = proxyClassBuilder.annotateType(clientAnnotation);
		}
		try (
			final var unloadedClass = proxyClassBuilder.make();
		) {
			return unloadedClass
				.load(
					GuiceEndpointConfigurator.class.getClassLoader(),
					ClassLoadingStrategy.Default.INJECTION
				)
				.getLoaded();
		}
	}



	/**
	 * Checks if annotated {@code endpointClass} has all the
	 * {@link #getRequiredEndpointMethodAnnotationTypes() required} {@code Endpoint} life-cycle
	 * methods.
	 * Additionally checks if @{@link OnOpen} annotated method has a {@link Session} param.
	 * <p>
	 * Note the discrepancy between container implementations: Tyrus requires lifecycle methods
	 * overridden in subclasses to be re-annotated with @{@link OnOpen} / @{@link OnClose} etc,
	 * while Jetty forbids it and will throw an {@code Exception} in such case.<br/>
	 * By default this method performs a relaxed checking: it verifies only that a given annotation
	 * is present and allows both duplicates and non-top-level annotating. However, if
	 * {@link #requireTopLevelMethodAnnotations} is set, then Tyrus-style checking will be
	 * performed, meaning lifecycle methods will be required to be annotated at the top level.</p>
	 * @throws IllegalArgumentException if the check fails.
	 */
	protected void checkIfRequiredEndpointMethodsPresent(Class<?> endpointClass) {
		final var wantedMethodAnnotationTypes = getRequiredEndpointMethodAnnotationTypes();
		var classUnderScan = endpointClass;
		var methodsToScan = endpointClass.getMethods();
		do {
			for (var method: methodsToScan) {
				if ( !Modifier.isPublic(method.getModifiers())) continue;
				final var wantedAnnotationTypeIterator = wantedMethodAnnotationTypes.iterator();
				while (wantedAnnotationTypeIterator.hasNext()) {
					final var wantedAnnotationType = wantedAnnotationTypeIterator.next();
					if (method.isAnnotationPresent(wantedAnnotationType)) {
						wantedAnnotationTypeIterator.remove();
						if (
							wantedAnnotationType.equals(OnOpen.class)
							&& !Arrays.asList(method.getParameterTypes()).contains(Session.class)
						) {
							throw new IllegalArgumentException(NO_ON_OPEN_SESSION_PARAM_MESSAGE);
						}
					}
				}
				if (wantedMethodAnnotationTypes.isEmpty()) break;
			}
			classUnderScan = classUnderScan.getSuperclass();
			methodsToScan = classUnderScan.getDeclaredMethods();
		} while ( !requireTopLevelMethodAnnotations && !classUnderScan.equals(Object.class));
		if ( !wantedMethodAnnotationTypes.isEmpty()) {
			throw new IllegalArgumentException(MISSING_LIFECYCLE_METHOD_MESSAGE
					+ wantedMethodAnnotationTypes.iterator().next().getSimpleName());
		}
	}

	static final String NO_ON_OPEN_SESSION_PARAM_MESSAGE =
			"method annotated with @OnOpen must have a " + Session.class.getName() + " param";
	static final String MISSING_LIFECYCLE_METHOD_MESSAGE =
			"endpoint class must have a method annotated with @";



	/**
	 * Returns a set of annotations of {@code Endpoint} lifecycle methods required to be present by
	 * this configurator.
	 * By default a singleton of {@link OnOpen}. Subclasses may override this method if needed.
	 * Overriding methods should call {@code super} and add their required annotations to the
	 * obtained {@code Set} before returning it.
	 */
	protected HashSet<Class<? extends Annotation>> getRequiredEndpointMethodAnnotationTypes() {
		final var requiredAnnotationTypes = new HashSet<Class<? extends Annotation>>(5);
		requiredAnnotationTypes.add(OnOpen.class);
		return requiredAnnotationTypes;
	}



	/**
	 * Subclasses may override this method to further customize {@code Endpoints}.
	 * {@link InvocationHandler#invoke(Object, Method, Object[])} method of the returned handler
	 * will be executed within websocket {@code  Contexts}. By default this method returns a handler
	 * that simply invokes the given method on {@code endpoint}.
	 */
	protected InvocationHandler getAdditionalDecorator(Object endpoint) {
		return (proxy, method, args) -> method.invoke(endpoint, args);
	}
}
