#!/bin/bash
# Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
rm -f build.gradle settings.gradle &&

./gradlew init --type pom --dsl groovy &&
sed -n -e '/^dependencies {/,/^}/p' <build.gradle |head -n -1 |sed -e 's#implementation#api#' \
    >dependencies.txt &&
grep -E 'compileOnly|providedCompile' <dependencies.txt |sed -e 's#compileOnly#testImplementation#' \
    -e 's#providedCompile#testImplementation#' >testDependencies.txt &&
echo '// Generated from build.gradle.header and pom.xml using generate-build.gradle.sh' \
    >build.gradle &&
cat build.gradle.header dependencies.txt testDependencies.txt >>build.gradle &&
echo -e '}\n' >>build.gradle &&
rm dependencies.txt testDependencies.txt &&

echo -n "group = '" >>build.gradle &&
./mvnw -q --non-recursive exec:exec -Dexec.executable=echo '-Dexec.args=-n ${project.groupId}' \
    >>build.gradle &&
echo "'" >>build.gradle &&
echo -n "version = '" >>build.gradle &&
./mvnw -q --non-recursive exec:exec -Dexec.executable=echo '-Dexec.args=-n ${project.version}' \
    >>build.gradle &&
echo "'" >>build.gradle
