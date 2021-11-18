#!/bin/bash
# Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-experimental-->#\1jakarta-experimental\2#' \
	<pom.xml >pom.jakarta.xml &&
mv pom.jakarta.xml pom.xml &&

sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-experimental-->#\1jakarta-experimental\2#' \
	<sample/pom.xml >sample/pom.jakarta.xml &&
mv sample/pom.jakarta.xml sample/pom.xml &&

find src -name '*.java' | while read file; do
	sed -e 's#javax.servlet#jakarta.servlet#g' \
		-e 's#javax.websocket#jakarta.websocket#g' \
		-e 's#org.eclipse.jetty.websocket.javax.server.config.JavaxWebSocketServletContainerInitializer#org.eclipse.jetty.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer#g' \
		<"${file}" >"${file}.jakarta" &&
	mv "${file}.jakarta" "${file}";
done
