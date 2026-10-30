#!/usr/bin/env bash
# Stops what scripts/dev-up.sh started and clears the adb reverse mappings.
# Only processes recorded by dev-up.sh are stopped; services you started yourself are left running.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/.dev-run"
PORTS=(3000 8000 7880 7881)

echo "==> MetaFrazo dev down"

stop_pid_file() {
  local name="$1" file="$RUN_DIR/$1.pid"
  [ -f "$file" ] || return 0
  local pid
  pid="$(cat "$file")"
  if kill -0 "$pid" 2>/dev/null; then
    # npm spawns next as a child, so stop the whole process group's children too.
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill -TERM "$pid" 2>/dev/null || true
    echo "    stopped $name (pid $pid)"
  fi
  rm -f "$file"
}

stop_pid_file backend
stop_pid_file livekit

if [ -f "$RUN_DIR/livekit.docker" ]; then
  ( cd "$ROOT/infra" && docker compose stop livekit redis >/dev/null 2>&1 ) && echo "    stopped LiveKit containers"
  rm -f "$RUN_DIR/livekit.docker"
fi

ADB_BIN="${ADB:-$(command -v adb || true)}"
if [ -z "$ADB_BIN" ]; then
  sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT/android/local.properties" 2>/dev/null | head -n1)"
  [ -x "$sdk/platform-tools/adb" ] && ADB_BIN="$sdk/platform-tools/adb"
fi
if [ -n "$ADB_BIN" ]; then
  while read -r serial state; do
    [ "$state" = "device" ] || continue
    for port in "${PORTS[@]}"; do
      "$ADB_BIN" -s "$serial" reverse --remove "tcp:$port" >/dev/null 2>&1 || true
    done
    echo "    cleared adb reverse for ${PORTS[*]} on $serial"
  done < <("$ADB_BIN" devices | tail -n +2)
fi
