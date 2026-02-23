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

echo "===== PAYMENT SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

CUSTOMER_EMAIL="payment_customer_${SUFFIX}@test.com"
ADMIN_EMAIL="payment_admin_${SUFFIX}@test.com"
ORDER_ID="$(new_uuid)"

code=$(request GET "/api/payments/health" "$TMP_DIR/health.out")
assert_code "$code" "200" "payment health check" "$TMP_DIR/health.out"

code=$(request POST "/api/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Payment Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"
CUSTOMER_ID="$(jq -r '.id' "$TMP_DIR/customer_create.out")"

code=$(request POST "/api/users" "$TMP_DIR/admin_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Payment Admin ${SUFFIX}\",\"email\":\"$ADMIN_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"ADMIN\"}")
assert_code "$code" "201" "create admin" "$TMP_DIR/admin_create.out"

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

PAY_KEY="payment-pay-${SUFFIX}"
PAY_BODY="{\"orderId\":\"$ORDER_ID\",\"userId\":\"$CUSTOMER_ID\",\"amount\":199.95,\"currency\":\"USD\"}"

code=$(request POST "/api/payments/pay" "$TMP_DIR/pay_forbidden.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: payment-forbidden-${SUFFIX}" \
  -d "$PAY_BODY")
assert_code "$code" "403" "customer cannot call pay endpoint" "$TMP_DIR/pay_forbidden.out"

code=$(request POST "/api/payments/pay" "$TMP_DIR/pay_admin.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $PAY_KEY" \
  -d "$PAY_BODY")
assert_code "$code" "200" "pay (admin)" "$TMP_DIR/pay_admin.out"
assert_jq '.status == "PAID"' "pay status is PAID" "$TMP_DIR/pay_admin.out"
PAYMENT_ID="$(jq -r '.paymentId' "$TMP_DIR/pay_admin.out")"

code=$(request POST "/api/payments/pay" "$TMP_DIR/pay_replay.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $PAY_KEY" \
  -d "$PAY_BODY")
assert_code "$code" "200" "pay idempotency replay" "$TMP_DIR/pay_replay.out"
assert_jq '.idempotentReplay == true' "pay replay returns idempotentReplay=true" "$TMP_DIR/pay_replay.out"

REFUND_KEY="payment-refund-${SUFFIX}"
REFUND_BODY="{\"orderId\":\"$ORDER_ID\",\"amount\":199.95,\"currency\":\"USD\"}"
code=$(request POST "/api/payments/refund" "$TMP_DIR/refund_admin.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $REFUND_KEY" \
  -d "$REFUND_BODY")
assert_code "$code" "200" "refund (admin)" "$TMP_DIR/refund_admin.out"
assert_jq '.status == "REFUNDED"' "refund status is REFUNDED" "$TMP_DIR/refund_admin.out"

code=$(request POST "/api/payments/refund" "$TMP_DIR/refund_replay.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Idempotency-Key: $REFUND_KEY" \
  -d "$REFUND_BODY")
assert_code "$code" "200" "refund idempotency replay" "$TMP_DIR/refund_replay.out"
assert_jq '.idempotentReplay == true' "refund replay returns idempotentReplay=true" "$TMP_DIR/refund_replay.out"

code=$(request GET "/api/payments/order/$ORDER_ID" "$TMP_DIR/payments_by_order.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "200" "list payments by order (admin)" "$TMP_DIR/payments_by_order.out"
assert_jq 'length >= 2' "order payment timeline has at least PAY + REFUND" "$TMP_DIR/payments_by_order.out"

code=$(request GET "/api/payments/simulate-cpu?seconds=1" "$TMP_DIR/sim_cpu.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "200" "simulate payment cpu load" "$TMP_DIR/sim_cpu.out"

code=$(request GET "/api/payments/simulate-memory?mb=32" "$TMP_DIR/sim_mem.out" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
assert_code "$code" "200" "simulate payment memory load" "$TMP_DIR/sim_mem.out"

echo "----- SUMMARY -----"
echo "ORDER_ID=$ORDER_ID"
echo "PAYMENT_ID=$PAYMENT_ID"
echo "PAYMENT_SERVICE_SMOKE=PASS"
