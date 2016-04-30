#!/usr/bin/env bash

# Find the directory containing the script.
base_dir=$(pwd)
cd $(dirname $(readlink "$0" || echo "$0"))
code_dir=$(pwd)

# Run the app, bulding it if necessary.
JAR=$(ls "$code_dir"/target/scala-2.11/*-assembly-*.jar 2>/dev/null)
if [ ! -f "$JAR" ]; then
	./sbt assembly
	JAR=$(ls "$code_dir"/target/scala-2.11/*-assembly-*.jar 2>/dev/null)
fi
cd "$base_dir"
java -server -jar "$JAR" $*
