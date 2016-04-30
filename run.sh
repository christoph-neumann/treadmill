#!/usr/bin/env bash

# Switch to the directory containing the script.
cd $(dirname $(readlink "$0" || echo "$0"))

# Run the app, bulding it if necessary.
JAR=$(ls target/scala-2.11/*-assembly-*.jar 2>/dev/null)
if [ ! -f "$JAR" ]; then
	./sbt assembly
fi
java -server -jar "$JAR" $*
