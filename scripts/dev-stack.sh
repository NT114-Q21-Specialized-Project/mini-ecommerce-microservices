#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.dev.yml}"

usage() {
  cat <<'EOF'
Usage:
  ./scripts/dev-stack.sh <action> <target>

Actions:
  up        Start minimal stack for target
  down      Stop minimal stack for target
  ps        Show container status for target
  logs      Stream logs for target

Targets:
  user
  product
  inventory
  payment
  order
  gateway
  full
  observability

Examples:
  ./scripts/dev-stack.sh up user
  ./scripts/dev-stack.sh logs order
  ./scripts/dev-stack.sh down product
EOF
}

if [[ $# -ne 2 ]]; then
  usage
  exit 1
fi

action="$1"
target="$2"

service_set() {
  case "$1" in
    user)
      echo "user-db user-service"
      ;;
    product)
      echo "product-db product-service"
      ;;
    inventory)
      echo "redis product-db product-service inventory-db inventory-service"
      ;;
    payment)
      echo "redis payment-db payment-service"
      ;;
    order)
      echo "redis product-db product-service inventory-db inventory-service payment-db payment-service order-db order-service"
      ;;
    gateway)
      echo "redis user-db user-service product-db product-service inventory-db inventory-service payment-db payment-service order-db order-service api-gateway"
      ;;
    observability)
      echo "tempo loki promtail prometheus grafana"
      ;;
    full)
      echo "user-db user-service product-db product-service inventory-db inventory-service payment-db payment-service order-db order-service redis api-gateway front-end tempo loki promtail prometheus grafana"
      ;;
    *)
      echo "Unknown target: $1" >&2
      exit 1
      ;;
  esac
}

services="$(service_set "$target")"
compose_cmd=(docker compose -f "$COMPOSE_FILE")

# Load .env first so local defaults below do not override repository settings.
if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

# Provide safe local defaults so compose can parse even when .env is partial.
: "${AUTH_JWT_SECRET:=local-dev-jwt-secret-change-me-at-least-32-bytes}"
: "${USER_DB_PASSWORD:=userpass}"
: "${PRODUCT_DB_PASSWORD:=productpass}"
: "${ORDER_DB_PASSWORD:=orderpass}"
: "${INVENTORY_DB_PASSWORD:=inventorypass}"
: "${PAYMENT_DB_PASSWORD:=paymentpass}"
: "${GRAFANA_ADMIN_USER:=admin}"
: "${GRAFANA_ADMIN_PASSWORD:=admin-change-me}"

# jwt parser requires >= 256-bit secret for HS256.
if (( ${#AUTH_JWT_SECRET} < 32 )); then
  echo "[dev-stack] AUTH_JWT_SECRET must be at least 32 characters." >&2
  exit 1
fi

export AUTH_JWT_SECRET
export USER_DB_PASSWORD
export PRODUCT_DB_PASSWORD
export ORDER_DB_PASSWORD
export INVENTORY_DB_PASSWORD
export PAYMENT_DB_PASSWORD
export GRAFANA_ADMIN_USER
export GRAFANA_ADMIN_PASSWORD

echo "[dev-stack] action=$action target=$target compose_file=$COMPOSE_FILE"
echo "[dev-stack] services: $services"

case "$action" in
  up)
    (cd "$ROOT_DIR" && "${compose_cmd[@]}" up -d --build $services)
    ;;
  down)
    (cd "$ROOT_DIR" && "${compose_cmd[@]}" stop $services)
    ;;
  ps)
    (cd "$ROOT_DIR" && "${compose_cmd[@]}" ps $services)
    ;;
  logs)
    (cd "$ROOT_DIR" && "${compose_cmd[@]}" logs -f $services)
    ;;
  *)
    echo "Unknown action: $action" >&2
    usage
    exit 1
    ;;
esac
