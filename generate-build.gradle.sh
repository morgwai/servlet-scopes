#!/bin/bash
rm -f build.gradle settings.gradle &&
./gradlew init --type pom --dsl groovy &&
sed -n -e '/^dependencies {/,/^}/p' <build.gradle >dependencies.txt &&
cat build.gradle.header dependencies.txt >build.gradle &&
rm dependencies.txt &&
echo >>build.gradle &&
echo -n "group = '" >>build.gradle &&
mvn -q --non-recursive exec:exec -Dexec.executable=echo '-Dexec.args=-n ${project.groupId}' >>build.gradle &&
echo "'" >>build.gradle &&
echo -n "version = '" >>build.gradle &&
mvn -q --non-recursive exec:exec -Dexec.executable=echo '-Dexec.args=-n ${project.version}' >>build.gradle &&
echo "'" >>build.gradle
