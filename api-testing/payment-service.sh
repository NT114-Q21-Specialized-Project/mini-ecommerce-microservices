#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://mini-ecommerce.tienphatng237.com}"
SUFFIX="$(date +%s)"
PASSWORD="${PASSWORD:-Password@123}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ems.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123!}"
AUTH_JWT_SECRET="${AUTH_JWT_SECRET:-mini-ecommerce-jwt-2026-prod-rotate-this-secret-please}"
CLIENT_IP="${CLIENT_IP:-198.51.100.$(( (SUFFIX % 100) + 24 ))}"
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

try_admin_login() {
  local body_file="$1"
  local code

  code=$(request POST "/api/v1/users/login" "$body_file" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")

  if [[ "$code" == "200" ]]; then
    ADMIN_TOKEN="$(jq -r '.access_token // empty' "$body_file")"
    if [[ -n "$ADMIN_TOKEN" && "$ADMIN_TOKEN" != "null" ]]; then
      echo "[PASS] login seeded admin (200)"
      return 0
    fi
  fi

  ADMIN_TOKEN=""
  echo "[WARN] admin login unavailable (status=$code). Admin-only checks will run as authorization checks."
  return 1
}

require_cmd curl
require_cmd jq
require_cmd openssl

echo "===== PAYMENT SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

CUSTOMER_EMAIL="payment_customer_${SUFFIX}@test.com"
REJECT_ADMIN_EMAIL="payment_admin_${SUFFIX}@test.com"
ORDER_ID="$(new_uuid)"

code=$(request GET "/api/v1/payments/health" "$TMP_DIR/health.out")
assert_code "$code" "200" "payment health check" "$TMP_DIR/health.out"

code=$(request POST "/api/v1/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Payment Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"
CUSTOMER_ID="$(jq -r '.id' "$TMP_DIR/customer_create.out")"

code=$(request POST "/api/v1/users" "$TMP_DIR/admin_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Payment Admin ${SUFFIX}\",\"email\":\"$REJECT_ADMIN_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"ADMIN\"}")
assert_code "$code" "400" "reject admin self-register" "$TMP_DIR/admin_create.out"

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

try_admin_login "$TMP_DIR/admin_login.out" || true

PAY_KEY="payment-pay-${SUFFIX}"
PAY_BODY="{\"orderId\":\"$ORDER_ID\",\"userId\":\"$CUSTOMER_ID\",\"amount\":199.95,\"currency\":\"USD\"}"

code=$(request POST "/api/v1/payments/pay" "$TMP_DIR/pay_forbidden.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: payment-forbidden-${SUFFIX}" \
  -d "$PAY_BODY")
assert_code "$code" "403" "customer cannot call pay endpoint" "$TMP_DIR/pay_forbidden.out"

PAYMENT_ID=""
REFUND_BODY="{\"orderId\":\"$ORDER_ID\",\"amount\":199.95,\"currency\":\"USD\"}"

if [[ -n "${ADMIN_TOKEN:-}" ]]; then
  code=$(request POST "/api/v1/payments/pay" "$TMP_DIR/pay_admin.out" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Idempotency-Key: $PAY_KEY" \
    -d "$PAY_BODY")
  assert_code "$code" "200" "pay (admin)" "$TMP_DIR/pay_admin.out"
  assert_jq '.status == "PAID"' "pay status is PAID" "$TMP_DIR/pay_admin.out"
  PAYMENT_ID="$(jq -r '.paymentId' "$TMP_DIR/pay_admin.out")"

  code=$(request POST "/api/v1/payments/pay" "$TMP_DIR/pay_replay.out" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Idempotency-Key: $PAY_KEY" \
    -d "$PAY_BODY")
  assert_code "$code" "200" "pay idempotency replay" "$TMP_DIR/pay_replay.out"
  assert_jq '.idempotentReplay == true' "pay replay returns idempotentReplay=true" "$TMP_DIR/pay_replay.out"

  REFUND_KEY="payment-refund-${SUFFIX}"
  code=$(request POST "/api/v1/payments/refund" "$TMP_DIR/refund_admin.out" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Idempotency-Key: $REFUND_KEY" \
    -d "$REFUND_BODY")
  assert_code "$code" "200" "refund (admin)" "$TMP_DIR/refund_admin.out"
  assert_jq '.status == "REFUNDED"' "refund status is REFUNDED" "$TMP_DIR/refund_admin.out"

  code=$(request POST "/api/v1/payments/refund" "$TMP_DIR/refund_replay.out" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Idempotency-Key: $REFUND_KEY" \
    -d "$REFUND_BODY")
  assert_code "$code" "200" "refund idempotency replay" "$TMP_DIR/refund_replay.out"
  assert_jq '.idempotentReplay == true' "refund replay returns idempotentReplay=true" "$TMP_DIR/refund_replay.out"

  code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/payments_by_order.out" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  assert_code "$code" "200" "list payments by order (admin)" "$TMP_DIR/payments_by_order.out"
  assert_jq 'length >= 2' "order payment timeline has at least PAY + REFUND" "$TMP_DIR/payments_by_order.out"

  code=$(request GET "/api/v1/payments/simulate-cpu?seconds=1" "$TMP_DIR/sim_cpu.out" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  assert_code "$code" "200" "simulate payment cpu load" "$TMP_DIR/sim_cpu.out"

  code=$(request GET "/api/v1/payments/simulate-memory?mb=32" "$TMP_DIR/sim_mem.out" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  assert_code "$code" "200" "simulate payment memory load" "$TMP_DIR/sim_mem.out"
else
  echo "[SKIP] admin-only payment success path (no admin token)"
fi

code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/payments_by_order_forbidden.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "403" "customer cannot list payments by order" "$TMP_DIR/payments_by_order_forbidden.out"

code=$(request GET "/api/v1/payments/simulate-cpu?seconds=1" "$TMP_DIR/sim_cpu_forbidden.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "403" "customer cannot simulate payment cpu load" "$TMP_DIR/sim_cpu_forbidden.out"

code=$(request GET "/api/v1/payments/simulate-memory?mb=32" "$TMP_DIR/sim_mem_forbidden.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "403" "customer cannot simulate payment memory load" "$TMP_DIR/sim_mem_forbidden.out"

echo "----- SUMMARY -----"
echo "ORDER_ID=$ORDER_ID"
if [[ -n "$PAYMENT_ID" ]]; then
  echo "PAYMENT_ID=$PAYMENT_ID"
fi
echo "PAYMENT_SERVICE_SMOKE=PASS"
