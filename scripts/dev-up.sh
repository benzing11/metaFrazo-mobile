#!/usr/bin/env bash
# Starts the MetaFrazo dev stack (backend + LiveKit) and wires a USB-connected phone to reach it.
# Joining a meeting needs both: the backend issues the token, LiveKit carries the media. If either
# is down or adb reverse forwarding is missing, create/join fails on the phone.
# Idempotent: services already listening are left alone. Stop with scripts/dev-down.sh.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT/.dev-run"
BACKEND_LOG="$RUN_DIR/backend.log"
LIVEKIT_LOG="$RUN_DIR/livekit.log"
PORTS=(3000 8000 7880 7881)

mkdir -p "$RUN_DIR"

listening() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

# Polls a URL until it answers (any HTTP status) or the timeout in seconds elapses.
wait_for() {
  local url="$1" timeout="$2"
  for _ in $(seq 1 "$timeout"); do
    if curl -sS -o /dev/null -m 2 "$url" 2>/dev/null; then return 0; fi
    sleep 1
  done
  return 1
}

# adb is often not on PATH; fall back to the SDK configured for the Android build.
resolve_adb() {
  if [ -n "${ADB:-}" ] && [ -x "$ADB" ]; then echo "$ADB"; return; fi
  if command -v adb >/dev/null 2>&1; then command -v adb; return; fi
  local sdk
  sdk="$(sed -n 's/^sdk\.dir=//p' "$ROOT/android/local.properties" 2>/dev/null | head -n1)"
  for candidate in "$sdk/platform-tools/adb" \
    "/opt/homebrew/share/android-commandlinetools/platform-tools/adb" \
    "$HOME/Library/Android/sdk/platform-tools/adb"; do
    if [ -x "$candidate" ]; then echo "$candidate"; return; fi
  done
}

echo "==> MetaFrazo dev up"

# 1. Backend (Next.js) on :3000.
if listening 3000; then
  echo "    backend already running on :3000"
else
  echo "    starting backend (next dev -p 3000)..."
  ( cd "$ROOT/backend" && nohup npm run dev >"$BACKEND_LOG" 2>&1 & echo $! >"$RUN_DIR/backend.pid" )
fi

# 2. LiveKit on :7880 (signal) / :7881 (RTC over TCP). `--dev` uses the devkey/secret pair the
#    backend falls back to in development.
if listening 7880; then
  echo "    LiveKit already running on :7880"
elif command -v livekit-server >/dev/null 2>&1; then
  echo "    starting LiveKit (livekit-server --dev)..."
  # --bind 0.0.0.0 so phones on the same Wi-Fi can reach it, not only adb reverse.
  nohup livekit-server --dev --bind 0.0.0.0 >"$LIVEKIT_LOG" 2>&1 &
  echo $! >"$RUN_DIR/livekit.pid"
elif command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "    starting LiveKit via docker compose (infra/)..."
  ( cd "$ROOT/infra" && docker compose up -d redis livekit >/dev/null )
  touch "$RUN_DIR/livekit.docker"
else
  echo "==> LiveKit not found. Install it (brew install livekit) or start Docker." >&2
  exit 1
fi

# 3. USB port forwarding for every attached device. adb reverse is per-connection, so it is
#    reapplied every run.
ADB_BIN="$(resolve_adb || true)"
DEVICES=()
if [ -n "$ADB_BIN" ]; then
  while read -r serial state; do
    [ "$state" = "device" ] && DEVICES+=("$serial")
  done < <("$ADB_BIN" devices | tail -n +2)
fi
if [ -z "$ADB_BIN" ]; then
  echo "    adb not found; skipping adb reverse (fine for emulator/Wi-Fi setups)"
elif [ "${#DEVICES[@]}" -eq 0 ]; then
  echo "    no device connected; skipping adb reverse"
else
  for serial in "${DEVICES[@]}"; do
    for port in "${PORTS[@]}"; do
      "$ADB_BIN" -s "$serial" reverse "tcp:$port" "tcp:$port" >/dev/null
    done
    echo "    adb reverse set for ${PORTS[*]} on $serial"
  done
fi

# 4. Health. Next dev compiles on first request, so give it some headroom.
status=0
if wait_for http://127.0.0.1:3000/api/health 60; then
  echo "==> backend healthy at http://127.0.0.1:3000"
else
  echo "==> backend NOT healthy; check $BACKEND_LOG" >&2
  status=1
fi
if wait_for http://127.0.0.1:7880 30; then
  echo "==> LiveKit healthy at ws://127.0.0.1:7880"
else
  echo "==> LiveKit NOT healthy; check $LIVEKIT_LOG" >&2
  status=1
fi

for serial in ${DEVICES[@]+"${DEVICES[@]}"}; do
  if "$ADB_BIN" -s "$serial" shell 'curl -s -m 5 -o /dev/null http://localhost:3000/api/health' >/dev/null 2>&1; then
    echo "==> $serial can reach the backend over USB"
  else
    echo "    could not confirm $serial reachability (device may lack curl); adb reverse is set"
  fi
done

echo "    logs: $RUN_DIR"
exit "$status"
