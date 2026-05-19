#!/usr/bin/env bash
set -euo pipefail

PROFILE="local"
STARTUP_DELAY_SECONDS=20
SHUTDOWN_TIMEOUT_SECONDS=60
FORCED_TERMINATION_GRACE_SECONDS=5

if [ $# -gt 0 ] && [[ "$1" != --* ]]; then
  PROFILE="$1"
  shift
fi

while [ $# -gt 0 ]; do
  case "$1" in
    --startup-delay-seconds)
      STARTUP_DELAY_SECONDS="${2:?Startup delay value is required}"
      shift 2
      ;;
    --shutdown-timeout-seconds)
      SHUTDOWN_TIMEOUT_SECONDS="${2:?Shutdown timeout value is required}"
      shift 2
      ;;
    --force-termination-grace-seconds)
      FORCED_TERMINATION_GRACE_SECONDS="${2:?Forced termination grace value is required}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [ ! -t 0 ]; then
  echo "project-run.sh requires an interactive console." >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$(mktemp -d -t modern-ecommerce-project-run.XXXXXX)"
TERMINAL_EMULATOR=""
cleanup_started=0
shutdown_requested=0
rabbitmq_preflight_started=0

COMPONENTS=(
  "service-registry"
  "auth-service"
  "user-service"
  "category-service"
  "product-service"
  "cart-service"
  "notification-service"
  "order-service"
  "api-gateway"
  "web"
)

declare -A SCRIPT_PATHS
declare -A WORKING_DIRECTORIES
declare -A PORT_ENV_VARS
declare -A DEFAULT_PORTS
declare -A REQUIRED_ENV_VARS
declare -A EXTERNAL_PREREQUISITES
declare -A STOP_FILES
declare -A PID_FILES
declare -A RESOLVED_PORTS
started_components=()

SCRIPT_PATHS["service-registry"]="$REPO_ROOT/api/service-registry/run-local.sh"
SCRIPT_PATHS["auth-service"]="$REPO_ROOT/api/auth-service/run-local.sh"
SCRIPT_PATHS["user-service"]="$REPO_ROOT/api/user-service/run-local.sh"
SCRIPT_PATHS["category-service"]="$REPO_ROOT/api/category-service/run-local.sh"
SCRIPT_PATHS["product-service"]="$REPO_ROOT/api/product-service/run-local.sh"
SCRIPT_PATHS["cart-service"]="$REPO_ROOT/api/cart-service/run-local.sh"
SCRIPT_PATHS["notification-service"]="$REPO_ROOT/api/notification-service/run-local.sh"
SCRIPT_PATHS["order-service"]="$REPO_ROOT/api/order-service/run-local.sh"
SCRIPT_PATHS["api-gateway"]="$REPO_ROOT/api/api-gateway/run-local.sh"
SCRIPT_PATHS["web"]="$REPO_ROOT/web/run-local.sh"

WORKING_DIRECTORIES["service-registry"]="$REPO_ROOT/api/service-registry"
WORKING_DIRECTORIES["auth-service"]="$REPO_ROOT/api/auth-service"
WORKING_DIRECTORIES["user-service"]="$REPO_ROOT/api/user-service"
WORKING_DIRECTORIES["category-service"]="$REPO_ROOT/api/category-service"
WORKING_DIRECTORIES["product-service"]="$REPO_ROOT/api/product-service"
WORKING_DIRECTORIES["cart-service"]="$REPO_ROOT/api/cart-service"
WORKING_DIRECTORIES["notification-service"]="$REPO_ROOT/api/notification-service"
WORKING_DIRECTORIES["order-service"]="$REPO_ROOT/api/order-service"
WORKING_DIRECTORIES["api-gateway"]="$REPO_ROOT/api/api-gateway"
WORKING_DIRECTORIES["web"]="$REPO_ROOT/web"

PORT_ENV_VARS["service-registry"]="SERVICE_REGISTRY_PORT"
PORT_ENV_VARS["auth-service"]="AUTH_SERVICE_PORT"
PORT_ENV_VARS["user-service"]="USER_SERVICE_PORT"
PORT_ENV_VARS["category-service"]="CATEGORY_SERVICE_PORT"
PORT_ENV_VARS["product-service"]="PRODUCT_SERVICE_PORT"
PORT_ENV_VARS["cart-service"]="CART_SERVICE_PORT"
PORT_ENV_VARS["notification-service"]="NOTIFICATION_SERVICE_PORT"
PORT_ENV_VARS["order-service"]="ORDER_SERVICE_PORT"
PORT_ENV_VARS["api-gateway"]="API_GATEWAY_PORT"
PORT_ENV_VARS["web"]="VITE_PORT"

