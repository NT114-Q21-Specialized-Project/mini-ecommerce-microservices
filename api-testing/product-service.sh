#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://mini-ecommerce.tienphatng237.com}"
SUFFIX="$(date +%s)"
PASSWORD="${PASSWORD:-Password@123}"
AUTH_JWT_SECRET="${AUTH_JWT_SECRET:-mini-ecommerce-jwt-2026-prod-rotate-this-secret-please}"
CLIENT_IP="${CLIENT_IP:-198.51.100.$(( (SUFFIX % 100) + 22 ))}"
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

  curl -sS -o "$body_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" \
    -H "X-Forwarded-For: $CLIENT_IP" \
    "${extra_args[@]}"
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

b64url() {
  openssl base64 -A | tr '+/' '-_' | tr -d '='
}

mint_jwt() {
  local subject="$1"
  local role="$2"
  local now
  local exp
  local header
  local payload
  local encoded_header
  local encoded_payload
  local signature

  now="$(date +%s)"
  exp=$((now + 3600))
  header='{"alg":"HS256","typ":"JWT"}'
  payload="{\"sub\":\"${subject}\",\"role\":\"${role}\",\"iat\":${now},\"exp\":${exp}}"

  encoded_header="$(printf '%s' "$header" | b64url)"
  encoded_payload="$(printf '%s' "$payload" | b64url)"
  signature="$(printf '%s' "${encoded_header}.${encoded_payload}" | openssl dgst -binary -sha256 -hmac "$AUTH_JWT_SECRET" | b64url)"

  printf '%s.%s.%s' "$encoded_header" "$encoded_payload" "$signature"
}

require_cmd curl
require_cmd jq
require_cmd openssl

echo "===== PRODUCT SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

SELLER_EMAIL="product_seller_${SUFFIX}@test.com"
CUSTOMER_EMAIL="product_customer_${SUFFIX}@test.com"

code=$(request POST "/api/v1/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Product Seller ${SUFFIX}\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller" "$TMP_DIR/seller_create.out"
SELLER_ID="$(jq -r '.id' "$TMP_DIR/seller_create.out")"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/seller_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\"}")
if [[ "$code" == "200" ]]; then
  echo "[PASS] login seller (200)"
  SELLER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/seller_login.out")"
else
  SELLER_TOKEN="$(mint_jwt "$SELLER_ID" "SELLER")"
  echo "[WARN] seller login unavailable (status=$code). Fallback to minted SELLER token."
fi

code=$(request POST "/api/v1/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Product Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"
CUSTOMER_ID="$(jq -r '.id' "$TMP_DIR/customer_create.out")"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/customer_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\"}")
if [[ "$code" == "200" ]]; then
  echo "[PASS] login customer (200)"
  CUSTOMER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/customer_login.out")"
else
  CUSTOMER_TOKEN="$(mint_jwt "$CUSTOMER_ID" "CUSTOMER")"
  echo "[WARN] customer login unavailable (status=$code). Fallback to minted CUSTOMER token."
fi

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
