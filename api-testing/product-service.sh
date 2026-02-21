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

require_cmd curl
require_cmd jq

echo "===== PRODUCT SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

SELLER_EMAIL="product_seller_${SUFFIX}@test.com"
CUSTOMER_EMAIL="product_customer_${SUFFIX}@test.com"

code=$(request POST "/api/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Product Seller ${SUFFIX}\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller" "$TMP_DIR/seller_create.out"

code=$(request POST "/api/users/login" "$TMP_DIR/seller_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login seller" "$TMP_DIR/seller_login.out"
SELLER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/seller_login.out")"

code=$(request POST "/api/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Product Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"

code=$(request POST "/api/users/login" "$TMP_DIR/customer_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login customer" "$TMP_DIR/customer_login.out"
CUSTOMER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/customer_login.out")"

code=$(request POST "/api/products" "$TMP_DIR/product_create.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"name\":\"Smoke Product ${SUFFIX}\",\"price\":199.9,\"stock\":10}")
assert_code "$code" "201" "create product by seller" "$TMP_DIR/product_create.out"
PRODUCT_ID="$(jq -r '.id' "$TMP_DIR/product_create.out")"

code=$(request POST "/api/products" "$TMP_DIR/product_create_forbidden.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d "{\"name\":\"Forbidden Product ${SUFFIX}\",\"price\":10,\"stock\":1}")
assert_code "$code" "403" "customer cannot create product" "$TMP_DIR/product_create_forbidden.out"

code=$(request GET "/api/products" "$TMP_DIR/product_list.out")
assert_code "$code" "200" "list products" "$TMP_DIR/product_list.out"

code=$(request GET "/api/products/$PRODUCT_ID" "$TMP_DIR/product_get.out")
assert_code "$code" "200" "get product by id" "$TMP_DIR/product_get.out"

echo "----- SUMMARY -----"
echo "PRODUCT_ID=$PRODUCT_ID"
echo "PRODUCT_SERVICE_SMOKE=PASS"
