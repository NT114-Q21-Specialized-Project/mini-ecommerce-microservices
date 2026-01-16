# Mini Ecommerce Microservices

## 1. Tổng quan (Overview)

**Mini Ecommerce Microservices** là một dự án microservices đơn giản nhằm mục đích **học tập và thực hành kiến trúc Microservices cũng như CI/CD pipeline**.

Hệ thống được xây dựng theo hướng cloud-native, tách biệt từng service độc lập, dễ dàng mở rộng và tích hợp vào các nền tảng DevOps/Kubernetes sau này.

Các service chính bao gồm:
- **User Service**
- **Order Service** (sẽ phát triển)
- **API Gateway** (sẽ phát triển)

---

## 2. Kiến trúc Microservices

Kiến trúc hệ thống tuân theo nguyên tắc:
- Mỗi microservice **độc lập về codebase và database**
- Các service **giao tiếp với nhau thông qua HTTP/REST**
- Database **không được chia sẻ giữa các service**

```
Client
  |
  v
API Gateway
  |
  +--> User Service (Go + PostgreSQL)
  |
  +--> Order Service (Spring Boot + PostgreSQL)
```

---

## 3. Chi tiết ứng dụng

### 3.1 User Service

**User Service** được viết hoàn toàn bằng **Go**, chịu trách nhiệm quản lý thông tin người dùng (CRUD User).

**Công nghệ sử dụng:**
- Go 1.22
- PostgreSQL
- Docker & Docker Compose
- RESTful API

---

### 🚀 Chạy User Service ở môi trường local

<details>
<summary><strong>Click để xem hướng dẫn chạy local User Service</strong></summary>

---

### Bước 1: Chạy PostgreSQL database

```bash
docker compose up -d user-db
```

Kiểm tra container đang chạy:

```bash
docker ps
```

---

### Bước 2: Tạo bảng USERS trong database (chỉ cần chạy 1 lần)

Exec vào container PostgreSQL:

```bash
docker exec -it user-db psql -U user -d user_db
```

Trong giao diện `psql`, tạo extension và bảng `users`:

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Thoát khỏi `psql`:

```sql
\q
```

---

### Bước 3: Chạy User Service

```bash
docker compose up --build user-service
```

Nếu log hiển thị:

```
User Service running on :8080
```

👉 Điều này cho thấy **User Service đã kết nối thành công tới database**.

---

### Bước 4: Test nhanh API (mở terminal mới)

#### Health check

```bash
curl http://localhost:8080/health
```

---

#### Tạo user mới

```bash
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tien Phat",
    "email": "tienphat@gmail.com"
  }'
```

---

#### Lấy danh sách user

```bash
curl http://localhost:8080/users
```

Ví dụ kết quả:

```json
[
  {
    "id": "f5caf3b2-832b-4470-917b-eebdf4b34e76",
    "name": "Tien Phat",
    "email": "tienphat@gmail.com",
    "created_at": "2026-01-16T03:13:05.152545Z"
  }
]
```

👉 Nếu các lệnh trên chạy thành công, **User Service đã hoạt động hoàn chỉnh ở môi trường local**.

</details>

---