DEFAULT_PORTS["service-registry"]="8761"
DEFAULT_PORTS["auth-service"]="8081"
DEFAULT_PORTS["user-service"]="8082"
DEFAULT_PORTS["category-service"]="8083"
DEFAULT_PORTS["product-service"]="8084"
DEFAULT_PORTS["cart-service"]="8085"
DEFAULT_PORTS["notification-service"]="8087"
DEFAULT_PORTS["order-service"]="8086"
DEFAULT_PORTS["api-gateway"]="8080"
DEFAULT_PORTS["web"]="5173"

REQUIRED_ENV_VARS["service-registry"]=""
REQUIRED_ENV_VARS["auth-service"]="AUTH_MONGODB_URI"
REQUIRED_ENV_VARS["user-service"]="USER_MONGODB_URI"
REQUIRED_ENV_VARS["category-service"]="CATEGORY_MONGODB_URI"
REQUIRED_ENV_VARS["product-service"]="PRODUCT_MONGODB_URI"
REQUIRED_ENV_VARS["cart-service"]="CART_MONGODB_URI"
REQUIRED_ENV_VARS["notification-service"]=""
REQUIRED_ENV_VARS["order-service"]="ORDER_MONGODB_URI"
REQUIRED_ENV_VARS["api-gateway"]=""
REQUIRED_ENV_VARS["web"]=""

EXTERNAL_PREREQUISITES["service-registry"]="Eureka must be able to bind its local port."
EXTERNAL_PREREQUISITES["auth-service"]="MongoDB must be reachable through AUTH_MONGODB_URI."
EXTERNAL_PREREQUISITES["user-service"]="MongoDB must be reachable through USER_MONGODB_URI."
EXTERNAL_PREREQUISITES["category-service"]="MongoDB must be reachable through CATEGORY_MONGODB_URI."
EXTERNAL_PREREQUISITES["product-service"]="MongoDB must be reachable through PRODUCT_MONGODB_URI."
EXTERNAL_PREREQUISITES["cart-service"]="MongoDB must be reachable through CART_MONGODB_URI."
EXTERNAL_PREREQUISITES["notification-service"]="RabbitMQ must be reachable through RABBITMQ_* settings. SMTP credentials are required only when NOTIFICATION_MAIL_ENABLED=true."
EXTERNAL_PREREQUISITES["order-service"]="MongoDB must be reachable through ORDER_MONGODB_URI and RabbitMQ must be reachable through RABBITMQ_* settings."
EXTERNAL_PREREQUISITES["api-gateway"]="Downstream services should have time to register in Eureka before the frontend starts."
EXTERNAL_PREREQUISITES["web"]="Node.js and installed frontend dependencies are required."

get_environment_files() {
  local files=()
  local profile_env="$REPO_ROOT/.env.$PROFILE"
  local local_env="$REPO_ROOT/.env.local"

  if [ -f "$profile_env" ]; then
    files+=("$profile_env")
  fi

  if [ "$local_env" != "$profile_env" ] && [ -f "$local_env" ]; then
    files+=("$local_env")
  fi

  printf '%s\n' "${files[@]}"
}

