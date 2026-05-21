#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <service-name> [profile]"
  exit 1
fi

service_name="$1"
profile="${2:-local}"

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(dirname "$(dirname "$script_dir")")"

case "$service_name" in
  auth-service)
    module_dir="$repo_root/api/auth-service"
    main_class="md.services.auth_service.connection.CheckDatabaseConnection"
    ;;
  user-service)
    module_dir="$repo_root/api/user-service"
    main_class="md.services.user_service.connection.CheckDatabaseConnection"
    ;;
  category-service)
    module_dir="$repo_root/api/category-service"
    main_class="md.services.category_service.connection.CheckDatabaseConnection"
    ;;
  product-service)
    module_dir="$repo_root/api/product-service"
    main_class="md.services.product_service.connection.CheckDatabaseConnection"
    ;;
  cart-service)
    module_dir="$repo_root/api/cart-service"
    main_class="md.services.cart_service.connection.CheckDatabaseConnection"
    ;;
  order-service)
    module_dir="$repo_root/api/order-service"
    main_class="md.services.order_service.connection.CheckDatabaseConnection"
    ;;
  *)
    echo "Unsupported service name '$service_name'."
    exit 1
    ;;
esac

maven_wrapper="$module_dir/mvnw"
if [[ ! -x "$maven_wrapper" ]]; then
  echo "Could not find Maven wrapper at '$maven_wrapper'."
  exit 1
fi

pushd "$module_dir" >/dev/null
classpath_file="$module_dir/target/database-connection-check.classpath"

set +e
"$maven_wrapper" -q -DskipTests compile dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/database-connection-check.classpath
prepare_exit_code=$?
set -e

if [[ $prepare_exit_code -ne 0 ]]; then
  rm -f "$classpath_file"
  popd >/dev/null
  echo "Preparing the runtime classpath for '$service_name' failed with exit code $prepare_exit_code."
  exit "$prepare_exit_code"
fi

dependency_classpath=""
if [[ -f "$classpath_file" ]]; then
  dependency_classpath="$(tr -d '\r' < "$classpath_file")"
fi

runtime_classpath="$module_dir/target/classes"
if [[ -n "$dependency_classpath" ]]; then
  runtime_classpath="${runtime_classpath}:$dependency_classpath"
fi

set +e
java "-Dspring.profiles.active=$profile" -cp "$runtime_classpath" "$main_class"
exit_code=$?
set -e

rm -f "$classpath_file"
popd >/dev/null

if [[ $exit_code -ne 0 ]]; then
  echo "Database connection check for '$service_name' failed with exit code $exit_code."
  exit "$exit_code"
fi
