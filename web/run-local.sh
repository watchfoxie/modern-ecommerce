#!/usr/bin/env bash
set -euo pipefail

PROFILE="local"
SHUTDOWN_SIGNAL_FILE=""
PID_FILE=""

if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
  PROFILE="$1"
  shift
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --shutdown-signal-file)
      SHUTDOWN_SIGNAL_FILE="${2:?Shutdown signal file path is required}"
      shift 2
      ;;
    --pid-file)
      PID_FILE="${2:?PID file path is required}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [ ! -t 0 ]; then
  echo "run-local.sh requires an interactive console." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="$SCRIPT_DIR/run-local.mjs"
VITE_PACKAGE="$SCRIPT_DIR/node_modules/vite/package.json"

if [ ! -f "$RUNNER" ]; then
  echo "Could not find web launcher at '$RUNNER'." >&2
  exit 1
fi

if [ ! -f "$VITE_PACKAGE" ]; then
  echo "Could not find Vite dependencies at '$VITE_PACKAGE'. Run 'npm install' in '$SCRIPT_DIR' first." >&2
  exit 1
fi

if ! command -v node >/dev/null 2>&1; then
  echo "Could not find 'node' on PATH." >&2
  exit 1
fi

ARGS=("$RUNNER" "--mode" "$PROFILE")

if [ -n "$SHUTDOWN_SIGNAL_FILE" ]; then
  ARGS+=("--shutdown-signal-file" "$SHUTDOWN_SIGNAL_FILE")
fi

if [ -n "$PID_FILE" ]; then
  ARGS+=("--pid-file" "$PID_FILE")
fi

cd "$SCRIPT_DIR"
exec node "${ARGS[@]}"
