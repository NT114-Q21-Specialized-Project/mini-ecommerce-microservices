#!/usr/bin/env bash
set -e

# =========================
# GLOBAL CONFIG
# =========================
GATEWAY=http://localhost:9000

# Generate unique suffix (avoid duplicate data)
SUFFIX=$(date +%s)

CUSTOMER_NAME="Customer API $SUFFIX"
SELLER_NAME="Seller API $SUFFIX"

CUSTOMER_EMAIL="customer_$SUFFIX@test.com"
SELLER_EMAIL="seller_$SUFFIX@test.com"

PASSWORD="123456"

echo "====================================="
echo "===== USER SERVICE API TEST ====="
echo "SUFFIX = $SUFFIX"
echo "====================================="
echo

# =========================
# 1. HEALTH CHECK
# =========================
echo "1️⃣ Health check"
curl -s $GATEWAY/api/users/health
echo
echo

# =========================
# 2. CREATE CUSTOMER
# =========================
echo "2️⃣ Create CUSTOMER user"
CUSTOMER_ID=$(curl -s -X POST $GATEWAY/api/users \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"$CUSTOMER_NAME\",
    \"email\": \"$CUSTOMER_EMAIL\",
    \"password\": \"$PASSWORD\",
    \"role\": \"CUSTOMER\"
  }" | jq -r '.id')

echo "CUSTOMER_ID=$CUSTOMER_ID"
echo

# =========================
# 3. CREATE SELLER
# =========================
echo "3️⃣ Create SELLER user"
SELLER_ID=$(curl -s -X POST $GATEWAY/api/users \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"$SELLER_NAME\",
    \"email\": \"$SELLER_EMAIL\",
    \"password\": \"$PASSWORD\",
    \"role\": \"SELLER\"
  }" | jq -r '.id')

echo "SELLER_ID=$SELLER_ID"
echo

# =========================
# 4. LOGIN (AUTH DEMO)
# =========================
echo "4️⃣ Login CUSTOMER"
CUSTOMER_LOGIN_JSON=$(curl -s -X POST $GATEWAY/api/users/login \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$CUSTOMER_EMAIL\",
    \"password\": \"$PASSWORD\"
  }")

echo "$CUSTOMER_LOGIN_JSON" | jq
CUSTOMER_TOKEN=$(echo "$CUSTOMER_LOGIN_JSON" | jq -r '.access_token')
echo "CUSTOMER_TOKEN=$CUSTOMER_TOKEN"
echo

# =========================
# 5. LIST USERS
# =========================
echo "5️⃣ List all active users"
curl -s $GATEWAY/api/users | jq
echo

# =========================
# 6. GET USER BY ID
# =========================
echo "6️⃣ Get user by ID"
curl -s $GATEWAY/api/users/$CUSTOMER_ID | jq
echo

# =========================
# 7. GET USER BY EMAIL
# =========================
echo "7️⃣ Get user by email"
curl -s "$GATEWAY/api/users/by-email?email=$CUSTOMER_EMAIL" | jq
echo

# =========================
# 8. CHECK EMAIL EXISTS
# =========================
echo "8️⃣ Check email exists"
curl -s "$GATEWAY/api/users/email-exists?email=$CUSTOMER_EMAIL" | jq
echo

# =========================
# 9. UPDATE USER
# =========================
echo "9️⃣ Update user name"
curl -s -X PUT $GATEWAY/api/users/$CUSTOMER_ID \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"${CUSTOMER_NAME} Updated\"
  }"
echo
echo

# =========================
# 10. USER EXISTS (INTERNAL)
# =========================
echo "🔟 Check user exists (internal)"
curl -s $GATEWAY/api/users/$CUSTOMER_ID/exists | jq
echo

# =========================
# 11. GET USER ROLE (INTERNAL)
# =========================
echo "1️⃣1️⃣ Get user role (internal)"
curl -s $GATEWAY/api/users/$SELLER_ID/role | jq
echo

# =========================
# 12. DEACTIVATE USER
# =========================
echo "1️⃣2️⃣ Deactivate user"
curl -s -X PATCH $GATEWAY/api/users/$CUSTOMER_ID/deactivate
echo
echo

# =========================
# 13. CHECK USER AFTER DEACTIVATE
# =========================
echo "1️⃣3️⃣ Get deactivated user (expected to fail)"
curl -s $GATEWAY/api/users/$CUSTOMER_ID || true
echo
echo

# =========================
# 14. ACTIVATE USER
# =========================
echo "1️⃣4️⃣ Activate user again"
curl -s -X PATCH $GATEWAY/api/users/$CUSTOMER_ID/activate
echo
echo

# =========================
# 15. USER STATS
# =========================
echo "1️⃣5️⃣ User statistics"
curl -s $GATEWAY/api/users/stats | jq
echo

# =========================
# 16. DELETE USER (SOFT DELETE)
# =========================
echo "1️⃣6️⃣ Delete user (soft delete)"
curl -s -X DELETE $GATEWAY/api/users/$CUSTOMER_ID
echo
echo

# =========================
# 17. FINAL USER LIST
# =========================
echo "1️⃣7️⃣ List users after delete"
curl -s $GATEWAY/api/users | jq
echo

echo "====================================="
echo "✅ USER SERVICE API TEST DONE"
echo "====================================="
