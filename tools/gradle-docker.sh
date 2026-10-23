#!/usr/bin/env bash
# Runs ./gradlew <args> in a memory-capped container (the fleet's own cgroup is too small for ModDevGradle + a test
# server). Uses the host's ~/.gradle caches and JDK 21. One run at a time (fixed container name).
set -euo pipefail
cd "$(dirname "$0")/.."
JDK=$HOME/opt/jdk-21.0.12.1+1
exec docker run --rm --name localladders-nf1211-gradle --memory 2600m --cpus 3 --user "$(id -u):$(id -g)" \
  -e HOME=/home/u -e GRADLE_USER_HOME=/home/u/.gradle -e JAVA_HOME=/jdk -e JAVA_TOOL_OPTIONS=-Xmx1100m \
  -v "$HOME/.gradle:/home/u/.gradle" -v "$JDK:/jdk:ro" -v "$PWD:/src" -w /src eclipse-temurin:25-jre \
  ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx900m "$@"
