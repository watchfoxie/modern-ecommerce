#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-.env.local}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-360}"
SKIP_ORDER_FLOW="${SKIP_ORDER_FLOW:-0}"
KEEP_STACK="${KEEP_STACK:-0}"

if [ ! -f "$REPO_ROOT/$ENV_FILE" ]; then
  echo "Environment file '$REPO_ROOT/$ENV_FILE' was not found." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$REPO_ROOT/$ENV_FILE"
set +a

export APP_DATA_SEED_ENABLED=true

compose() {
  docker compose --env-file "$REPO_ROOT/$ENV_FILE" -f "$REPO_ROOT/compose.yaml" "$@"
}

wait_container_healthy() {
  local container="$1"
  local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
  local status

  while [ "$SECONDS" -lt "$deadline" ]; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [ "$status" = "healthy" ]; then
      return 0
    fi
    sleep 5
  done

  echo "Container '$container' did not become healthy within the timeout." >&2
  return 1
}

http_status() {
  curl -ksS -o /dev/null -w '%{http_code}' "$1"
}

assert_status() {
  local url="$1"
  local expected="$2"
  local timeout="${HTTP_PROBE_TIMEOUT_SECONDS:-90}"
  local deadline=$((SECONDS + timeout))
  local actual=""

  while [ "$SECONDS" -lt "$deadline" ]; do
    actual="$(http_status "$url" || true)"
    if [ "$actual" = "$expected" ]; then
      return 0
    fi
    sleep 3
  done

  echo "Expected HTTP $expected for $url, got $actual." >&2
  exit 1
}

read_json_field() {
  local expression="$1"
  node -e "const fs=require('node:fs'); const data=JSON.parse(fs.readFileSync(0,'utf8')); const value=($expression); if (value === undefined || value === null) process.exit(2); if (typeof value === 'object') console.log(JSON.stringify(value)); else console.log(value);"
}

json_post() {
  local url="$1"
  local body="$2"
  local auth_header="${3:-}"
  if [ -n "$auth_header" ]; then
    curl -ksS -H 'Content-Type: application/json' -H "$auth_header" -d "$body" "$url"
  else
    curl -ksS -H 'Content-Type: application/json' -d "$body" "$url"
  fi
}

json_post_status() {
  local url="$1"
  local body="$2"
  local auth_header="${3:-}"
  local output_file status
  output_file="$(mktemp)"

  if [ -n "$auth_header" ]; then
    status="$(curl -ksS -o "$output_file" -w '%{http_code}' -H 'Content-Type: application/json' -H "$auth_header" -d "$body" "$url")"
  else
    status="$(curl -ksS -o "$output_file" -w '%{http_code}' -H 'Content-Type: application/json' -d "$body" "$url")"
  fi

  cat "$output_file"
  rm -f "$output_file"
  printf '\n%s' "$status"
}

is_transient_status() {
  case "$1" in
    500|502|503|504) return 0 ;;
    *) return 1 ;;
  esac
}

