# Mini Ecommerce Microservices

## 1. Tổng quan (Overview)

**Mini Ecommerce Microservices** là một dự án microservices đơn giản nhằm mục đích **học tập và thực hành kiến trúc Microservices cũng như CI/CD pipeline**.

Hệ thống được xây dựng theo hướng cloud-native, tách biệt từng service độc lập, dễ dàng mở rộng và tích hợp vào các nền tảng DevOps/Kubernetes sau này.

Các service chính bao gồm:
- **User Service**
- **Order Service**
- **Product Service**
- **API Gateway**

---

## 2. Kiến trúc Microservices

Kiến trúc hệ thống tuân theo nguyên tắc:
- Mỗi microservice **độc lập về codebase và database**
- Các service **giao tiếp với nhau thông qua HTTP/REST**
- Database **không được chia sẻ giữa các service**

```mermaid
flowchart LR
    %% ===== Client =====
    Client[Client<br/>Browser / Curl / k6]

    %% ===== API Gateway =====
    APIGateway["API Gateway<br/>(Spring Cloud Gateway)<br/>Port: 9000"]

    %% ===== Services =====
    UserService["User Service<br/>Go<br/>Port 8080"]
    ProductService["Product Service<br/>Spring Boot<br/>Port 8082"]
    OrderService["Order Service<br/>Spring Boot<br/>Port 8081"]

    %% ===== Databases =====
    UserDB[(PostgreSQL<br/>user_db)]
    ProductDB[(PostgreSQL<br/>product_db)]
    OrderDB[(PostgreSQL<br/>order_db)]

    %% ===== Client Entry =====
    Client -->|HTTP /api/*| APIGateway

    %% ===== Gateway Routing =====
    APIGateway -->|/api/users| UserService
    APIGateway -->|/api/products| ProductService
    APIGateway -->|/api/orders| OrderService

    %% ===== Service to Service =====
    OrderService -->|validate user| UserService
    OrderService -->|decrease stock| ProductService
    ProductService -->|check user role SELLER| UserService

    %% ===== Database Access =====
    UserService --> UserDB
    ProductService --> ProductDB
    OrderService --> OrderDB

```
## 2.1 Bảng tổng hợp API (API Summary)

Tất cả các request từ **Client** đều được gửi đến **API Gateway** tại cổng **9000**.  
API Gateway chịu trách nhiệm:
- Định tuyến (Routing) request đến service tương ứng
- Loại bỏ tiền tố `/api` trước khi forward vào service nội bộ
- Đóng vai trò **Entry Point duy nhất** của hệ thống

---

### 🔹 User Service  
**Gateway Route:** `/api/users/**`  
**Service nội bộ:** User Service (port **8080**)

#### 🧩 Public APIs (Client / Frontend sử dụng)

| Method | Endpoint (Gateway) | Mô tả |
|------|--------------------|------|
| GET | `/api/users/health` | Health check User Service |
| POST | `/api/users` | Tạo người dùng mới (`CUSTOMER`, `SELLER`) |
| POST | `/api/users/login` | Đăng nhập người dùng (demo auth) |
| GET | `/api/users` | Lấy danh sách user đang active |
| GET | `/api/users/{id}` | Lấy thông tin user theo ID |
| GET | `/api/users/by-email?email=` | Lấy thông tin user theo email |
| GET | `/api/users/email-exists?email=` | Kiểm tra email đã tồn tại |
| PUT | `/api/users/{id}` | Cập nhật thông tin user |
| DELETE | `/api/users/{id}` | Xóa user (soft delete) |
| PATCH | `/api/users/{id}/deactivate` | Vô hiệu hóa user |
| PATCH | `/api/users/{id}/activate` | Kích hoạt lại user |
| GET | `/api/users/stats` | Thống kê user (total, active, inactive, theo role) |

---

#### 🔒 Internal APIs (Service-to-Service ONLY)

| Method | Endpoint | Mô tả |
|------|---------|------|
| GET | `/api/users/{id}/exists` | Kiểm tra user tồn tại & active |
| GET | `/api/users/{id}/role` | Lấy role user |
| GET | `/api/users/{id}/validate` | Validate user (exist, active, role) |

---

#### 🩺 System Endpoints

| Method | Endpoint | Mô tả |
|------|---------|------|
| GET | `/health` | Service up & DB connected |

**Ví dụ gọi API:**
```bash
curl -s http://localhost:9000/api/users | jq
```


**Ví dụ gọi API:**
```bash
curl -s http://localhost:9000/api/users | jq
```

---

### 🔹 Product Service  
**Gateway Route:** `/api/products/**`  
**Service nội bộ:** Product Service (port **8082**)

