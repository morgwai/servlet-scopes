#!/bin/bash
# Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
( echo; echo "building bom:"; echo; cd bom; ./mvnw clean install ) &&
( echo; echo "building main:"; echo; ./mvnw clean install ) &&
( echo; echo "building tyrus:"; echo; cd connection-proxy-tyrus; ./mvnw clean install ) &&
( echo; echo "building sample:"; echo; cd sample; ./mvnw clean package )
