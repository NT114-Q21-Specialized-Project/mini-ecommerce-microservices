#!/usr/bin/env bash
set -e

GATEWAY=http://localhost:9000
CUSTOMER_ID="<PUT_CUSTOMER_ID_HERE>"
PRODUCT_ID="<PUT_PRODUCT_ID_HERE>"

echo "===== ORDER SERVICE API TEST ====="

echo "1. Create order (VALID)"
curl -s -X POST \
  "$GATEWAY/api/orders?userId=$CUSTOMER_ID&productId=$PRODUCT_ID&quantity=2&totalAmount=6000" | jq
echo

echo "2. Create order - user not found"
curl -i -X POST \
  "$GATEWAY/api/orders?userId=00000000-0000-0000-0000-000000000000&productId=$PRODUCT_ID&quantity=1&totalAmount=100"
echo

echo "3. Create order - out of stock"
curl -i -X POST \
  "$GATEWAY/api/orders?userId=$CUSTOMER_ID&productId=$PRODUCT_ID&quantity=9999&totalAmount=999999"
echo

echo "===== ORDER SERVICE TEST DONE ====="
