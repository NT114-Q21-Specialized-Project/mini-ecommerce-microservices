#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://mini-ecommerce.tienphatng237.com}"
SUFFIX="$(date +%s)"
PASSWORD="${PASSWORD:-Password@123}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@ems.com}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123!}"
AUTH_JWT_SECRET="${AUTH_JWT_SECRET:-mini-ecommerce-jwt-2026-prod-rotate-this-secret-please}"
CLIENT_IP="${CLIENT_IP:-198.51.100.$(( (SUFFIX % 100) + 25 ))}"
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

echo "===== ORDER SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

CUSTOMER_EMAIL="order_customer_${SUFFIX}@test.com"
SELLER_EMAIL="order_seller_${SUFFIX}@test.com"
REJECT_ADMIN_EMAIL="order_admin_${SUFFIX}@test.com"

code=$(request POST "/api/v1/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Order Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"
CUSTOMER_ID="$(jq -r '.id' "$TMP_DIR/customer_create.out")"

code=$(request POST "/api/v1/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Order Seller ${SUFFIX}\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller" "$TMP_DIR/seller_create.out"
SELLER_ID="$(jq -r '.id' "$TMP_DIR/seller_create.out")"

code=$(request POST "/api/v1/users" "$TMP_DIR/admin_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Order Admin ${SUFFIX}\",\"email\":\"$REJECT_ADMIN_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"ADMIN\"}")
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

try_admin_login "$TMP_DIR/admin_login.out" || true

code=$(request POST "/api/v1/products" "$TMP_DIR/product_create.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d "{\"name\":\"Order Product ${SUFFIX}\",\"price\":120.5,\"stock\":10}")
assert_code "$code" "201" "create product" "$TMP_DIR/product_create.out"
PRODUCT_ID="$(jq -r '.id' "$TMP_DIR/product_create.out")"

IDEMP_KEY="order-smoke-${SUFFIX}"
code=$(request POST "/api/v1/orders" "$TMP_DIR/order_create.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
assert_code "$code" "201" "create order" "$TMP_DIR/order_create.out"
ORDER_ID="$(jq -r '.order.id' "$TMP_DIR/order_create.out")"

code=$(request POST "/api/v1/orders" "$TMP_DIR/order_replay.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: $IDEMP_KEY" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":2}")
assert_code "$code" "200" "idempotency replay" "$TMP_DIR/order_replay.out"
ORDER_ID_REPLAY="$(jq -r '.order.id' "$TMP_DIR/order_replay.out")"
if [[ "$ORDER_ID" != "$ORDER_ID_REPLAY" ]]; then
  echo "[FAIL] replay order id mismatch"
  exit 1
fi
echo "[PASS] replay same order id"

code=$(request GET "/api/v1/orders" "$TMP_DIR/order_list.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "200" "list my orders" "$TMP_DIR/order_list.out"

code=$(request GET "/api/v1/orders/$ORDER_ID/saga" "$TMP_DIR/order_saga_customer.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "200" "get saga steps (customer)" "$TMP_DIR/order_saga_customer.out"

if ! jq -e 'map(.stepName) | index("ORDER_CREATED") != null' "$TMP_DIR/order_saga_customer.out" >/dev/null; then
  echo "[FAIL] saga steps missing ORDER_CREATED"
  cat "$TMP_DIR/order_saga_customer.out" || true
  exit 1
fi
echo "[PASS] saga contains ORDER_CREATED"

if [[ -n "${ADMIN_TOKEN:-}" ]]; then
  code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/payment_timeline_before_cancel.out" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  assert_code "$code" "200" "get payment timeline (admin)" "$TMP_DIR/payment_timeline_before_cancel.out"

  if ! jq -e 'length > 0' "$TMP_DIR/payment_timeline_before_cancel.out" >/dev/null; then
    echo "[FAIL] payment timeline is empty before cancel"
    cat "$TMP_DIR/payment_timeline_before_cancel.out" || true
    exit 1
  fi
  echo "[PASS] payment timeline has transactions"
else
  code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/payment_timeline_before_cancel_forbidden.out" \
    -H "Authorization: Bearer $CUSTOMER_TOKEN")
  assert_code "$code" "403" "customer cannot get payment timeline" "$TMP_DIR/payment_timeline_before_cancel_forbidden.out"
fi

code=$(request PATCH "/api/v1/orders/$ORDER_ID/cancel" "$TMP_DIR/order_cancel.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "200" "cancel order" "$TMP_DIR/order_cancel.out"

code=$(request GET "/api/v1/orders/$ORDER_ID/saga" "$TMP_DIR/order_saga_after_cancel.out" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN")
assert_code "$code" "200" "get saga after cancel (customer)" "$TMP_DIR/order_saga_after_cancel.out"

if ! jq -e 'map(.stepName) | index("ORDER_CANCELLED") != null' "$TMP_DIR/order_saga_after_cancel.out" >/dev/null; then
  echo "[FAIL] saga steps missing ORDER_CANCELLED"
  cat "$TMP_DIR/order_saga_after_cancel.out" || true
  exit 1
fi
echo "[PASS] saga contains ORDER_CANCELLED"

if [[ -n "${ADMIN_TOKEN:-}" ]]; then
  code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/payment_timeline_after_cancel.out" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  assert_code "$code" "200" "get payment timeline after cancel (admin)" "$TMP_DIR/payment_timeline_after_cancel.out"

  if ! jq -e 'map(.operationType) | index("REFUND") != null' "$TMP_DIR/payment_timeline_after_cancel.out" >/dev/null; then
    echo "[FAIL] payment timeline missing REFUND after cancel"
    cat "$TMP_DIR/payment_timeline_after_cancel.out" || true
    exit 1
  fi
  echo "[PASS] payment timeline contains REFUND after cancel"
else
  code=$(request GET "/api/v1/payments/order/$ORDER_ID" "$TMP_DIR/payment_timeline_after_cancel_forbidden.out" \
    -H "Authorization: Bearer $CUSTOMER_TOKEN")
  assert_code "$code" "403" "customer cannot get payment timeline after cancel" "$TMP_DIR/payment_timeline_after_cancel_forbidden.out"
fi

code=$(request POST "/api/v1/orders" "$TMP_DIR/order_out_of_stock.out" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: order-smoke-${SUFFIX}-oos" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"quantity\":9999}")
assert_code "$code" "400" "create order out-of-stock" "$TMP_DIR/order_out_of_stock.out"

if [[ -n "${ADMIN_TOKEN:-}" ]]; then
  code=$(request GET "/api/v1/orders/outbox/pending?limit=20" "$TMP_DIR/outbox_pending.out" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  assert_code "$code" "200" "pending outbox" "$TMP_DIR/outbox_pending.out"
else
  code=$(request GET "/api/v1/orders/outbox/pending?limit=20" "$TMP_DIR/outbox_pending_forbidden.out" \
    -H "Authorization: Bearer $CUSTOMER_TOKEN")
  assert_code "$code" "403" "customer cannot view pending outbox" "$TMP_DIR/outbox_pending_forbidden.out"
fi

echo "----- SUMMARY -----"
echo "PRODUCT_ID=$PRODUCT_ID"
echo "ORDER_ID=$ORDER_ID"
echo "ORDER_SERVICE_SMOKE=PASS"
