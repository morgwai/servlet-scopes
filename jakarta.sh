#!/bin/bash
# Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
if [[ -n "$(git status --porcelain)" ]]; then
	echo "repository not clean, exiting..." >&2;
	exit 1;
fi;

sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
	<pom.xml >pom.jakarta.xml &&
mv pom.jakarta.xml pom.xml &&

sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
	<sample/pom.xml >sample/pom.jakarta.xml &&
mv sample/pom.jakarta.xml sample/pom.xml &&

sed -E -e 's#(\t*).*<!--jakarta:(.*)-->#\1\2#' \
	-e 's#(.*)javax(.*)<!--jakarta-->#\1jakarta\2#' \
	<connection-proxy-tyrus/pom.xml >connection-proxy-tyrus/pom.jakarta.xml &&
mv connection-proxy-tyrus/pom.jakarta.xml connection-proxy-tyrus/pom.xml &&

for folder in src connection-proxy-tyrus/src; do
  find ${folder} -name '*.java' | while read file; do
    sed -e 's#javax.servlet#jakarta.servlet#g' \
      -e 's#javax.websocket#jakarta.websocket#g' \
      -e 's#javax.annotation#jakarta.annotation#g' \
      -e 's#org.eclipse.jetty.websocket.javax#org.eclipse.jetty.websocket.jakarta#g' \
      -e 's#JavaxWebSocket#JakartaWebSocket#g' \
      <"${file}" >"${file}.jakarta" &&
    mv "${file}.jakarta" "${file}";
	done;
done