| Method | Endpoint (Gateway) | Mô tả |
|------|--------------------|------|
| POST | `/api/products` | Tạo sản phẩm mới (Yêu cầu Header `X-User-Id` của SELLER) |
| GET | `/api/products` | Lấy danh sách toàn bộ sản phẩm |
| GET | `/api/products/{id}` | Lấy chi tiết sản phẩm theo ID |
| POST | `/api/products/{id}/decrease-stock?quantity={n}` | Giảm tồn kho sản phẩm theo số lượng |

**Ví dụ gọi API:**
```bash
curl -s http://localhost:9000/api/products | jq
```

---

### 🔹 Order Service  
**Gateway Route:** `/api/orders/**`  
**Service nội bộ:** Order Service (port **8081**)

| Method | Endpoint (Gateway) | Mô tả |
|------|--------------------|------|
| POST | `/api/orders` | Tạo đơn hàng mới (Validate User & trừ kho Product) |

#### Query parameters bắt buộc cho `POST /api/orders`

| Tên tham số | Kiểu dữ liệu | Bắt buộc | Mô tả |
|-----------|-------------|---------|------|
| userId | UUID | ✅ | ID của người mua |
| productId | UUID | ✅ | ID của sản phẩm |
| quantity | Integer | ✅ | Số lượng sản phẩm đặt mua |
| totalAmount | Double | ✅ | Tổng giá trị đơn hàng |

#### Error cases
- User not found
- Product not found
- Not enough stock

**Ví dụ gọi API:**
```bash
curl -X POST "http://localhost:9000/api/orders?userId=<USER_ID>&productId=<PRODUCT_ID>&quantity=2&totalAmount=120.5"
```

---

### 2.3 HTTP Status Codes

| Status | Ý nghĩa |
|------|--------|
| 201 | Tạo resource thành công |
| 400 | Input không hợp lệ |
| 403 | Không đủ quyền |
| 404 | Resource không tồn tại |
| 502 | Service phụ thuộc không khả dụng |

## 3. Chi tiết ứng dụng

### 3.1 User Service

**User Service** được viết hoàn toàn bằng **Go**, chịu trách nhiệm quản lý thông tin người dùng (CRUD User).

**Công nghệ sử dụng:**
- Go 1.22
- PostgreSQL
- Docker & Docker Compose
- RESTful API

---

### 🔐 User Role & Authorization

User Service chịu trách nhiệm **quản lý role người dùng** trong toàn hệ thống, phục vụ cho các service khác (Product / Order) kiểm tra quyền hạn.

#### Các role hiện tại

| Role | Mô tả |
|------|------|
| CUSTOMER | Người mua hàng |
| SELLER | Người bán, được phép tạo sản phẩm |

Role được lưu trực tiếp trong bảng `users` của User Service.

---

### 🚀 Chạy User Service ở môi trường local

<details>
<summary><strong>Click để xem hướng dẫn chạy local User Service</strong></summary>

---

### Bước 1: Chạy User Service

```bash
docker compose up --build user-service
```

Nếu log hiển thị:

```
User Service running on :8080
```

👉 Điều này cho thấy **User Service đã kết nối thành công tới database**.

---

### Bước 2: Test nhanh API (mở terminal mới)

#### 1. Health check & Trạng thái hệ thống

```bash
curl -v -s http://localhost:9000/api/users/health
```

---

#### 2. Quản lý người dùng (CRUD Operations)

##### Tạo user mới (CUSTOMER)

```bash
curl -s -X POST http://localhost:9000/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tien Phat",
    "email": "tienphat@gmail.com",
    "role": "CUSTOMER"
  }' | jq

```
##### Tạo user mới (SELLER)

```bash
curl -s -X POST http://localhost:9000/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Seller One",
    "email": "seller1@gmail.com",
    "role": "SELLER"
  }' | jq

```

##### Cập nhật thông tin User (Partial Update) 

Dùng để thay đổi tên hoặc email của một user hiện có (thay {userId} bằng ID thực tế).

```bash
curl -v -X PUT http://localhost:9000/api/users/{userId} \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Tien Phat Updated",
    "email": "tienphat.new@gmail.com"
  }'

```
##### Xóa user (Soft Delete) 

Chuyển trạng thái `is_active` về  `false`, user sẽ không xuất hiện trong các danh sách công khai.

```bash
curl -v -X DELETE http://localhost:9000/api/users/{userId}
```

---

#### 3. Truy vấn dữ liệu (Query)

##### Lấy danh sách user

```bash
curl -s http://localhost:9000/api/users | jq
```

Ví dụ kết quả:

```json
[
  {
    "id": "edf3ed8d-bfc6-485b-bae3-db00d7fb73c1",
    "name": "Tien Phat",
    "email": "tienphat@gmail.com",
    "role": "CUSTOMER",
    "created_at": "2026-01-17T03:21:16.576701Z"
  },
  {
    "id": "62ca9e4e-8c65-4c7e-8348-535ff5e27b76",
    "name": "Seller One",
    "email": "seller1@gmail.com",
    "role": "SELLER",
    "created_at": "2026-01-17T04:45:35.827152Z"
  }
]

```

