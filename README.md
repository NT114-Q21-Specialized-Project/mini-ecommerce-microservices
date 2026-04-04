# Mini Ecommerce Microservices

## 1. Overview

**Mini Ecommerce Microservices** is a polyglot distributed system for microservices and DevOps/AIOps practice.

Current runtime topology:

- `api-gateway` (Spring Cloud Gateway)
- `user-service` (Go)
- `product-service` (Spring Boot)
- `inventory-service` (Go)
- `payment-service` (Spring Boot)
- `order-service` (Spring Boot, Saga orchestrator)
- `front-end` (React)
- `redis` (async event backbone)
- `postgres` per service database
- `tempo`, `prometheus`, `grafana`, `loki`, `promtail`

Default local ports:

- API Gateway: `9000`
- Front-end: `5173`
- User/Product/Order/Inventory/Payment services: internal network only (not published to host in compose)
- Redis/Postgres: internal network only (not published to host in compose)
- Grafana: `3000`
- Prometheus: `9090`
- Loki: `3100`
- Tempo: `3200` (UI API), `4317/4318` (OTLP)

## 2. Architecture

```mermaid
flowchart LR
    Client[Client\nBrowser/Curl] -->|HTTP /api/v1/*| Gateway[API Gateway :9000]

    Gateway -->|/api/v1/users| UserService[User Service :8080]
    Gateway -->|/api/v1/products| ProductService[Product Service :8082]
    Gateway -->|/api/v1/inventory| InventoryService[Inventory Service :8083]
    Gateway -->|/api/v1/payments| PaymentService[Payment Service :8084]
    Gateway -->|/api/v1/orders| OrderService[Order Service :8081]
    OrderService -->|Saga Step 1| InventoryService
    OrderService -->|Saga Step 2| PaymentService
    OrderService -->|Compensation| InventoryService
    OrderService -->|Compensation| PaymentService

    UserService --> UserDB[(user_db)]
    ProductService --> ProductDB[(product_db)]
    InventoryService --> InventoryDB[(inventory_db)]
    PaymentService --> PaymentDB[(payment_db)]
    OrderService --> OrderDB[(order_db)]

    OrderService --> Redis[(Redis Pub/Sub)]
    InventoryService --> Redis
    PaymentService --> Redis
```

## 3. Saga Workflow (Order Service)

Order creation uses orchestrated Saga steps:

1. Create order with `CREATED`
2. Reserve inventory (`inventory-service`)
3. Process payment (`payment-service`)
4. Mark order `CONFIRMED` if all steps pass
5. If any step fails, run compensation:
   - release inventory if already reserved
   - refund payment if already paid
   - mark order `FAILED`

Order statuses used in service logic:

- `CREATED`
- `INVENTORY_RESERVED`
- `PAYMENT_PENDING`
- `CONFIRMED`
- `FAILED`
- `CANCELLED`

Saga step history is persisted and can be queried via:

- `GET /api/v1/orders/{id}/saga`

## 4. API Surface (via Gateway)

Base URLs:

```text
Local compose: http://localhost:9000
k0s ingress:   http://mini-ecommerce.tienphatng237.com
```

### 4.1 Gateway/System

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/actuator/health` | Public | Gateway health probe |
| GET | `/actuator/info` | Public | Gateway info |

### 4.2 User Service (`/api/v1/users`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/users/health` | Public | Health check |
| POST | `/api/v1/users` | Public | Register user (`CUSTOMER/SELLER`) |
| POST | `/api/v1/users/login` | Public | Login and get access/refresh token |
| POST | `/api/v1/users/refresh` | Public | Refresh access token |
| POST | `/api/v1/users/logout` | Public | Revoke refresh token |
| GET | `/api/v1/users` | Bearer JWT (`ADMIN`) | List active users |
| GET | `/api/v1/users/{id}` | Bearer JWT (Owner or `ADMIN`) | Get user by ID |
| GET | `/api/v1/users/by-email?email=...` | Bearer JWT (`ADMIN`) | Get user by email |
| GET | `/api/v1/users/email-exists?email=...` | Bearer JWT (`ADMIN`) | Check email existence |
| GET | `/api/v1/users/stats` | Bearer JWT (`ADMIN`) | User statistics |
| PUT | `/api/v1/users/{id}` | Bearer JWT (Owner or `ADMIN`) | Update user |
| PATCH | `/api/v1/users/{id}/activate` | Bearer JWT (`ADMIN`) | Activate user |
| PATCH | `/api/v1/users/{id}/deactivate` | Bearer JWT (`ADMIN`) | Deactivate user |
| DELETE | `/api/v1/users/{id}` | Bearer JWT (Owner or `ADMIN`) | Soft-delete user |
| GET | `/api/v1/users/{id}/exists` | Bearer JWT (`ADMIN`) | Internal exists check |
| GET | `/api/v1/users/{id}/role` | Bearer JWT (`ADMIN`) | Internal role lookup |
| GET | `/api/v1/users/{id}/validate` | Bearer JWT (`ADMIN`) | Internal user validation |
| GET | `/api/v1/users/internal/users/{id}/validate` | Bearer JWT (`ADMIN`) | Internal alias for validation |

