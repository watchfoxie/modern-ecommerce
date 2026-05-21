#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${1:?Service name is required}"
MODULE_DIR="${2:?Module directory is required}"
REQUIRED_VARS_CSV="${3:-}"
shift 3

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

MODULE_DIR="$(cd "$MODULE_DIR" && pwd)"
REPO_ROOT="$(cd "$MODULE_DIR/../.." && pwd)"
SOURCE_ROOT="$MODULE_DIR/src/main/java"

PROFILE_ENV_FILE="$REPO_ROOT/.env.$PROFILE"
LOCAL_ENV_FILE="$REPO_ROOT/.env.local"

ENV_FILES=()
if [ -f "$PROFILE_ENV_FILE" ]; then
  ENV_FILES+=("$PROFILE_ENV_FILE")
fi
if [ "$LOCAL_ENV_FILE" != "$PROFILE_ENV_FILE" ] && [ -f "$LOCAL_ENV_FILE" ]; then
  ENV_FILES+=("$LOCAL_ENV_FILE")
fi

MAVEN_WRAPPER="$MODULE_DIR/mvnw"
if [ ! -x "$MAVEN_WRAPPER" ]; then
  if [ -x "$MODULE_DIR/mvnw.cmd" ]; then
    MAVEN_WRAPPER="$MODULE_DIR/mvnw.cmd"
  else
    echo "Could not find Maven wrapper at '$MODULE_DIR'." >&2
    exit 1
  fi
fi

started=0
stopped=0
cleaned_up=0
location_pushed=0

load_env_files() {
  local env_file line key value

  for env_file in "${ENV_FILES[@]}"; do
    while IFS= read -r line || [ -n "$line" ]; do
      line="${line%$'\r'}"

      case "$line" in
        ""|\#*)
          continue
          ;;
      esac

      if [[ "$line" != *=* ]]; then
        continue
      fi

      key="${line%%=*}"
      value="${line#*=}"

      if [ -z "$key" ]; then
        continue
      fi

      if [ -z "${!key+x}" ]; then
        export "$key=$value"
      fi
    done < "$env_file"
  done
}

validate_required_env_vars() {
  local missing=()
  local key
  local env_locations

  if [ -z "$REQUIRED_VARS_CSV" ]; then
    return
  fi

  IFS=',' read -r -a required_vars <<< "$REQUIRED_VARS_CSV"
  for key in "${required_vars[@]}"; do
    if [ -z "$key" ]; then
      continue
    fi

    if [ -z "${!key-}" ]; then
      missing+=("$key")
    fi
  done

  if [ ${#missing[@]} -eq 0 ]; then
    return
  fi

  if [ ${#ENV_FILES[@]} -eq 0 ]; then
    env_locations="the current environment"
  else
    env_locations="$(printf "'%s', " "${ENV_FILES[@]}")"
    env_locations="${env_locations%, }"
  fi

  echo "Missing required environment variables for $SERVICE_NAME: ${missing[*]}." >&2
  echo "Define them in $env_locations or export them before launching the service." >&2
  exit 1
}

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

get_main_class() {
  local candidate_file
  local package_name
  local class_name

  if [ ! -d "$SOURCE_ROOT" ]; then
    echo "Could not find source directory at '$SOURCE_ROOT'." >&2
    exit 1
  fi

  candidate_file="$(find "$SOURCE_ROOT" -name "*Application.java" -exec grep -l "@SpringBootApplication" {} + 2>/dev/null | head -n 1)"
  if [ -z "$candidate_file" ]; then
    candidate_file="$(find "$SOURCE_ROOT" -name "*Application.java" | head -n 1)"
  fi

  if [ -z "$candidate_file" ]; then
    echo "Could not infer a Spring Boot application class under '$SOURCE_ROOT'." >&2
    exit 1
  fi

  package_name="$(grep -m1 '^[[:space:]]*package[[:space:]]\+' "$candidate_file" | sed -E 's/^[[:space:]]*package[[:space:]]+([^;]+);/\1/' || true)"
  class_name="$(basename "$candidate_file" .java)"

  if [ -z "$package_name" ]; then
    printf '%s\n' "$class_name"
  else
    printf '%s.%s\n' "$package_name" "$class_name"
  fi
}

stop_service() {
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
  stop_service
  if [ "$location_pushed" -eq 1 ]; then
    popd >/dev/null
  fi
  remove_pid_file
}

if [ ${#ENV_FILES[@]} -gt 0 ]; then
  load_env_files
fi

validate_required_env_vars

MAIN_CLASS="$(get_main_class)"
write_pid_file

trap cleanup EXIT INT TERM

pushd "$MODULE_DIR" >/dev/null
location_pushed=1

invoke_maven compile
invoke_maven spring-boot:start "-Dspring-boot.run.profiles=$PROFILE" "-Dspring-boot.run.main-class=$MAIN_CLASS"
started=1

echo "$SERVICE_NAME is running. Press Ctrl+C to stop gracefully."
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
