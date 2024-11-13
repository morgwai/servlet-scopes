// Copyright 2024 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.servlet.guice.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import com.google.inject.BindingAnnotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;



/**
 * Annotation for client {@code Endpoints} that should be injected using a
 * {@link com.google.inject.Provider} based on {@link PingingEndpointConfigurator}.
 * @see PingingWebsocketModule
 * @see pl.morgwai.base.servlet.guice.scopes.GuiceEndpointConfigurator#getProxyForEndpoint(Object,
 *     boolean, boolean) explenation of params
 */
@Retention(RUNTIME)
@Target({ FIELD, PARAMETER })
@BindingAnnotation
public @interface PingingClientEndpoint {

	boolean nestConnectionContext() default true;
	boolean nestHttpSessionContext() default true;
}
