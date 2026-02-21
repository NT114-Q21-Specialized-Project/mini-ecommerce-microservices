#!/usr/bin/env bash
set -e

GATEWAY=http://localhost:9000
SELLER_TOKEN="<PUT_SELLER_BEARER_TOKEN_HERE>"

echo "===== PRODUCT SERVICE API TEST ====="

echo "1. Create product (SELLER)"
PRODUCT_ID=$(curl -s -X POST $GATEWAY/api/products \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $SELLER_TOKEN" \
  -d '{
    "name": "Macbook API",
    "price": 3000,
    "stock": 10
  }' | jq -r '.id')

echo "PRODUCT_ID=$PRODUCT_ID"
echo

echo "2. List products"
curl -s $GATEWAY/api/products | jq
echo

echo "3. Get product by ID"
curl -s $GATEWAY/api/products/$PRODUCT_ID | jq
echo

echo "===== PRODUCT SERVICE TEST DONE ====="
