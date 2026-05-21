#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="$SCRIPT_DIR/../scripts/run-local-service.sh"

exec "$RUNNER" "category-service" "$SCRIPT_DIR" "CATEGORY_MONGODB_URI" "$@"