##### Lấy chi tiết user theo ID

```bash
curl -s http://localhost:9000/api/users/{userId} | jq
```

##### Kiểm tra User có tồn tại và đang active không

```bash
curl -s http://localhost:9000/api/users/{userId}/exists | jq
```

Kết quả trả về: `{"exists": true}` hoặc `{"exists": false}`

#### 4. API Nội bộ (Internal API – Service to Service)
 
##### Lấy role user

API này chỉ dùng cho các service nội bộ như Product Service hoặc Order Service.

```bash
curl -s http://localhost:9000/api/users/{userId}/role | jq
```

Ví dụ kết quả:

```json
{
  "id": "62ca9e4e-8c65-4c7e-8348-535ff5e27b76",
  "role": "SELLER"
}
```

👉 Nếu các lệnh trên chạy thành công, **User Service đã hoạt động hoàn chỉnh ở môi trường local**.

</details>

---

### 3.2 Order Service

**Order Service** được viết bằng **Spring Boot + JPA**, chịu trách nhiệm quản lý đơn hàng và thực hiện **service-to-service communication** với User Service để xác thực người dùng trước khi tạo đơn.

Order Service **không truy cập trực tiếp database của User Service**, mà xác thực user thông qua HTTP call – đúng nguyên tắc microservices.

**Công nghệ sử dụng:**
- Java 17
- Spring Boot 3
- Spring Data JPA
- PostgreSQL
- Docker & Docker Compose
- RESTful API

---

### 🚀 Chạy Order Service ở môi trường local

<details>
<summary><strong>Click để xem hướng dẫn chạy local Order Service</strong></summary>

---

### Bước 1: Chạy toàn bộ hệ thống (User + Order)

Từ thư mục root của project:

```bash
docker compose up --build
```

Kiểm tra container:

```bash
docker ps
```

Kết quả mong đợi:

```
user-db
user-service
order-db
order-service
```

---

### Bước 2: Kiểm tra User Service (bắt buộc)

Order Service phụ thuộc vào User Service để xác thực user.

```bash
curl http://localhost:8080/users
```

Đảm bảo có ít nhất **1 user tồn tại**.

---

### Bước 3: Tạo order 

##### Tạo order với user hợp lệ

```bash
curl -X POST "http://localhost:8081/orders?userId=<USER_UUID>&totalAmount=120.5"
```

Ví dụ:

```bash
curl -X POST "http://localhost:8081/orders?userId=f5caf3b2-832b-4470-917b-eebdf4b34e76&totalAmount=120.5"
```

Kết quả ví dụ:

```json
{
  "id": "7cf2ff2e-b742-49a6-8214-67762d67b8bc",
  "userId": "f5caf3b2-832b-4470-917b-eebdf4b34e76",
  "totalAmount": 120.5,
  "status": "CREATED",
  "createdAt": "2026-01-16T03:44:42.36490Z"
}
```

---

#### Tạo order với user không tồn tại

```bash
curl -X POST "http://localhost:8081/orders?userId=00000000-0000-0000-0000-000000000000&totalAmount=50"
```

Kết quả:

```
HTTP/1.1 400 Bad Request
User not found
```

#### Tạo order với số lượng vượt quá tồn kho

```bash
curl -X POST "http://localhost:8081/orders?userId=<USER_ID>&productId=<PRODUCT_ID>&quantity=9999&totalAmount=999999"
```

👉 Điều này chứng minh:
- Order Service đã **gọi User Service thành công**
- Business validation hoạt động đúng
- Error handling được xử lý đúng chuẩn API

---

### 🔑 Nguyên tắc thiết kế

- **Database per service**
  - User Service → `user_db`
  - Order Service → `order_db`
- Không sử dụng foreign key giữa các service
- Service-to-service giao tiếp qua HTTP
- Order Service chỉ lưu `userId`, không lưu thông tin user

</details>


### 3.3 Product Service

**Product Service** được viết bằng **Spring Boot + JPA**, chịu trách nhiệm quản lý thông tin sản phẩm.

**Công nghệ sử dụng:**
- Java 17
- Spring Boot 3
- Spring Data JPA
- PostgreSQL
- Docker & Docker Compose

### 🚀 Chạy Product Service ở môi trường local

<details>
<summary><strong>Click để xem hướng dẫn chạy local Product Service</strong></summary>

---

```bash
docker compose up --build product-service
```

#### Tạo product với SELLER (HỢP LỆ)

