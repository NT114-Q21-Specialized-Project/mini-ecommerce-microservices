#!/usr/bin/env bash
set -e

GATEWAY=http://localhost:9000
SELLER_ID="<PUT_SELLER_ID_HERE>"

echo "===== PRODUCT SERVICE API TEST ====="

echo "1. Create product (SELLER)"
PRODUCT_ID=$(curl -s -X POST $GATEWAY/api/products \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $SELLER_ID" \
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

echo "4. Decrease stock"
curl -s -X POST "$GATEWAY/api/products/$PRODUCT_ID/decrease-stock?quantity=2" | jq
echo

echo "===== PRODUCT SERVICE TEST DONE ====="
