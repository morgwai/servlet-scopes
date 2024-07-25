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
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import pl.morgwai.base.guice.scopes.ContextTracker;



/**
 * Obtains {@code Endpoint} instances from {@link Injector#getInstance(Class) Guice} and ensures
 * their methods
 * {@link WebsocketEventContext#executeWithinSelf(Runnable) run within websocket Contexts}.
 * This ensures that all dependencies are injected and {@link Scope}s from {@link ServletModule}
 * ({@link ServletModule#containerCallScope}, {@link ServletModule#websocketConnectionScope} and
 * {@link ServletModule#httpSessionScope}) work properly.
 * <p>
 * To use this class for client {@code Endpoints}, todo: javadoc </p>
 * <p>
 * To use this class for server {@code Endpoints}, see {@link GuiceServerEndpointConfigurator}.</p>
 */
public class GuiceEndpointConfigurator {



	protected final Injector injector;
	protected final ContextTracker<ContainerCallContext> ctxTracker;



	// todo: javadoc
	@Inject
	public GuiceEndpointConfigurator(
		Injector injector,
		ContextTracker<ContainerCallContext> ctxTracker
	) {
		this.injector = injector;
		this.ctxTracker = ctxTracker;
	}



	/**
	 * Obtains an instance of {@code endpointClass} from {@link Injector#getInstance(Class) Guice}
	 * and creates a dynamic context-aware proxy for it.
	 * The proxy ensures that {@code Endpoint} lifecycle methods are executed within
	 * {@link WebsocketEventContext}, {@link WebsocketConnectionContext} and if an
	 * {@link javax.servlet.http.HttpSession} is present, then also {@link HttpSessionContext}.
	 * @return a proxy for an {@code endpointClass} instance obtained from {@link Injector Guice}.
	 */
	public <EndpointT> EndpointT getProxiedEndpointInstance(Class<EndpointT> endpointClass)
			throws InvocationTargetException {
		return getProxyForEndpoint(injector.getInstance(endpointClass));
	}

	// todo: javadoc
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



	// todo: javadoc
	protected  <ProxyT> ProxyT createEndpointProxyInstance(Class<ProxyT> proxyClass)
			throws InstantiationException, InvocationTargetException {
		try {
			return proxyClass.getConstructor().newInstance();
		} catch (IllegalAccessException | NoSuchMethodException e) {
			throw new IllegalArgumentException(e);
		}
	}



	/**
	 * Returns a dynamic class of a context-aware proxy for {@code endpointClass}.
	 * Exposed for proxy class pre-building.
	 */
	public <EndpointT> Class<? extends EndpointT> getProxyClass(Class<EndpointT> endpointClass) {
		@SuppressWarnings("unchecked")
		final Class<? extends EndpointT> proxyClass = (Class<? extends EndpointT>)
				proxyClasses.computeIfAbsent(endpointClass, this::createProxyClass);
		return proxyClass;
	}

	static final ConcurrentMap<Class<?>, Class<?>> proxyClasses = new ConcurrentHashMap<>();



	/**
	 * Creates a dynamic proxy class that delegates calls to the associated
	 * {@link EndpointProxyHandler} instance.
	 */
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
	 * Additionally checks if {@link OnOpen} annotated method has a {@link Session} param.
	 * <p>
	 * Note the discrepancy between container implementations: Tyrus requires overriding methods to
	 * be re-annotated with @{@link OnOpen} / @{@link OnClose} etc, while Jetty forbids it.<br/>
	 * By default this configurator does Jetty-style checking, so some {@code Endpoints} that don't
	 * meet Tyrus requirements will be allowed to deploy and their overridden life-cycle methods
	 * will not be called by the container.</p>
	 * @throws RuntimeException if the check fails.
	 */
	protected void checkIfRequiredEndpointMethodsPresent(Class<?> endpointClass) {
		final var wantedMethodAnnotationTypes = getRequiredEndpointMethodAnnotationTypes();
		var classUnderScan = endpointClass;
		while ( !classUnderScan.equals(Object.class) && !wantedMethodAnnotationTypes.isEmpty()) {
			for (var method: classUnderScan.getMethods()) {
				final var wantedAnnotationTypeIterator = wantedMethodAnnotationTypes.iterator();
				while (wantedAnnotationTypeIterator.hasNext()) {
					final var wantedAnnotationType = wantedAnnotationTypeIterator.next();
					if (method.isAnnotationPresent(wantedAnnotationType)) {
						wantedAnnotationTypeIterator.remove();
						if (
							wantedAnnotationType.equals(OnOpen.class)
							&& !Arrays.asList(method.getParameterTypes()).contains(Session.class)
						) {
							throw new RuntimeException("method annotated with @OnOpen must have a "
									+ Session.class.getName() + " param");
						}
					}
				}
				if (wantedMethodAnnotationTypes.isEmpty()) break;
			}
			classUnderScan = classUnderScan.getSuperclass();
		}
		if ( !wantedMethodAnnotationTypes.isEmpty()) {
			throw new RuntimeException("endpoint class must have a method annotated with @"
					+ wantedMethodAnnotationTypes.iterator().next().getSimpleName());
		}
	}



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