json_post_retry() {
  local url="$1"
  local body="$2"
  local auth_header="${3:-}"
  local timeout="${HTTP_PROBE_TIMEOUT_SECONDS:-90}"
  local deadline=$((SECONDS + timeout))
  local response status payload=""

  while [ "$SECONDS" -lt "$deadline" ]; do
    response="$(json_post_status "$url" "$body" "$auth_header")"
    status="$(printf '%s' "$response" | tail -n 1)"
    payload="$(printf '%s' "$response" | sed '$d')"
    if [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
      printf '%s' "$payload"
      return 0
    fi
    if ! is_transient_status "$status"; then
      printf '%s\n' "$payload" >&2
      exit 1
    fi
    sleep 5
  done

  echo "HTTP request to $url did not recover from transient status $status within $timeout seconds." >&2
  exit 1
}

assert_readiness() {
  local service="$1"
  local port="$2"
  compose exec -T "$service" sh -c "wget -qO- http://127.0.0.1:$port/actuator/health/readiness | grep -q '\"status\":\"UP\"'"
}

assert_rabbitmq_topology() {
  compose exec -T rabbitmq sh -c "rabbitmqctl list_exchanges name | grep -Fx '${ORDER_EVENTS_EXCHANGE:-modern-ecommerce.events}'"
  compose exec -T rabbitmq sh -c "rabbitmqctl list_queues name | grep -Fx '${ORDER_CREATED_QUEUE:-notification.order-created.v1}'"
  compose exec -T rabbitmq sh -c "rabbitmqctl list_queues name | grep -Fx '${ORDER_CREATED_DLQ_QUEUE:-notification.order-created.v1.dlq}'"
  compose exec -T rabbitmq sh -c "rabbitmqctl list_bindings source_name destination_name routing_key | grep '${ORDER_CREATED_QUEUE:-notification.order-created.v1}'"
}

run_order_flow() {
  local base_url="$1"
  local product_json product_id product_name product_slug image_url category_slug price run_id email password token auth_header order_json order_id
  local response status payload deadline

  product_json="$(curl -ksS "$base_url/api/product-service/products?page=0&size=1" | read_json_field 'data.content[0]')"
  product_id="$(printf '%s' "$product_json" | read_json_field 'data.id')"
  product_name="$(printf '%s' "$product_json" | read_json_field 'data.name')"
  product_slug="$(printf '%s' "$product_json" | read_json_field 'data.slug')"
  image_url="$(printf '%s' "$product_json" | read_json_field 'data.imageUrls[0]')"
  category_slug="$(printf '%s' "$product_json" | read_json_field 'data.categorySlug')"
  price="$(printf '%s' "$product_json" | read_json_field 'data.promotionalPrice ?? data.price')"

  if [ -z "$product_id" ] || [ -z "$product_slug" ]; then
    echo "Product catalog returned no products. Enable APP_DATA_SEED_ENABLED or provide seeded MongoDB data." >&2
    exit 1
  fi

  password="Phase7!Pass123"

  deadline=$((SECONDS + 120))
  while [ "$SECONDS" -lt "$deadline" ]; do
    run_id="$(date +%s)-$$-$RANDOM"
    email="phase7-${run_id}@example.test"
    response="$(json_post_status "$base_url/api/auth-service/sign-up" "{\"firstName\":\"Phase\",\"lastName\":\"Seven\",\"email\":\"$email\",\"password\":\"$password\"}")"
    status="$(printf '%s' "$response" | tail -n 1)"
    payload="$(printf '%s' "$response" | sed '$d')"
    if [ "$status" -ge 200 ] && [ "$status" -lt 300 ]; then
      break
    fi
    if ! is_transient_status "$status"; then
      printf '%s\n' "$payload" >&2
      exit 1
    fi
    sleep 5
  done

  if [ -z "$email" ] || [ "$status" -lt 200 ] || [ "$status" -ge 300 ]; then
    echo "Sign-up did not recover from transient status $status within the timeout." >&2
    exit 1
  fi

  token="$(json_post_retry "$base_url/api/auth-service/sign-in" "{\"email\":\"$email\",\"password\":\"$password\"}" | read_json_field 'data.accessToken')"
  auth_header="Authorization: Bearer $token"

  json_post_retry "$base_url/api/cart-service/carts/me/items" "{\"productId\":\"$product_id\",\"quantity\":1,\"priceAtAdd\":$price,\"productSnapshot\":{\"name\":\"$product_name\",\"imageUrl\":\"$image_url\",\"categorySlug\":\"$category_slug\"}}" "$auth_header" >/dev/null
  order_json="$(json_post_retry "$base_url/api/order-service/orders" "{\"deliveryAddress\":{\"street\":\"Stefan cel Mare 1\",\"city\":\"Chisinau\",\"district\":\"Chisinau\",\"postalCode\":null,\"recipientName\":\"Phase Seven\",\"recipientPhone\":\"+37360000000\"},\"payment\":{\"method\":\"CARD\",\"transactionId\":\"phase7-$run_id\"},\"notes\":\"Phase 7 Compose E2E validation\"}" "$auth_header")"
  order_id="$(printf '%s' "$order_json" | read_json_field 'data.orderId')"

  local deadline=$((SECONDS + 90))
  local overview
  while [ "$SECONDS" -lt "$deadline" ]; do
    overview="$(compose exec -T -e "CHECK_TOKEN=${INTERNAL_SERVICE_TOKEN:?INTERNAL_SERVICE_TOKEN must be set}" notification-service sh -c 'wget -qO- --header "X-Internal-Service-Token: ${CHECK_TOKEN}" http://127.0.0.1:${NOTIFICATION_SERVICE_PORT:-8087}/internal/notifications')"
    if printf '%s' "$overview" | node -e "const fs=require('node:fs'); const data=JSON.parse(fs.readFileSync(0,'utf8')); process.exit((data.recentNotifications||[]).some((item)=>item.orderId === '$order_id') ? 0 : 1)"; then
      return 0
    fi
    if printf '%s' "$overview" | node -e "const fs=require('node:fs'); const data=JSON.parse(fs.readFileSync(0,'utf8')); process.exit((data.deadLetterNotifications||[]).some((item)=>item.orderId === '$order_id') ? 0 : 1)"; then
      echo "Order event reached the dead-letter diagnostic store for order $order_id." >&2
      exit 1
    fi
    sleep 5
  done

  echo "Notification-service did not record order.created for order $order_id within the timeout." >&2
  exit 1
}

cleanup() {
  if [ "$KEEP_STACK" = "1" ]; then
    echo "Keeping Compose stack running because KEEP_STACK=1."
    return
  fi
  compose stop || true
  compose down -v || true
}

trap cleanup EXIT

compose config --quiet
compose up -d --build

containers=(
  modern-ecommerce-rabbitmq
  modern-ecommerce-service-registry
  modern-ecommerce-auth-service
  modern-ecommerce-user-service
  modern-ecommerce-category-service
  modern-ecommerce-product-service
  modern-ecommerce-cart-service
  modern-ecommerce-order-service
  modern-ecommerce-notification-service
  modern-ecommerce-api-gateway
  modern-ecommerce-web
)

for container in "${containers[@]}"; do
  wait_container_healthy "$container"
done

compose ps
compose stats --no-stream
compose exec -T rabbitmq sh -c "rabbitmq-diagnostics -q ping"
compose exec -T service-registry sh -c "wget -q --spider http://127.0.0.1:${SERVICE_REGISTRY_PORT:-8761}/"
assert_readiness auth-service "${AUTH_SERVICE_PORT:-8081}"
assert_readiness user-service "${USER_SERVICE_PORT:-8082}"
assert_readiness category-service "${CATEGORY_SERVICE_PORT:-8083}"
assert_readiness product-service "${PRODUCT_SERVICE_PORT:-8084}"
assert_readiness cart-service "${CART_SERVICE_PORT:-8085}"
assert_readiness order-service "${ORDER_SERVICE_PORT:-8086}"
assert_readiness notification-service "${NOTIFICATION_SERVICE_PORT:-8087}"
compose exec -T api-gateway sh -c "wget -qO- http://127.0.0.1:${API_GATEWAY_PORT:-8080}/actuator/health | grep -q '\"status\":\"UP\"'"
compose exec -T web sh -c "wget -q --spider http://127.0.0.1:${VITE_PORT:-5173}/"

BASE_URL="http://127.0.0.1:${VITE_PORT:-5173}"
assert_status "$BASE_URL/" 200
assert_status "$BASE_URL/home" 200
assert_status "$BASE_URL/api/product-service/products" 200
assert_status "$BASE_URL/api/category-service/categories" 200
assert_status "$BASE_URL/api/notification-service/internal/notifications" 404
assert_status "$BASE_URL/api/product-service/internal/products/example" 404
assert_status "$BASE_URL/api/user-service/users/internal/by-auth/example" 404
assert_rabbitmq_topology

if [ "$SKIP_ORDER_FLOW" != "1" ]; then
  run_order_flow "$BASE_URL"
fi

compose logs --tail 100