load_env_files() {
  local env_file line key value

  while IFS= read -r env_file; do
    [ -n "$env_file" ] || continue

    while IFS= read -r line || [ -n "$line" ]; do
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
      key="${key#"${key%%[![:space:]]*}"}"
      key="${key%"${key##*[![:space:]]}"}"

      if [ -z "$key" ] || [ -n "${!key+x}" ]; then
        continue
      fi

      export "$key=$value"
    done < "$env_file"
  done < <(get_environment_files)
}

compose_base_args() {
  local local_env="$REPO_ROOT/.env.local"

  if [ -f "$local_env" ]; then
    printf '%s\0' "--env-file" "$local_env" "-f" "$REPO_ROOT/compose.yaml"
  else
    printf '%s\0' "-f" "$REPO_ROOT/compose.yaml"
  fi
}

compose_rabbitmq_args() {
  local item

  while IFS= read -r -d '' item; do
    printf '%s\0' "$item"
  done < <(compose_base_args)

  if [ -f "$REPO_ROOT/compose.debug.yaml" ]; then
    printf '%s\0' "-f" "$REPO_ROOT/compose.debug.yaml"
  fi
}

docker_compose() {
  local compose_args=()
  local item

  while IFS= read -r -d '' item; do
    compose_args+=("$item")
  done < <(compose_base_args)

  docker compose "${compose_args[@]}" "$@"
}

docker_compose_rabbitmq() {
  local compose_args=()
  local item

  while IFS= read -r -d '' item; do
    compose_args+=("$item")
  done < <(compose_rabbitmq_args)

  docker compose "${compose_args[@]}" "$@"
}

ensure_rabbitmq() {
  if [[ "${PROJECT_RUN_SKIP_RABBITMQ_PREFLIGHT:-}" =~ ^(1|true|TRUE|yes|YES)$ ]]; then
    echo "Skipping RabbitMQ preflight because PROJECT_RUN_SKIP_RABBITMQ_PREFLIGHT is enabled."
    return
  fi

  if ! command -v docker >/dev/null 2>&1; then
    echo "Could not find 'docker' on PATH. It is required to start RabbitMQ for local sequential runs." >&2
    exit 1
  fi

  if [ ! -f "$REPO_ROOT/.env.local" ]; then
    echo ".env.local is required for RabbitMQ preflight because the hardened Compose profile fails fast on required secrets." >&2
    exit 1
  fi

  echo "Ensuring RabbitMQ is available through Docker Compose..."
  local rabbitmq_already_running=0
  if docker_compose_rabbitmq ps --status running --services rabbitmq 2>/dev/null | grep -qx "rabbitmq"; then
    rabbitmq_already_running=1
  fi

  docker_compose_rabbitmq up -d --build rabbitmq
  if [ "$rabbitmq_already_running" -eq 0 ]; then
    rabbitmq_preflight_started=1
  fi

  local deadline=$((SECONDS + 120))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if docker_compose_rabbitmq exec -T rabbitmq sh -c "rabbitmq-diagnostics -q ping" >/dev/null 2>&1; then
      echo "RabbitMQ preflight passed."
      return
    fi

    sleep 3
  done

  echo "RabbitMQ did not become healthy within the preflight timeout." >&2
  exit 1
}

stop_rabbitmq_preflight() {
  if [ "$rabbitmq_preflight_started" -ne 1 ]; then
    return
  fi

  if [[ "${PROJECT_RUN_STOP_RABBITMQ_ON_EXIT:-}" =~ ^(0|false|FALSE|no|NO)$ ]]; then
    echo "Leaving RabbitMQ running because PROJECT_RUN_STOP_RABBITMQ_ON_EXIT is disabled."
    return
  fi

  echo "Stopping RabbitMQ preflight container..."
  docker_compose_rabbitmq stop rabbitmq >/dev/null 2>&1 || true
}

resolve_port() {
  local env_var="$1"
  local default_port="$2"
  local value="${!env_var:-$default_port}"

  if ! [[ "$value" =~ ^[0-9]+$ ]]; then
    echo "Environment variable '$env_var' must be an integer. Current value: '$value'." >&2
    exit 1
  fi

  printf '%s\n' "$value"
}

port_in_use() {
  local port="$1"

  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1
    return $?
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -ltn "( sport = :$port )" 2>/dev/null | tail -n +2 | grep -q .
    return $?
  fi

  if command -v netstat >/dev/null 2>&1; then
    netstat -ltn 2>/dev/null | grep -E "[.:]$port[[:space:]]" >/dev/null
    return $?
  fi

  return 1
}

detect_terminal_emulator() {
  if [ "$(uname -s)" = "Darwin" ] && command -v osascript >/dev/null 2>&1; then
    TERMINAL_EMULATOR="osascript"
    return
  fi

  local candidates=(
    "gnome-terminal"
    "konsole"
    "xfce4-terminal"
    "mate-terminal"
    "alacritty"
    "kitty"
    "xterm"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if command -v "$candidate" >/dev/null 2>&1; then
      TERMINAL_EMULATOR="$candidate"
      return
    fi
  done

  echo "Could not find a supported terminal emulator. Install one of: gnome-terminal, konsole, xfce4-terminal, mate-terminal, alacritty, kitty, xterm, or use Terminal.app on macOS." >&2
  exit 1
}

launch_in_terminal() {
  local launch_script="$1"
  local apple_command

  case "$TERMINAL_EMULATOR" in
    gnome-terminal|mate-terminal)
      "$TERMINAL_EMULATOR" -- bash "$launch_script" >/dev/null 2>&1 &
      ;;
    konsole)
      konsole -e bash "$launch_script" >/dev/null 2>&1 &
      ;;
    xfce4-terminal)
      xfce4-terminal -x bash "$launch_script" >/dev/null 2>&1 &
      ;;
    alacritty)
      alacritty -e bash "$launch_script" >/dev/null 2>&1 &
      ;;
    kitty)
      kitty bash "$launch_script" >/dev/null 2>&1 &
      ;;
    xterm)
      xterm -e bash "$launch_script" >/dev/null 2>&1 &
      ;;
    osascript)
      printf -v apple_command 'bash %q' "$launch_script"
      apple_command="${apple_command//\\/\\\\}"
      apple_command="${apple_command//\"/\\\"}"
      osascript -e "tell application \"Terminal\" to do script \"$apple_command\"" >/dev/null
      ;;
    *)
      echo "Unsupported terminal emulator: $TERMINAL_EMULATOR" >&2
      exit 1
      ;;
  esac
}

