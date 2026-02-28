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

echo "===== USER SERVICE API TEST ====="
echo "BASE_URL=$BASE_URL"
echo "SUFFIX=$SUFFIX"

CUSTOMER_EMAIL="customer_${SUFFIX}@test.com"
SELLER_EMAIL="seller_${SUFFIX}@test.com"
REJECT_ADMIN_EMAIL="admin_${SUFFIX}@test.com"

code=$(request GET "/api/users/health" "$TMP_DIR/health.out")
assert_code "$code" "200" "health check" "$TMP_DIR/health.out"

code=$(request POST "/api/users" "$TMP_DIR/customer_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Customer ${SUFFIX}\",\"email\":\"$CUSTOMER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"CUSTOMER\"}")
assert_code "$code" "201" "create customer" "$TMP_DIR/customer_create.out"
CUSTOMER_ID="$(jq -r '.id' "$TMP_DIR/customer_create.out")"

code=$(request POST "/api/users" "$TMP_DIR/seller_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Seller ${SUFFIX}\",\"email\":\"$SELLER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"SELLER\"}")
assert_code "$code" "201" "create seller" "$TMP_DIR/seller_create.out"
SELLER_ID="$(jq -r '.id' "$TMP_DIR/seller_create.out")"

code=$(request POST "/api/users" "$TMP_DIR/admin_create.out" \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Admin ${SUFFIX}\",\"email\":\"$REJECT_ADMIN_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"ADMIN\"}")
assert_code "$code" "400" "reject admin self-register" "$TMP_DIR/admin_create.out"

code=$(request POST "/api/users/login" "$TMP_DIR/admin_login.out" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@ems.com","password":"admin123"}')
assert_code "$code" "200" "login seeded admin" "$TMP_DIR/admin_login.out"
ADMIN_TOKEN="$(jq -r '.access_token' "$TMP_DIR/admin_login.out")"
ADMIN_ID="$(jq -r '.user.id' "$TMP_DIR/admin_login.out")"

AUTH=(-H "Authorization: Bearer $ADMIN_TOKEN")

code=$(request GET "/api/users" "$TMP_DIR/users_list_1.out" "${AUTH[@]}")
assert_code "$code" "200" "list users" "$TMP_DIR/users_list_1.out"

code=$(request GET "/api/users/$CUSTOMER_ID" "$TMP_DIR/user_by_id.out" "${AUTH[@]}")
assert_code "$code" "200" "get user by id" "$TMP_DIR/user_by_id.out"

code=$(request GET "/api/users/by-email?email=$CUSTOMER_EMAIL" "$TMP_DIR/user_by_email.out" "${AUTH[@]}")
assert_code "$code" "200" "get user by email" "$TMP_DIR/user_by_email.out"

code=$(request GET "/api/users/email-exists?email=$CUSTOMER_EMAIL" "$TMP_DIR/email_exists.out" "${AUTH[@]}")
assert_code "$code" "200" "check email exists" "$TMP_DIR/email_exists.out"

code=$(request PUT "/api/users/$CUSTOMER_ID" "$TMP_DIR/user_update.out" \
  -H 'Content-Type: application/json' \
  "${AUTH[@]}" \
  -d "{\"name\":\"Customer ${SUFFIX} Updated\"}")
assert_code "$code" "204" "update user" "$TMP_DIR/user_update.out"

code=$(request GET "/api/users/$CUSTOMER_ID/exists" "$TMP_DIR/user_exists.out" "${AUTH[@]}")
assert_code "$code" "200" "internal exists" "$TMP_DIR/user_exists.out"

code=$(request GET "/api/users/$SELLER_ID/role" "$TMP_DIR/user_role.out" "${AUTH[@]}")
assert_code "$code" "200" "internal role" "$TMP_DIR/user_role.out"

code=$(request GET "/api/users/$CUSTOMER_ID/validate" "$TMP_DIR/user_validate.out" "${AUTH[@]}")
assert_code "$code" "200" "internal validate" "$TMP_DIR/user_validate.out"

code=$(request PATCH "/api/users/$CUSTOMER_ID/deactivate" "$TMP_DIR/user_deactivate.out" "${AUTH[@]}")
assert_code "$code" "204" "deactivate user" "$TMP_DIR/user_deactivate.out"

code=$(request PATCH "/api/users/$CUSTOMER_ID/activate" "$TMP_DIR/user_activate.out" "${AUTH[@]}")
assert_code "$code" "204" "activate user" "$TMP_DIR/user_activate.out"

code=$(request GET "/api/users/stats" "$TMP_DIR/user_stats.out" "${AUTH[@]}")
assert_code "$code" "200" "user stats" "$TMP_DIR/user_stats.out"

code=$(request DELETE "/api/users/$CUSTOMER_ID" "$TMP_DIR/user_delete.out" "${AUTH[@]}")
assert_code "$code" "204" "soft delete user" "$TMP_DIR/user_delete.out"

code=$(request GET "/api/users" "$TMP_DIR/users_list_2.out" "${AUTH[@]}")
assert_code "$code" "200" "list users after delete" "$TMP_DIR/users_list_2.out"

echo "----- SUMMARY -----"
echo "CUSTOMER_ID=$CUSTOMER_ID"
echo "SELLER_ID=$SELLER_ID"
echo "ADMIN_ID=$ADMIN_ID"
echo "USER_SERVICE_SMOKE=PASS"
