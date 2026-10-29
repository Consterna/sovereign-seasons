#!/usr/bin/env bash
# Sovereign Seasons - Advanced Minecraft Seasonal Mechanics Plugin
# Copyright (C) 2026 Cristian (@Consterna) <cristianguerrero1958@outlook.com>
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Runs the end-to-end suites against a local Paper server on a fresh world.
#
# Usage: tools/e2e/run-local.sh <server-dir> <java-binary> [mc-version] [--nature] [--extra-plugin <jar>]...
#   <server-dir>  folder with paper.jar, eula.txt and server.properties (see tools/e2e/README.md)
#   <java-binary> Java 21 for Paper 1.21.x, Java 25 for Paper 26.x
#   --nature      also run the slow natural rain / mob spawn checks (e2e-nature.js)
set -euo pipefail

SERVER_DIR="$(cd "$1" && pwd)"
JAVA="$2"
MC_VERSION="${3:-1.21.11}"
shift 3 || shift $#
RUN_NATURE=false
EXTRA_PLUGINS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --nature) RUN_NATURE=true ;;
    --extra-plugin) EXTRA_PLUGINS+=("$2"); shift ;;
  esac
  shift
done

E2E_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$E2E_DIR/../.." && pwd)"
PROPS="$SERVER_DIR/server.properties"
PORT="$(grep '^server-port=' "$PROPS" | cut -d= -f2)"
SERVER_PID=""

start_server() {
  : > "$SERVER_DIR/console.log"
  (cd "$SERVER_DIR" && "$JAVA" -Xms1G -Xmx2G -jar paper.jar --nogui > console.log 2>&1) &
  SERVER_PID=$!
  for _ in $(seq 1 300); do
    if grep -qE 'Done \(' "$SERVER_DIR/console.log"; then return 0; fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then echo "server exited during startup"; tail -n 30 "$SERVER_DIR/console.log"; return 1; fi
    sleep 1
  done
  echo "server did not start in time"; return 1
}

stop_server() {
  node "$E2E_DIR/rcon-cli.js" "$PROPS" stop > /dev/null 2>&1 || true
  if [[ -n "$SERVER_PID" ]]; then wait "$SERVER_PID" 2>/dev/null || true; fi
  SERVER_PID=""
}
trap stop_server EXIT

reset_world() {
  rm -rf "$SERVER_DIR"/world "$SERVER_DIR"/world_nether "$SERVER_DIR"/world_the_end \
         "$SERVER_DIR"/plugins/SovereignSeasons-Core/calendar_data.yml
}

# Fresh plugin jars (and plugin configs) for every run
mkdir -p "$SERVER_DIR/plugins"
rm -rf "$SERVER_DIR"/plugins/sovereign-seasons-*.jar "$SERVER_DIR"/plugins/SovereignSeasons-Core "$SERVER_DIR"/plugins/SovereignSeasons-Pantry
cp "$REPO_DIR"/sovereign-seasons-core/build/libs/sovereign-seasons-core-*.jar "$REPO_DIR"/sovereign-seasons-pantry/build/libs/sovereign-seasons-pantry-*.jar "$SERVER_DIR/plugins/"
for jar in "${EXTRA_PLUGINS[@]}"; do cp "$jar" "$SERVER_DIR/plugins/"; done

# First boot generates the plugin configs; the deterministic test overrides need a restart
reset_world
start_server
node "$E2E_DIR/apply-test-config.js" "$SERVER_DIR"
stop_server

STATUS=0
reset_world
start_server
MC_VERSION="$MC_VERSION" node "$E2E_DIR/e2e.js" "$PROPS" || STATUS=$?
if [[ "$RUN_NATURE" == true ]]; then
  MC_VERSION="$MC_VERSION" node "$E2E_DIR/e2e-nature.js" "$PROPS" || STATUS=$?
fi

echo
echo "Plugin log lines with errors or warnings:"
grep -nE 'SovereignSeasons|Pantry' "$SERVER_DIR/console.log" | grep -E 'ERROR|WARN|Exception' || echo "  none"
grep -nE 'Exception' "$SERVER_DIR/console.log" | grep -viE 'UnknownHostException|PaperVersionFetcher' | head -5 || true
exit "$STATUS"
