#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9000}"
SUFFIX="$(date +%s)"
PASSWORD="${PASSWORD:-Password@123}"
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

code=$(request POST "/api/v1/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Product Seller ${SUFFIX}\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller" "$TMP_DIR/seller_create.out"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/seller_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login seller" "$TMP_DIR/seller_login.out"
SELLER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/seller_login.out")"

code=$(request POST "/api/v1/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Product Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/customer_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "login customer" "$TMP_DIR/customer_login.out"
CUSTOMER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/customer_login.out")"

code=$(request POST "/api/v1/products" "$TMP_DIR/product_create.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"name\":\"Smoke Product ${SUFFIX}\",\"price\":199.9,\"stock\":10}")
assert_code "$code" "201" "create product by seller" "$TMP_DIR/product_create.out"
PRODUCT_ID="$(jq -r '.id' "$TMP_DIR/product_create.out")"

code=$(request POST "/api/v1/products" "$TMP_DIR/product_create_forbidden.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d "{\"name\":\"Forbidden Product ${SUFFIX}\",\"price\":10,\"stock\":1}")
assert_code "$code" "403" "customer cannot create product" "$TMP_DIR/product_create_forbidden.out"

code=$(request GET "/api/v1/products" "$TMP_DIR/product_list.out")
assert_code "$code" "200" "list products" "$TMP_DIR/product_list.out"

if jq -e --arg id "$PRODUCT_ID" 'if type=="array" then any(.[]; .id == $id) else any((.items // [] )[]; .id == $id) end' "$TMP_DIR/product_list.out" >/dev/null; then
  echo "[PASS] list contains created product"
else
  echo "[FAIL] list does not contain created product"
  cat "$TMP_DIR/product_list.out"
  exit 1
fi

code=$(request GET "/api/v1/products/$PRODUCT_ID" "$TMP_DIR/product_get.out")
assert_code "$code" "200" "get product by id" "$TMP_DIR/product_get.out"

code=$(request GET "/api/v1/products?page=0&size=5&sortBy=price&sortDir=desc&name=Smoke" "$TMP_DIR/product_page.out")
assert_code "$code" "200" "list products with pagination/filter/sort" "$TMP_DIR/product_page.out"
if jq -e '.items and (.page == 0) and (.size == 5)' "$TMP_DIR/product_page.out" >/dev/null; then
  echo "[PASS] paginated response shape"
else
  echo "[FAIL] invalid paginated response shape"
  cat "$TMP_DIR/product_page.out"
  exit 1
fi

code=$(request GET "/api/v1/products?sortBy=invalidField" "$TMP_DIR/product_invalid_sort.out")
assert_code "$code" "400" "invalid sortBy returns validation error" "$TMP_DIR/product_invalid_sort.out"
if jq -e '.error.code == "INVALID_SORT_FIELD"' "$TMP_DIR/product_invalid_sort.out" >/dev/null; then
  echo "[PASS] invalid sortBy error code"
else
  echo "[FAIL] invalid sortBy error payload"
  cat "$TMP_DIR/product_invalid_sort.out"
  exit 1
fi

echo "----- SUMMARY -----"
echo "PRODUCT_ID=$PRODUCT_ID"
echo "PRODUCT_SERVICE_SMOKE=PASS"
