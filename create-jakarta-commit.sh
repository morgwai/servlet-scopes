#!/bin/bash
# Copyright 2022 Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
if [[ -n "$(git status --porcelain)" ]]; then
	echo "repository not clean, exiting..." >&2;
	exit 1;
fi;
if ! git log -1 --oneline --pretty=%B | grep -q 'release .*-javax'; then
	echo "not a javax release commit, exiting..." >&2;
	exit 2;
fi;

version="$(git log -1 --oneline --pretty=%B | sed -e 's#release ##' -e 's#-javax##')";
./jakarta.sh && git add --all && git commit -m "release ${version}-jakarta" &&
git tag -u 5B5AE744CAD124534DBF0B9369DA23D16BE1A728 \
		-m "v${version}-jakarta" "v${version}-jakarta" &&
./mvnw clean install && ( cd sample; ./mvnw clean package; cd .. )
