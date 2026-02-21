#!/usr/bin/env bash
set -e

GATEWAY=http://localhost:9000
CUSTOMER_TOKEN="<PUT_CUSTOMER_BEARER_TOKEN_HERE>"
PRODUCT_ID="<PUT_PRODUCT_ID_HERE>"

echo "===== ORDER SERVICE API TEST ====="

echo "1. Create order (VALID)"
curl -s -X POST \
  "$GATEWAY/api/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: test-create-1" \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": \"$PRODUCT_ID\",
    \"quantity\": 2
  }" | jq
echo

echo "2. List my orders"
curl -s -X GET "$GATEWAY/api/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" | jq
echo

echo "3. Create order - out of stock"
curl -i -X POST \
  "$GATEWAY/api/orders" \
  -H "Authorization: Bearer $CUSTOMER_TOKEN" \
  -H "Idempotency-Key: test-create-2" \
  -H "Content-Type: application/json" \
  -d "{
    \"productId\": \"$PRODUCT_ID\",
    \"quantity\": 9999
  }"
echo

echo "===== ORDER SERVICE TEST DONE ====="
