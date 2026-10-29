#!/usr/bin/env bash
# Starts the MetaFrazo backend and wires a USB-connected phone to reach it.
# The recurring "can't reach localhost:3000" error happens when the backend
# isn't running or adb reverse forwarding is missing. This script fixes both.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ADB:-/opt/homebrew/share/android-commandlinetools/platform-tools/adb}"

echo "==> MetaFrazo dev up"

# 1. Reapply USB port forwarding (no-op if no device; adb reverse is per-connection).
if [ -x "$ADB" ]; then
  if "$ADB" get-state >/dev/null 2>&1; then
    for port in 3000 8000 7880 7881; do
      "$ADB" reverse "tcp:$port" "tcp:$port" >/dev/null 2>&1 || true
    done
    echo "    adb reverse set for 3000, 8000, 7880, 7881"
  else
    echo "    no device connected; skipping adb reverse"
  fi
else
  echo "    adb not found at $ADB; skipping adb reverse"
fi

# 2. Start the backend if it is not already listening on :3000.
if lsof -nP -iTCP:3000 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "    backend already running on :3000"
else
  echo "    starting backend (next dev -p 3000)..."
  ( cd "$ROOT/backend" && npm run dev >/tmp/metafrazo-backend.log 2>&1 & )
  # Wait until healthy (up to ~30s).
  for _ in $(seq 1 30); do
    if curl -fsS -o /dev/null http://127.0.0.1:3000/api/health 2>/dev/null; then
      break
    fi
    sleep 1
  done
fi

# 3. Report health.
if curl -fsS -o /dev/null http://127.0.0.1:3000/api/health 2>/dev/null; then
  echo "==> backend healthy at http://127.0.0.1:3000 (logs: /tmp/metafrazo-backend.log)"
else
  echo "==> backend NOT healthy yet; check /tmp/metafrazo-backend.log" >&2
  exit 1
fi
