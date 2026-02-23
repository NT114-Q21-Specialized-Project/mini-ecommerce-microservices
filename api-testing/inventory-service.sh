#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9000}"
SUFFIX="$(date +%s)"
PASSWORD="123456"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { echo "[FAIL] Missing command: $1"; exit 1; }
}

new_uuid() {
  if [[ -r /proc/sys/kernel/random/uuid ]]; then
    cat /proc/sys/kernel/random/uuid
    return
  fi
  uuidgen
}

request() {
  local method="$1"
  local path="$2"
  local body_file="$3"
  shift 3
  local extra_args=("$@")

  curl -sS -o "$body_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" "${extra_args[@]}"
}

assert_code() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  local body_file="$4"

  if [[ "$actual" != "$expected" ]]; then
    echo "[FAIL] $label (expected=$expected actual=$actual)"
    echo "-- response --"
    cat "$body_file" || true
    exit 1
  fi
  echo "[PASS] $label ($actual)"
}

assert_jq() {
  local jq_expr="$1"
  local label="$2"
  local body_file="$3"

  if ! jq -e "$jq_expr" "$body_file" >/dev/null; then
    echo "[FAIL] $label"
    echo "-- response --"
    cat "$body_file" || true
    exit 1
  fi
  echo "[PASS] $label"
}

require_cmd curl
require_cmd jq

echo "===== INVENTORY SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

SELLER_EMAIL="inventory_seller_${SUFFIX}@test.com"
CUSTOMER_EMAIL="inventory_customer_${SUFFIX}@test.com"
ADMIN_EMAIL="inventory_admin_${SUFFIX}@test.com"
ORDER_ID="$(new_uuid)"
UNKNOWN_PRODUCT_ID="$(new_uuid)"

code=$(request GET "/api/inventory/health" "$TMP_DIR/health.out")
assert_code "$code" "200" "inventory health check" "$TMP_DIR/health.out"

code=$(request POST "/api/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Inventory Seller ${SUFFIX}\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller" "$TMP_DIR/seller_create.out"

code=$(request POST "/api/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Inventory Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"

code=$(request POST "/api/users" "$TMP_DIR/admin_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Inventory Admin ${SUFFIX}\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"ADMIN\"}")
assert_code "$code" "201" "create admin" "$TMP_DIR/admin_create.out"

code=$(request POST "/api/users/login" "$TMP_DIR/seller_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login seller" "$TMP_DIR/seller_login.out"
SELLER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/seller_login.out")"

code=$(request POST "/api/users/login" "$TMP_DIR/customer_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login customer" "$TMP_DIR/customer_login.out"
CUSTOMER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/customer_login.out")"

code=$(request POST "/api/users/login" "$TMP_DIR/admin_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login admin" "$TMP_DIR/admin_login.out"
ADMIN_TOKEN="$(jq -r '.access_token' "$TMP_DIR/admin_login.out")"

code=$(request POST "/api/products" "$TMP_DIR/product_create.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"name\":\"Inventory Product ${SUFFIX}\",\"price\":89.5,\"stock\":9}")
assert_code "$code" "201" "create product" "$TMP_DIR/product_create.out"
PRODUCT_ID="$(jq -r '.id' "$TMP_DIR/product_create.out")"

code=$(request GET "/api/inventory/$PRODUCT_ID" "$TMP_DIR/inventory_get_customer.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "200" "get inventory by product id (customer)" "$TMP_DIR/inventory_get_customer.out"
assert_jq '.productId == "'"$PRODUCT_ID"'"' "inventory response contains product id" "$TMP_DIR/inventory_get_customer.out"

code=$(request POST "/api/inventory/reserve" "$TMP_DIR/inventory_reserve_customer.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: inventory-customer-forbidden-${SUFFIX}" \
  -d "{\"orderId\":\"$ORDER_ID\",\"productId\":\"$PRODUCT_ID\",\"quantity\":1}")
assert_code "$code" "403" "customer cannot reserve inventory" "$TMP_DIR/inventory_reserve_customer.out"

RESERVE_KEY="inventory-reserve-${SUFFIX}"
code=$(request POST "/api/inventory/reserve" "$TMP_DIR/inventory_reserve_admin.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $RESERVE_KEY" \
  -d "{\"orderId\":\"$ORDER_ID\",\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
assert_code "$code" "200" "reserve inventory (admin)" "$TMP_DIR/inventory_reserve_admin.out"
assert_jq '.status == "RESERVED"' "reserve result status is RESERVED" "$TMP_DIR/inventory_reserve_admin.out"

code=$(request POST "/api/inventory/reserve" "$TMP_DIR/inventory_reserve_replay.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $RESERVE_KEY" \
  -d "{\"orderId\":\"$ORDER_ID\",\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
assert_code "$code" "200" "reserve inventory idempotency replay" "$TMP_DIR/inventory_reserve_replay.out"
assert_jq '.idempotentReplay == true' "reserve replay returns idempotentReplay=true" "$TMP_DIR/inventory_reserve_replay.out"

RELEASE_KEY="inventory-release-${SUFFIX}"
code=$(request POST "/api/inventory/release" "$TMP_DIR/inventory_release_admin.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $RELEASE_KEY" \
  -d "{\"orderId\":\"$ORDER_ID\",\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
assert_code "$code" "200" "release inventory (admin)" "$TMP_DIR/inventory_release_admin.out"
assert_jq '.status == "RELEASED"' "release result status is RELEASED" "$TMP_DIR/inventory_release_admin.out"

code=$(request POST "/api/inventory/release" "$TMP_DIR/inventory_release_replay.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $RELEASE_KEY" \
  -d "{\"orderId\":\"$ORDER_ID\",\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
assert_code "$code" "200" "release inventory idempotency replay" "$TMP_DIR/inventory_release_replay.out"
assert_jq '.idempotentReplay == true' "release replay returns idempotentReplay=true" "$TMP_DIR/inventory_release_replay.out"

code=$(request GET "/api/inventory/simulate-cpu?seconds=1" "$TMP_DIR/sim_cpu.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "200" "simulate inventory cpu load" "$TMP_DIR/sim_cpu.out"

code=$(request GET "/api/inventory/simulate-memory?mb=32" "$TMP_DIR/sim_mem.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "200" "simulate inventory memory load" "$TMP_DIR/sim_mem.out"

code=$(request GET "/api/inventory/$UNKNOWN_PRODUCT_ID" "$TMP_DIR/inventory_unknown_product.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "404" "unknown product returns 404" "$TMP_DIR/inventory_unknown_product.out"
assert_jq '.error.code == "INVALID_PRODUCT"' "unknown product error code INVALID_PRODUCT" "$TMP_DIR/inventory_unknown_product.out"

echo "----- SUMMARY -----"
echo "PRODUCT_ID=$PRODUCT_ID"
echo "ORDER_ID=$ORDER_ID"
echo "INVENTORY_SERVICE_SMOKE=PASS"
