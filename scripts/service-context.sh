#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/service-context.sh <service>

Prints the minimum file set to read when working on one microservice.
Services: user product inventory payment order gateway frontend
EOF
}

if [[ $# -ne 1 ]]; then
  usage
  exit 1
fi

service="$1"

case "$service" in
  user)
    cat <<'EOF'
user-service/cmd/main.go
user-service/internal/handler/user_handler.go
user-service/internal/service/user_service.go
user-service/internal/repository/user_repository.go
user-service/internal/model/user.go
user-service/migrations/001_create_users_table.sql
api-contracts/user-service.openapi.yaml
EOF
    ;;
  product)
    cat <<'EOF'
product-service/src/main/java/com/example/product/controller/ProductController.java
product-service/src/main/java/com/example/product/service/ProductService.java
product-service/src/main/java/com/example/product/repository/ProductRepository.java
product-service/src/main/java/com/example/product/model/Product.java
api-contracts/product-service.openapi.yaml
EOF
    ;;
  inventory)
    cat <<'EOF'
inventory-service/cmd/main.go
inventory-service/internal/handler/inventory_handler.go
inventory-service/internal/service/inventory_service.go
inventory-service/internal/repository/inventory_repository.go
inventory-service/internal/model/models.go
inventory-service/migrations/001_create_inventory_operations.sql
EOF
    ;;
  payment)
    cat <<'EOF'
payment-service/src/main/java/com/example/payment/controller/PaymentController.java
payment-service/src/main/java/com/example/payment/service/PaymentService.java
payment-service/src/main/java/com/example/payment/controller/GlobalExceptionHandler.java
payment-service/src/main/java/com/example/payment/model/PaymentTransaction.java
payment-service/src/main/java/com/example/payment/repository/PaymentTransactionRepository.java
payment-service/migrations/001_create_payment_transactions.sql
EOF
    ;;
  order)
    cat <<'EOF'
order-service/src/main/java/com/example/order/controller/OrderController.java
order-service/src/main/java/com/example/order/service/OrderService.java
order-service/src/main/java/com/example/order/repository/OrderRepository.java
order-service/src/main/java/com/example/order/repository/SagaStepRepository.java
order-service/src/main/java/com/example/order/repository/OutboxEventRepository.java
order-service/src/main/java/com/example/order/model/Order.java
order-service/src/main/java/com/example/order/model/SagaStep.java
order-service/src/main/java/com/example/order/model/OutboxEvent.java
order-service/src/main/resources/application.yml
api-contracts/order-service.openapi.yaml
EOF
    ;;
  gateway)
    cat <<'EOF'
api-gateway/src/main/java/com/example/gateway/security/JwtAuthenticationFilter.java
api-gateway/src/main/resources/application.yml
EOF
    ;;
  frontend)
    cat <<'EOF'
front-end/src/App.jsx
front-end/src/components
EOF
    ;;
  *)
    echo "Unknown service: $service" >&2
    usage
    exit 1
    ;;
esac
