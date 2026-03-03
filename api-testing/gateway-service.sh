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

request_with_headers() {
  local method="$1"
  local path="$2"
  local body_file="$3"
  local headers_file="$4"
  shift 4
  local extra_args=("$@")

  curl -sS -D "$headers_file" -o "$body_file" -w "%{http_code}" -X "$method" "$BASE_URL$path" "${extra_args[@]}"
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

header_value() {
  local header_name="$1"
  local headers_file="$2"
  awk -F': ' -v key="$header_name" 'tolower($1) == tolower(key) {gsub("\r", "", $2); print $2; exit}' "$headers_file"
}

assert_gateway_error_contract() {
  local body_file="$1"
  local headers_file="$2"
  local expected_code="$3"
  local expected_path="$4"
  local label="$5"

  assert_jq ".code == \"$expected_code\"" "$label: code" "$body_file"
  assert_jq ".message | type == \"string\"" "$label: message string" "$body_file"
  assert_jq ".path == \"$expected_path\"" "$label: path" "$body_file"
  assert_jq '.correlationId | type == "string" and length > 0' "$label: correlationId present" "$body_file"

  local header_cid
  local body_cid
  header_cid="$(header_value "X-Correlation-Id" "$headers_file")"
  body_cid="$(jq -r '.correlationId' "$body_file")"
  if [[ -z "$header_cid" || "$header_cid" != "$body_cid" ]]; then
    echo "[FAIL] $label: correlationId header/body mismatch"
    echo "header=$header_cid body=$body_cid"
    echo "-- response --"
    cat "$body_file" || true
    exit 1
  fi
  echo "[PASS] $label: correlationId header/body match"
}

require_cmd curl
require_cmd jq

echo "===== API GATEWAY API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

SELLER_EMAIL="gateway_seller_${SUFFIX}@test.com"
CUSTOMER_EMAIL="gateway_customer_${SUFFIX}@test.com"
CUSTOMER_NAME="Gateway Customer ${SUFFIX}"
SELLER_NAME="Gateway Seller ${SUFFIX}"
ORDER_ID="$(new_uuid)"

code=$(request GET "/api/v1/users/health" "$TMP_DIR/versioned_health.out")
assert_code "$code" "200" "versioned health check" "$TMP_DIR/versioned_health.out"

code=$(request_with_headers GET "/api/users/health" "$TMP_DIR/unversioned.out" "$TMP_DIR/unversioned.headers")
assert_code "$code" "404" "reject unversioned route" "$TMP_DIR/unversioned.out"
assert_gateway_error_contract "$TMP_DIR/unversioned.out" "$TMP_DIR/unversioned.headers" "API_VERSION_REQUIRED" "/api/users/health" "unversioned route error contract"

REQUEST_CID="gateway-cid-${SUFFIX}"
code=$(request_with_headers GET "/api/v1/users" "$TMP_DIR/missing_auth.out" "$TMP_DIR/missing_auth.headers" \
  -H "X-Correlation-Id: $REQUEST_CID")
assert_code "$code" "401" "missing auth rejected" "$TMP_DIR/missing_auth.out"
assert_gateway_error_contract "$TMP_DIR/missing_auth.out" "$TMP_DIR/missing_auth.headers" "UNAUTHORIZED" "/api/v1/users" "missing auth error contract"
if [[ "$(header_value "X-Correlation-Id" "$TMP_DIR/missing_auth.headers")" != "$REQUEST_CID" ]]; then
  echo "[FAIL] missing auth should preserve caller correlation id"
  exit 1
fi
echo "[PASS] preserve caller correlation id"

code=$(request POST "/api/v1/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$SELLER_NAME\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller user" "$TMP_DIR/seller_create.out"

code=$(request POST "/api/v1/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$CUSTOMER_NAME\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer user" "$TMP_DIR/customer_create.out"
CUSTOMER_ID="$(jq -r '.id' "$TMP_DIR/customer_create.out")"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/seller_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "seller login" "$TMP_DIR/seller_login.out"
SELLER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/seller_login.out")"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/customer_login.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "customer login" "$TMP_DIR/customer_login.out"
CUSTOMER_TOKEN="$(jq -r '.access_token' "$TMP_DIR/customer_login.out")"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/admin_login.out" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@ems.com","password":"admin123"}')
assert_code "$code" "200" "admin login" "$TMP_DIR/admin_login.out"
ADMIN_TOKEN="$(jq -r '.access_token' "$TMP_DIR/admin_login.out")"

code=$(request POST "/api/v1/products" "$TMP_DIR/customer_product_forbidden.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -d "{\"name\":\"Gateway Forbidden Product ${SUFFIX}\",\"price\":10,\"stock\":1}")
assert_code "$code" "403" "customer cannot create product" "$TMP_DIR/customer_product_forbidden.out"
assert_jq '.code == "FORBIDDEN"' "customer forbidden error code" "$TMP_DIR/customer_product_forbidden.out"

code=$(request POST "/api/v1/products" "$TMP_DIR/seller_product_create.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"name\":\"Gateway Seller Product ${SUFFIX}\",\"price\":109.9,\"stock\":5}")
assert_code "$code" "201" "seller can create product" "$TMP_DIR/seller_product_create.out"
PRODUCT_ID="$(jq -r '.id' "$TMP_DIR/seller_product_create.out")"

code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/admin_payment_timeline.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "200" "admin can access payment timeline" "$TMP_DIR/admin_payment_timeline.out"

code=$(request_with_headers GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/customer_payment_forbidden.out" "$TMP_DIR/customer_payment_forbidden.headers" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "403" "customer cannot access payment timeline" "$TMP_DIR/customer_payment_forbidden.out"
assert_gateway_error_contract "$TMP_DIR/customer_payment_forbidden.out" "$TMP_DIR/customer_payment_forbidden.headers" "FORBIDDEN" "/api/v1/payments/order/$ORDER_ID" "customer payment forbidden contract"

ATTACK_IP="198.51.100.10"
for attempt in 1 2 3 4 5; do
  code=$(request POST "/api/v1/users/login" "$TMP_DIR/bruteforce_fail_${attempt}.out" \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: $ATTACK_IP" \
    -d '{"email":"admin@ems.com","password":"wrong-password"}')
  assert_code "$code" "401" "bruteforce failed attempt $attempt" "$TMP_DIR/bruteforce_fail_${attempt}.out"
done

code=$(request_with_headers POST "/api/v1/users/login" "$TMP_DIR/bruteforce_blocked.out" "$TMP_DIR/bruteforce_blocked.headers" \
  -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: $ATTACK_IP" \
  -d '{"email":"admin@ems.com","password":"wrong-password"}')
assert_code "$code" "429" "bruteforce blocked request" "$TMP_DIR/bruteforce_blocked.out"
assert_gateway_error_contract "$TMP_DIR/bruteforce_blocked.out" "$TMP_DIR/bruteforce_blocked.headers" "LOGIN_TEMPORARILY_BLOCKED" "/api/v1/users/login" "bruteforce block error contract"

RATE_IP="198.51.100.11"
for req in $(seq 1 20); do
  code=$(request POST "/api/v1/users/login" "$TMP_DIR/rate_ok_${req}.out" \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: $RATE_IP" \
    -d '{"email":"admin@ems.com","password":"admin123"}')
  assert_code "$code" "200" "rate-limit warmup login $req/20" "$TMP_DIR/rate_ok_${req}.out"
done

code=$(request_with_headers POST "/api/v1/users/login" "$TMP_DIR/rate_limited.out" "$TMP_DIR/rate_limited.headers" \
  -H 'Content-Type: application/json' \
  -H "X-Forwarded-For: $RATE_IP" \
  -d '{"email":"admin@ems.com","password":"admin123"}')
assert_code "$code" "429" "rate limit triggers on 21st login" "$TMP_DIR/rate_limited.out"
assert_gateway_error_contract "$TMP_DIR/rate_limited.out" "$TMP_DIR/rate_limited.headers" "RATE_LIMITED" "/api/v1/users/login" "rate limit error contract"

code=$(request POST "/api/v1/users/login" "$TMP_DIR/normal_login_after_hardening.out" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\"}")
assert_code "$code" "200" "normal login still works after protections" "$TMP_DIR/normal_login_after_hardening.out"

echo "----- SUMMARY -----"
echo "CUSTOMER_ID=$CUSTOMER_ID"
echo "PRODUCT_ID=$PRODUCT_ID"
echo "ORDER_ID=$ORDER_ID"
echo "GATEWAY_SERVICE_SMOKE=PASS"