```bash
curl -s -X POST http://localhost:9000/api/products \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 62ca9e4e-8c65-4c7e-8348-535ff5e27b76" \
  -d '{
    "name": "Macbook Pro",
    "price": 2500,
    "stock": 5
  }' | jq
```

Ví dụ response:

```json
{
  "id": "fa740574-e924-4baf-9058-488706ec95a0",
  "name": "Macbook Pro",
  "price": 2500.0,
  "stock": 5,
  "createdAt": "2026-01-17T09:00:33.217502303Z"
}
```

#### Tạo product với CUSTOMER (BỊ TỪ CHỐI)

```bash
curl -X POST http://localhost:8082/products \
  -H "Content-Type: application/json" \
  -H "X-User-Id: edf3ed8d-bfc6-485b-bae3-db00d7fb73c1" \
  -d '{
    "name": "iPhone 15",
    "price": 1200,
    "stock": 10
  }'
```

Response:
```json
Only SELLER can create product
```

#### Lấy danh sách product

```bash
curl -s http://localhost:9000/api/products | jq
```

Ví dụ Response:

```json
[
  {
    "id": "e01fb1e3-8c0b-4ee8-b531-7273e55cdb60",
    "name": "Macbook Pro",
    "price": 2500.0,
    "stock": 8,
    "createdAt": "2026-01-17T03:21:56.543595Z"
  },
  {
    "id": "e747500d-6719-4819-95a2-6016ee931865",
    "name": "Macbook Pro",
    "price": 2500.0,
    "stock": 3,
    "createdAt": "2026-01-17T05:08:12.580308Z"
  },
  {
    "id": "2496e6fb-1adf-4f74-9e4c-41d67f2a4aa7",
    "name": "Macbook Pro M3",
    "price": 2800.0,
    "stock": 10,
    "createdAt": "2026-01-17T08:42:47.024898Z"
  },
  {
    "id": "fa740574-e924-4baf-9058-488706ec95a0",
    "name": "Macbook Pro",
    "price": 2500.0,
    "stock": 5,
    "createdAt": "2026-01-17T09:00:33.217502Z"
  }
]

```
#### Lấy product theo ID

```bash
curl http://localhost:8082/products/{productId}
```

#### Giảm tồn kho sản phẩm

```bash
curl -s -X POST "http://localhost:9000/api/products/{productId}/decrease-stock?quantity=2"
```

</details>

### 3.4 ORDER ↔ PRODUCT INTEGRATION
#### 📦 Order tạo đơn & tự động trừ tồn kho sản phẩm

Khi tạo đơn hàng thành công, Order Service sẽ gọi sang Product Service để:
- Kiểm tra tồn kho
- Giảm số lượng sản phẩm tương ứng

**Luồng xử lý:**
```bash
Client
  → Order Service
      → Validate User (User Service)
      → Check & Decrease Stock (Product Service)
      → Save Order
```

**Tạo order hợp lệ (Customer mua hàng)**
Điều kiện:
- User tồn tại
- Product tồn tại
- Quantity ≤ stock hiện tại

```bash 
curl -s -X POST "http://localhost:9000/api/orders?userId={userID}&productId={productID}&quantity=2&totalAmount=5000" | jq
```
**Ví dụ response:**
```json
{
  "id": "ef65b13f-9c75-472e-88db-95c777414c52",
  "userId": "edf3ed8d-bfc6-485b-bae3-db00d7fb73c1",
  "productId": "2496e6fb-1adf-4f74-9e4c-41d67f2a4aa7",
  "quantity": 2,
  "totalAmount": 5000.0,
  "status": "CREATED",
  "createdAt": "2026-01-17T09:05:17.580037038Z"
}

```

**🔍 Kiểm tra tồn kho sau khi tạo order**

Sau khi order được tạo thành công, tồn kho của sản phẩm sẽ giảm tương ứng.

```bash
curl http://localhost:8082/products/{productID}
```
**Ví dụ kết quả:**

```json
{
  "id": "2496e6fb-1adf-4f74-9e4c-41d67f2a4aa7",
  "name": "Macbook Pro M3",
  "price": 2800.0,
  "stock": 8,
  "createdAt": "2026-01-17T08:42:47.024898Z"
}

```

#### Các trường hợp lỗi

**Quantity vượt quá tồn kho**
```bash
curl -s -X POST "http://localhost:9000/api/orders?userId=<USER_ID>&productId=<PRODUCT_ID>&quantity=9999&totalAmount=999999"
```

**Response:**

```matheamtica
400 Bad Request
Not enough stock
```

**Product không tồn tại**

```bash
curl -s -X POST "http://localhost:9000/api/orders?userId=<USER_ID>&productId=00000000-0000-0000-0000-000000000000&quantity=1&totalAmount=100"
```

**Response:**

```matheamtica
400 Bad Request
Product not found
```