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

MAVEN_WRAPPER="$SCRIPT_DIR/mvnw"
if [ ! -x "$MAVEN_WRAPPER" ]; then
  if [ -x "$SCRIPT_DIR/mvnw.cmd" ]; then
    MAVEN_WRAPPER="$SCRIPT_DIR/mvnw.cmd"
  else
    echo "Could not find Maven wrapper at '$SCRIPT_DIR'." >&2
    exit 1
  fi
fi

started=0
stopped=0
cleaned_up=0
location_pushed=0

invoke_maven() {
  "$MAVEN_WRAPPER" "$@"
  local rc=$?
  if [ $rc -ne 0 ]; then
    echo "Maven command failed with exit code $rc: $MAVEN_WRAPPER $*" >&2
    exit $rc
  fi
}

write_pid_file() {
  local parent_dir

  if [ -z "$PID_FILE" ]; then
    return
  fi

  parent_dir="$(dirname "$PID_FILE")"
  mkdir -p "$parent_dir"
  printf '%s' "$$" > "$PID_FILE"
}

remove_pid_file() {
  if [ -z "$PID_FILE" ]; then
    return
  fi

  rm -f "$PID_FILE"
}

stop_api_gateway() {
  if [ $started -eq 0 ] || [ $stopped -eq 1 ]; then
    return
  fi
  invoke_maven spring-boot:stop
  stopped=1
}

cleanup() {
  if [ "$cleaned_up" -eq 1 ]; then
    return
  fi

  cleaned_up=1
  stop_api_gateway
  if [ "$location_pushed" -eq 1 ]; then
    popd >/dev/null
  fi
  remove_pid_file
}

trap cleanup EXIT INT TERM

write_pid_file

pushd "$SCRIPT_DIR" >/dev/null
location_pushed=1

invoke_maven compile
invoke_maven spring-boot:start "-Dspring-boot.run.profiles=$PROFILE"
started=1

echo "api-gateway is running. Press Ctrl+C to stop gracefully."
echo "Press Q or Enter if your terminal does not forward Ctrl+C as input."

while true; do
  if [ -n "$SHUTDOWN_SIGNAL_FILE" ] && [ -f "$SHUTDOWN_SIGNAL_FILE" ]; then
    break
  fi

  if IFS= read -r -s -n1 -t 0.2 key; then
    if [[ "$key" == $'\n' ]] || [[ "$key" == $'\r' ]] || [[ "$key" == "q" ]] || [[ "$key" == "Q" ]]; then
      break
    fi
  fi
done

cleanup
exit 0
