#!/usr/bin/env bash
# Full-modpack check of a new ladders jar on northstar, in a memory-capped container:
# The Local's published host kit -> Forge install + pack sync -> boot once (fresh world), then
#   selftest : add the new jar, start with -Dlocalladders.selftest=true (runs every LadderScenarios scenario with Quark
#              and the rest of the pack installed, logs "[localladders-selftest] SUMMARY n/n passed", then stops)
# Usage: [STEPS="boot1 selftest"] tools/fullpack-test.sh <new jar> [workdir=/tmp/lld-fullpack]
# Throwaway only: never point it at a live server. NEVER run a second Minecraft JVM on northstar outside a container
# with a hard memory cap (incident 2026-10-11).
set -euo pipefail
JAR=$(realpath "$1"); W=${2:-/tmp/lld-fullpack}
KIT_URL=https://bdoyl.com/local/minecraft/files/The-Local-Server.zip
NAME=lld-fullpack; IMG=eclipse-temurin:25-jre; JDK=$HOME/opt/jdk-17.0.20.1+1
log() { printf '\n== %s %s\n' "$(date +%H:%M:%S)" "$*"; }

run() { # run <seconds> <stop regex> <env...>: start the kit's start.sh, wait for the regex or the container to exit
  local secs=$1 re=$2; shift 2
  docker rm -f $NAME >/dev/null 2>&1 || true
  docker run -d --name $NAME --memory 2900m --cpus 3 --user "$(id -u):$(id -g)" -e HOME=/tmp -e EULA=true -e MEMORY=2200M \
    "${@/#/-e}" -v "$W/kit:/srv" -v "$JDK:/jdk:ro" -w /srv $IMG \
    sh -c ': > console.txt; export PATH=/jdk/bin:$PATH; tail -f -n0 console.txt | sh start.sh' >/dev/null
  timeout "$secs" bash -c "until docker logs $NAME 2>&1 | grep -qE '$re' || [ \"\$(docker inspect -f '{{.State.Running}}' $NAME)\" != true ]; do sleep 5; done" || true
}
stop() { docker stop -t 90 $NAME >/dev/null 2>&1 || true; docker logs $NAME > "$W/$1.log" 2>&1 || true; docker rm -f $NAME >/dev/null 2>&1 || true; }

mkdir -p "$W"; cd "$W"
if [ ! -d kit ]; then
  log "download + unpack the host kit"
  curl -fsSL -o kit.zip "$KIT_URL"; mkdir kit; (cd kit && unzip -q ../kit.zip); rm kit.zip
  [ -f kit/start.sh ] || { d=$(dirname "$(find kit -name start.sh | head -1)"); mv "$d"/* kit/; }
fi
sed -i 's/^max-tick-time=.*/max-tick-time=-1/' kit/server.properties
STEPS=${STEPS:-boot1 selftest}

if [[ " $STEPS " == *" boot1 "* ]]; then
log "boot 1: Forge install + pack sync + fresh world"
run 900 'Done \(|Exception in server tick loop|Crash report'
stop boot1
grep -E 'Done \(' boot1.log | head -5 || true
fi

if [[ " $STEPS " == *" selftest "* ]]; then
log "boot 2: new jar + selftest"
rm -f kit/mods/localladders-*.jar; cp "$JAR" kit/mods/
grep -q localladders.selftest kit/jvm-flags.txt || echo '-Dlocalladders.selftest=true' >> kit/jvm-flags.txt
run 600 'SUMMARY [0-9]+/[0-9]+ passed' SYNC=0
sleep 10; stop selftest
sed -i '/localladders.selftest/d' kit/jvm-flags.txt
grep -E 'localladders-selftest\] (FAIL|SUMMARY)|Loading.*localladders|Crash report|Done \(' selftest.log | head -40 || true
fi
log "logs in $W (boot1.log, selftest.log); delete $W when done"
