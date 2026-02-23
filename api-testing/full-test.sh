#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:9000}"

scripts=(
  "user-service.sh"
  "product-service.sh"
  "inventory-service.sh"
  "payment-service.sh"
  "order-service.sh"
)

echo "===== MINI ECOMMERCE FULL API TEST ====="
echo "BASE_URL=$BASE_URL"

for script in "${scripts[@]}"; do
  echo
  echo ">>> Running $script"
  BASE_URL="$BASE_URL" "./api-testing/$script"
done

echo
echo "FULL_API_TEST=PASS"