wait_for_pid_file() {
  local component_name="$1"
  local pid_file="$2"
  local deadline=$((SECONDS + 20))

  while [ "$SECONDS" -lt "$deadline" ]; do
    if [ -s "$pid_file" ]; then
      return 0
    fi

    sleep 0.2
  done

  echo "Timed out while waiting for $component_name to register its PID file." >&2
  return 1
}

component_pid() {
  local name="$1"
  local pid_file="${PID_FILES[$name]}"

  if [ ! -s "$pid_file" ]; then
    return 1
  fi

  tr -d '[:space:]' < "$pid_file"
}

component_is_running() {
  local name="$1"
  local pid

  if ! pid="$(component_pid "$name")"; then
    return 1
  fi

  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

wait_for_component_stop() {
  local name="$1"
  local timeout_seconds="$2"
  local deadline

  if ! component_is_running "$name"; then
    return 0
  fi

  if [ "$timeout_seconds" -le 0 ]; then
    return 1
  fi

  deadline=$((SECONDS + timeout_seconds))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if ! component_is_running "$name"; then
      return 0
    fi

    sleep 1
  done

  ! component_is_running "$name"
}

start_component() {
  local name="$1"
  local stop_file="$STATE_DIR/$name.stop"
  local pid_file="$STATE_DIR/$name.pid"
  local launch_script="$STATE_DIR/launch-$name.sh"
  local script_path="${SCRIPT_PATHS[$name]}"
  local working_directory="${WORKING_DIRECTORIES[$name]}"
  local quoted_script quoted_workdir quoted_profile quoted_stop quoted_pid

  STOP_FILES["$name"]="$stop_file"
  PID_FILES["$name"]="$pid_file"

  rm -f "$stop_file" "$pid_file"

  printf -v quoted_script '%q' "$script_path"
  printf -v quoted_workdir '%q' "$working_directory"
  printf -v quoted_profile '%q' "$PROFILE"
  printf -v quoted_stop '%q' "$stop_file"
  printf -v quoted_pid '%q' "$pid_file"

cat > "$launch_script" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd $quoted_workdir
exec bash $quoted_script $quoted_profile --shutdown-signal-file $quoted_stop --pid-file $quoted_pid
EOF

  chmod +x "$launch_script"
  launch_in_terminal "$launch_script"
  wait_for_pid_file "$name" "$pid_file"
  started_components+=("$name")
}

stop_component() {
  local name="$1"
  local stop_file="${STOP_FILES[$name]}"
  local pid_file="${PID_FILES[$name]}"
  local deadline pid

  echo "Stopping $name..."

  if ! component_is_running "$name"; then
    echo "$name is already stopped."
    return
  fi

  : > "$stop_file"
  deadline=$((SECONDS + SHUTDOWN_TIMEOUT_SECONDS))

  while [ "$SECONDS" -lt "$deadline" ]; do
    if ! component_is_running "$name"; then
      echo "$name stopped gracefully."
      return
    fi

    sleep 1
  done

  echo "Warning: $name did not stop within $SHUTDOWN_TIMEOUT_SECONDS seconds. Attempting controlled termination before forcing it." >&2

  if pid="$(component_pid "$name" 2>/dev/null)"; then
    kill -TERM "$pid" 2>/dev/null || true
    if wait_for_component_stop "$name" "$FORCED_TERMINATION_GRACE_SECONDS"; then
      echo "$name stopped after the SIGTERM fallback."
      return
    fi

    if kill -0 "$pid" 2>/dev/null; then
      echo "Warning: $name did not exit after the SIGTERM fallback. Sending SIGKILL." >&2
      kill -KILL "$pid" 2>/dev/null || true
    fi
  fi

  rm -f "$pid_file"
}

cleanup() {
  local index

  if [ "$cleanup_started" -eq 1 ]; then
    return
  fi

  cleanup_started=1

  for ((index=${#started_components[@]} - 1; index>=0; index--)); do
    stop_component "${started_components[$index]}"
  done

  stop_rabbitmq_preflight
  rm -rf "$STATE_DIR"
}

trap 'shutdown_requested=1' INT TERM
trap cleanup EXIT

load_env_files
ensure_rabbitmq
detect_terminal_emulator

if ! command -v node >/dev/null 2>&1; then
  echo "Could not find 'node' on PATH. It is required to launch the Vite frontend." >&2
  exit 1
fi

missing_reports=()
busy_port_reports=()

for name in "${COMPONENTS[@]}"; do
  if [ ! -f "${SCRIPT_PATHS[$name]}" ]; then
    echo "Could not find launcher script for $name at '${SCRIPT_PATHS[$name]}'." >&2
    exit 1
  fi

  if [ -n "${REQUIRED_ENV_VARS[$name]}" ]; then
    IFS=',' read -r -a required_vars <<< "${REQUIRED_ENV_VARS[$name]}"
    missing_vars=()
    for variable_name in "${required_vars[@]}"; do
      if [ -z "${!variable_name:-}" ]; then
        missing_vars+=("$variable_name")
      fi
    done

    if [ "${#missing_vars[@]}" -gt 0 ]; then
      missing_reports+=("$name: ${missing_vars[*]}")
    fi
  fi

  RESOLVED_PORTS["$name"]="$(resolve_port "${PORT_ENV_VARS[$name]}" "${DEFAULT_PORTS[$name]}")"
  if port_in_use "${RESOLVED_PORTS[$name]}"; then
    busy_port_reports+=("$name: port ${RESOLVED_PORTS[$name]} is already in use")
  fi
done

if [ "${#missing_reports[@]}" -gt 0 ]; then
  printf 'Missing required environment variables:\n' >&2
  printf ' - %s\n' "${missing_reports[@]}" >&2
  exit 1
fi

if [ "${#busy_port_reports[@]}" -gt 0 ]; then
  printf 'Port preflight failed:\n' >&2
  printf ' - %s\n' "${busy_port_reports[@]}" >&2
  exit 1
fi

echo "Launching modern-ecommerce in profile '$PROFILE'."
echo "Each component will open in a dedicated terminal window using $TERMINAL_EMULATOR."
echo "External infrastructure is not started automatically:"
for name in "${COMPONENTS[@]}"; do
  echo " - $name -> port ${RESOLVED_PORTS[$name]}: ${EXTERNAL_PREREQUISITES[$name]}"
done

for ((index=0; index<${#COMPONENTS[@]}; index++)); do
  name="${COMPONENTS[$index]}"
  echo "Starting $name on port ${RESOLVED_PORTS[$name]}..."
  start_component "$name"

  for running_component in "${started_components[@]}"; do
    if ! component_is_running "$running_component"; then
      echo "Error: $running_component exited during the startup sequence." >&2
      exit 1
    fi
  done

  if [ "$index" -lt $((${#COMPONENTS[@]} - 1)) ] && [ "$STARTUP_DELAY_SECONDS" -gt 0 ]; then
    echo "Waiting $STARTUP_DELAY_SECONDS seconds before launching the next component..."
    sleep "$STARTUP_DELAY_SECONDS"

    for running_component in "${started_components[@]}"; do
      if ! component_is_running "$running_component"; then
        echo "Error: $running_component exited during the startup sequence." >&2
        exit 1
      fi
    done
  fi
done

echo "All components are running. Press Ctrl+C, Q, or Enter to stop them in reverse order."

while [ "$shutdown_requested" -eq 0 ]; do
  for name in "${started_components[@]}"; do
    if ! component_is_running "$name"; then
      echo "Warning: $name exited unexpectedly. Initiating graceful shutdown." >&2
      shutdown_requested=1
      break
    fi
  done

  [ "$shutdown_requested" -eq 1 ] && break

  if IFS= read -r -s -n1 -t 0.2 key; then
    case "$key" in
      $'\n'|$'\r'|[qQ])
        shutdown_requested=1
        ;;
    esac
  fi
done

exit 0