### 4.3 Product Service (`/api/v1/products`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/products` | Public | List products (`page,size,sortBy,sortDir,name,minPrice,maxPrice,minStock,maxStock`) |
| GET | `/api/v1/products/{id}` | Public | Get product detail |
| POST | `/api/v1/products` | Bearer JWT (`SELLER/ADMIN`) | Create product |
| PUT | `/api/v1/products/{id}` | Bearer JWT (`SELLER/ADMIN`) | Replace product |
| PATCH | `/api/v1/products/{id}` | Bearer JWT (`SELLER/ADMIN`) | Partially update product |
| DELETE | `/api/v1/products/{id}` | Bearer JWT (`SELLER/ADMIN`) | Delete product |
| POST | `/api/v1/products/{id}/decrease-stock?quantity=n` | Internal service call | Decrease stock |
| POST | `/api/v1/products/{id}/increase-stock?quantity=n` | Internal service call | Increase stock |

### 4.4 Inventory Service (`/api/v1/inventory`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/inventory/health` | Public | Health check |
| GET | `/api/v1/inventory/{productId}` | Bearer JWT (`CUSTOMER/SELLER/ADMIN`) | Check available stock |
| POST | `/api/v1/inventory/reserve` | Bearer JWT (`ADMIN`) | Reserve stock (supports `Idempotency-Key`) |
| POST | `/api/v1/inventory/release` | Bearer JWT (`ADMIN`) | Release reserved stock (compensation) |
| GET | `/api/v1/inventory/simulate-cpu` | Bearer JWT (`ADMIN`) | CPU load simulation |
| GET | `/api/v1/inventory/simulate-memory` | Bearer JWT (`ADMIN`) | Memory load simulation |

### 4.5 Payment Service (`/api/v1/payments`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/payments/health` | Public | Health check |
| POST | `/api/v1/payments/pay` | Bearer JWT (`ADMIN`) | Process payment (supports `Idempotency-Key`) |
| POST | `/api/v1/payments/refund` | Bearer JWT (`ADMIN`) | Refund payment (compensation) |
| GET | `/api/v1/payments/order/{orderId}` | Bearer JWT (`ADMIN`) | Payment timeline by order |
| GET | `/api/v1/payments/simulate-cpu` | Bearer JWT (`ADMIN`) | CPU load simulation |
| GET | `/api/v1/payments/simulate-memory` | Bearer JWT (`ADMIN`) | Memory load simulation |
| GET | `/api/v1/payments/simulate-load` | Bearer JWT (`ADMIN`) | Generic load simulation |

### 4.6 Order Service (`/api/v1/orders`)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/orders` | Bearer JWT (`CUSTOMER/ADMIN`) | Create order via Saga (requires `Idempotency-Key`) |
| GET | `/api/v1/orders` | Bearer JWT (`CUSTOMER/ADMIN`) | List current user orders |
| GET | `/api/v1/orders?userId=<uuid>` | Bearer JWT (`ADMIN`) | Query orders by user |
| GET | `/api/v1/orders/{id}/saga` | Bearer JWT (`CUSTOMER/ADMIN`) | List saga steps for an order |
| PATCH | `/api/v1/orders/{id}/cancel` | Bearer JWT (`CUSTOMER/ADMIN`) | Cancel order + compensation |
| GET | `/api/v1/orders/outbox/pending?limit=20` | Bearer JWT (`ADMIN`) | Pending outbox events |

## 5. Environment Variables

Copy template:

```bash
cp .env.example .env
```

Minimal required values in `.env`:

```env
AUTH_JWT_SECRET=<long-random-secret>
INTERNAL_SERVICE_TOKEN=<long-random-internal-service-token>
USER_DB_PASSWORD=<password>
PRODUCT_DB_PASSWORD=<password>
ORDER_DB_PASSWORD=<password>
INVENTORY_DB_PASSWORD=<password>
PAYMENT_DB_PASSWORD=<password>
GRAFANA_ADMIN_USER=<username>
GRAFANA_ADMIN_PASSWORD=<strong-password>
```

Optional tuning (already in `.env.example`):

- User bootstrap admin (compose has defaults if omitted):
  - `BOOTSTRAP_ADMIN_NAME`
  - `BOOTSTRAP_ADMIN_EMAIL`
  - `BOOTSTRAP_ADMIN_PASSWORD`
- User auth hardening:
  - `JWT_EXPIRES_MINUTES`
  - `JWT_REFRESH_EXPIRES_MINUTES`
  - `AUTH_MAX_FAILED_ATTEMPTS`
  - `AUTH_FAILED_ATTEMPT_WINDOW_SECONDS`
  - `AUTH_LOCKOUT_SECONDS`
  - `BOOTSTRAP_ADMIN_NAME`
- Payment behavior:
  - `PAYMENT_FAILURE_PROBABILITY`
  - `PAYMENT_DELAY_MS`
- Saga retry/circuit breaker:
  - `HTTP_CONNECT_TIMEOUT_MS`
  - `HTTP_READ_TIMEOUT_MS`
  - `SAGA_RETRY_MAX_ATTEMPTS`
  - `SAGA_RETRY_INITIAL_BACKOFF_MS`
  - `SAGA_CB_FAILURE_THRESHOLD`
  - `SAGA_CB_OPEN_DURATION_MS`
- Chaos mode:
  - `CHAOS_MODE`
  - `LATENCY_PROBABILITY`
  - `ERROR_PROBABILITY`
  - `CHAOS_DELAY_MS`

## 6. Run Locally with Docker Compose

Start full stack:

```bash
docker compose up --build -d
```

Start only observability:

```bash
docker compose up -d tempo prometheus grafana loki promtail
```

Check status:

```bash
docker compose ps
```

Stop stack:

```bash
docker compose down
```

Stop and remove volumes:

```bash
docker compose down -v
```

## 7. API Testing

Service-level smoke scripts:

```bash
./api-testing/gateway-service.sh
./api-testing/user-service.sh
./api-testing/product-service.sh
./api-testing/inventory-service.sh
./api-testing/payment-service.sh
./api-testing/order-service.sh
```

Run full API suite:

```bash
./api-testing/full-test.sh
```

If needed, override gateway URL:

```bash
BASE_URL=http://localhost:9000 ./api-testing/full-test.sh
```

## 8. Independent Microservice Development

Start minimal stack for one service:

```bash
./scripts/dev-stack.sh up user
./scripts/dev-stack.sh up product
./scripts/dev-stack.sh up inventory
./scripts/dev-stack.sh up payment
./scripts/dev-stack.sh up order
```

Inspect minimal file scope for one service:

```bash
./scripts/service-context.sh order
```

Detailed guide:

- `docs/microservice-independent-dev.md`

## 9. CI/CD Pipeline (Jenkinsfile)

The Jenkins pipeline is dynamic and matrix-driven:

- Service matrix includes:
  - `api-gateway`
  - `user-service`
  - `product-service`
  - `inventory-service`
  - `payment-service`
  - `order-service`
  - `frontend` (directory `front-end`)
- Detects changed services using `git diff`.
- Rebuilds all services when `Jenkinsfile` changes.
- Runs stages in parallel for selected services:
  - test
  - build image
  - trivy scan
  - push image
  - update image tags in GitOps repo (`kubernetes-hub`)
- Cleans up built images on Jenkins worker after successful run.

## 10. Observability

The stack ships with:

- Traces: Tempo (OTLP)
- Metrics: Prometheus
- Logs: Loki + Promtail
- Dashboards: Grafana (pre-provisioned datasources/dashboards)

Useful URLs:

- Grafana: `http://localhost:3000` (use `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD`)
- Prometheus: `http://localhost:9090`
- Tempo API: `http://localhost:3200`

## 11. Notes

- API contracts are under `api-contracts/`.
- Current OpenAPI contracts are maintained for `api-gateway`, `user-service`, `product-service`, `inventory-service`, `payment-service`, and `order-service`.
- Keep load-testing scripts in a separate repository and consume these OpenAPI contracts as the source of truth.
- Front-end communicates only through `api-gateway`.
- For local browser use, CORS is configured in gateway for `http://localhost:5173`.
