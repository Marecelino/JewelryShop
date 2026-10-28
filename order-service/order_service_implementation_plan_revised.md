# Order Service — Revised Implementation Plan

> **Trạng thái:** Plan đã cập nhật sau review; chưa triển khai code theo plan này.
> **Workspace:** `D:\java\JewelryShop\order-service`
> **Package gốc:** `com.example.order`
> **Phạm vi:** Hoàn thiện luồng Order trong kiến trúc microservices hiện tại; chưa triển khai Payment Service.
> **Nguyên tắc:** Giữ cấu trúc hiện tại, Java 21, Spring Boot version đang được dùng trong Order Service, Spring MVC, JPA, Maven, SQL Server; ưu tiên code dễ đọc, RESTful API và kiểm thử có bằng chứng.

---

## 1. Kết quả audit ban đầu và các contract đã biết

### 1.1. Order Service

| Hạng mục | Trạng thái audit ban đầu |
|---|---|
| Spring Boot | 4.1.2 |
| Java | 21 |
| Package | `com.example.order` |
| Source | Skeleton, có `OrderServiceApplication.java` |
| Properties | Chỉ có `spring.application.name=order-service` tại thời điểm audit |
| SQL script trong repository | Không tìm thấy ở thời điểm audit; người dùng đã tạo database/schema thủ công bằng script ở SSMS. Phải kiểm tra live schema trước khi chạy migration. |
| Dependencies | Spring MVC, Data JPA, Validation, SQL Server Driver, OAuth2 Resource Server, Actuator, SpringDoc; cần kiểm tra actual `pom.xml` trước khi sửa |
| Test dependencies | Có starter test và MVC/JPA test dependencies; kiểm tra và bỏ khai báo trùng nếu có, không thêm dependency mới nếu không cần |

### 1.2. Các service hiện có

| Service | Port | Database | Package |
|---|---:|---|---|
| Login Service | 8083 | `JewelryLoginDemo` | `com.example.login` |
| Product Service | 8082 | `JewelryStoreDemo` | `com.example.product` |
| Inventory Service | 8081 | `JewelryInventoryDemo` | `com.example.inventory` |
| API Gateway | 8085 | Không có | `com.example.gateway`, WebFlux |
| Order Service | 8084 (dự kiến) | `JewelryOrderDemo` | `com.example.order` |

Không giả định port 8084 đang trống; kiểm tra trước khi chạy service.

### 1.3. JWT contract từ Login Service

- Issuer: `http://localhost:8083`
- Audience: `jewelryshop-api`
- Algorithm: RS256
- JWT subject (`sub`): user ID, biểu diễn dạng chuỗi
- Claim `roles`: danh sách như `USER`, `ADMIN`
- Local public key path đã thấy trong cấu hình: `file:D:/java/JewelryShop/login-service/keys/public_key.pem`
- Order Service chỉ cần public key để xác thực JWT; không sao chép private key.
- Xác minh lại các giá trị trên với source thực tế trước khi triển khai.

### 1.4. Product Service contract

Endpoint đã xác minh theo audit:

`GET /api/v1/products/{id}`

Các field Order Service cần đọc từ `ProductResponse`:

- `productId` (`Integer`)
- `productName` (`String`)
- `price` (`BigDecimal`)
- `active` (`Boolean`)

Không dựa vào `StockQuantity`: schema `dbo.Products` được cung cấp không có cột đó. Không kết nối trực tiếp tới `JewelryStoreDemo`; chỉ gọi Product Service.

### 1.5. Inventory Reservation contract

**Create reservation**

`POST /internal/api/v1/inventory/reservations`

```json
{
  "orderReference": "ORD-...",
  "orderItemReference": "ITEM-...",
  "productId": 1,
  "ttlMinutes": 15
}
```

Response record theo audit có các field:

- `reservationId`
- `orderReference`
- `orderItemReference`
- `productId`
- `inventoryItemId` (`Long`, non-null theo database schema)
- `status`
- `ttlMinutes`
- `reservedAt`, `expiresAt`, `completedAt`, `createdAt`, `updatedAt`

Reservation states: `ACTIVE`, `COMMITTED`, `RELEASED`, `EXPIRED`.

**Các endpoint khác**

- `GET /internal/api/v1/inventory/reservations/{reservationId}`
- `POST /internal/api/v1/inventory/reservations/{reservationId}/commit`
- `POST /internal/api/v1/inventory/reservations/{reservationId}/release`

Header nội bộ: `X-Internal-Service-Key`, lấy từ cấu hình Inventory Service.

Theo audit, reserve cùng `(orderReference, orderItemReference)` và payload `productId`, `ttlMinutes` khớp sẽ trả lại reservation hiện tại; payload mâu thuẫn trả `409`. Release đã `RELEASED` là idempotent; release reservation `COMMITTED` hoặc `EXPIRED` có thể trả `409`. Phải đọc code/error response thực tế trước khi phân loại lỗi chỉ dựa trên HTTP status.

Không kết nối trực tiếp tới `JewelryInventoryDemo`.

### 1.6. Gateway contract và vấn đề tồn tại

- Gateway dùng Spring Cloud Gateway WebFlux và `@EnableWebFluxSecurity`.
- Claim `roles` được ánh xạ với prefix `ROLE_`.
- `/api/v1/orders/**` chưa có route theo audit.
- `anyExchange().denyAll()` đang được dùng; route và security matcher phải khai báo tường minh.
- Audit ghi nhận route index `[3]` bị khai báo trùng. Kiểm tra file thật và sửa index cho duy nhất, không tự suy luận rằng route sai phải là Inventory Summary.
- Không thêm route công khai cho `/api/v1/inventory-summaries` theo mặc định. Product Service gọi Inventory Summary trực tiếp.
- Giữ nguyên các route đang hoạt động cho Login, Product và Inventory Items.

### 1.7. Error response convention

Ưu tiên dùng cùng cấu trúc error response đang có trong JewelryShop, ví dụ:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/api/v1/...",
  "validationErrors": {
    "field": "message"
  }
}
```

Inspect actual `ApiErrorResponse`/`GlobalExceptionHandler` trước khi tạo format mới. Không tạo nhiều định dạng lỗi không cần thiết.

---

## 2. Nguyên tắc kiến trúc bắt buộc

1. Mỗi service sở hữu database của chính nó. Order Service chỉ truy cập `JewelryOrderDemo`.
2. Không tạo foreign key SQL cross-database sang `users`, `Products`, `inventory_items` hoặc `inventory_reservations`.
3. `userId` lấy từ subject của JWT đã xác thực, không lấy từ request body.
4. Product name và price được lấy qua Product Service rồi lưu snapshot tại thời điểm đặt hàng; client không gửi giá/tổng tiền.
5. Mỗi đơn vị trang sức vật lý là một `order_items` row và một Inventory reservation riêng. Không dùng một quantity duy nhất để thay thế các món vật lý.
6. Không giữ transaction Order DB trong khi thực hiện HTTP call đến Product/Inventory.
7. Mỗi remote side effect phải có business reference ổn định và xử lý timeout theo hướng **unknown outcome**, không giả định request đã thất bại.
8. Không commit Inventory khi chỉ giữ hàng thành công. Vì Payment Service chưa tồn tại, order chỉ dừng ở `AWAITING_PAYMENT`.
9. Không cho client tự sửa status, giá hoặc đánh dấu thanh toán thành công.
10. Không thêm Kafka, Eureka, Config Server hoặc framework Saga trong task này.
11. Giữ code đơn giản, theo conventions hiện tại; không tạo abstraction chỉ để tăng số layer.
12. Không tuyên bố complete chỉ dựa trên `BUILD SUCCESS`; phải tách rõ automated tests, SQL Server integration tests và manual tests.

---

## 3. State machine và quy tắc nghiệp vụ

### 3.1. OrderStatus

Các trạng thái cần dùng trong phạm vi hiện tại:

- `PENDING_RESERVATION`: order và items đã được lưu, workflow giữ hàng còn đang chạy hoặc chưa rõ kết quả.
- `AWAITING_PAYMENT`: tất cả item đều đang được Inventory xác nhận `ACTIVE`/đang giữ.
- `CONFIRMED`: dành cho flow thanh toán thành công trong tương lai; không tự đặt trạng thái này trong task hiện tại.
- `RESERVATION_FAILED`: có lỗi nghiệp vụ xác định khiến không thể giữ đủ hàng; có thể còn reservation phải compensation.
- `CANCELLATION_PENDING`: người dùng yêu cầu hủy, nhưng chưa xác nhận tất cả reservation đã được xử lý.
- `CANCELLED`: việc hủy đã được hoàn tất và các reservation cần giải phóng đã được xử lý.
- `RESERVATION_EXPIRED`: ít nhất một reservation hết hạn trước khi thanh toán; các reservation khác của order có thể vẫn cần compensation.
- `RECONCILIATION_REQUIRED`: không thể kết luận an toàn trạng thái remote sau lỗi không thể retry tự động hoặc có trạng thái mâu thuẫn; cần kiểm tra/khôi phục có kiểm soát.

Không tự động chuyển `RECONCILIATION_REQUIRED` về trạng thái cuối nếu chưa đối soát được Inventory.

### 3.2. OrderItemStatus

- `PENDING_RESERVATION`
- `RESERVED`
- `COMMITTED` (future Payment flow, chưa tự phát sinh trong task này)
- `RELEASED`
- `RESERVATION_FAILED`
- `EXPIRED`
- `RECONCILIATION_REQUIRED`

Quy tắc nhất quán:

- Chỉ set `RESERVED` khi Inventory response xác nhận reservation đang `ACTIVE`.
- Nếu response reserve là `200/201` nhưng trạng thái là `EXPIRED`, không được set item thành `RESERVED`.
- Nếu Inventory báo `COMMITTED` trong khi Order chưa có luồng thanh toán tương ứng, không release item và không giả định order đã thanh toán; đánh dấu reconciliation và ghi nhận cảnh báo.
- Reservation `EXPIRED` không được ghi nhận như `RELEASED`; cần giữ đúng trạng thái thực tế của Inventory.

### 3.3. Quy tắc khi một item hết hạn

Nếu order đang `AWAITING_PAYMENT` và một reservation đã `EXPIRED`:

1. Mark item đó là `EXPIRED`.
2. Order chuyển sang `RESERVATION_EXPIRED`.
3. Release các item khác vẫn được Inventory xác nhận `ACTIVE`.
4. Item release thành công chuyển sang `RELEASED`; item hết hạn vẫn là `EXPIRED`.
5. Nếu một release tạm thời thất bại, giữ trạng thái và retry theo policy.
6. Nếu Inventory trả `409`, gọi GET reservation để phân biệt `EXPIRED`, `COMMITTED` và tình huống khác; không retry cùng một thao tác mù quáng.
7. Nếu phát hiện trạng thái remote không thể xử lý an toàn tự động, chuyển order/item liên quan sang `RECONCILIATION_REQUIRED` và ngừng retry vô hạn.

Order ở `RESERVATION_EXPIRED` vẫn phải được recovery scheduler xem xét khi còn item ở trạng thái `RESERVED` cần compensation.

---

## 4. Phase 0 — Audit trước khi code

Đọc các file thực tế trước khi chỉnh sửa:

1. `order-service/pom.xml`
2. `order-service/src/main/resources/application.properties`
3. Tất cả source/test hiện có của Order Service
4. `Script_JewelryOrderDB.sql` nếu đã được lưu trong workspace
5. `Script_JewelryOrderDB_Workflow.sql` nếu đã tồn tại
6. Product Controller, `ProductResponse` và error response
7. Inventory Reservation DTO, Service, Controller, Security Filter và exception response
8. Login JWT generation/claims và key config
9. Gateway `application.properties` và `SecurityConfig.java`
10. Các SQL script/schema hiện có của `JewelryOrderDemo`
11. `pom.xml` và conventions của Product/Inventory/Login

Trước khi chạy migration:

- Kiểm tra database hiện có và `dbo.orders`/`dbo.order_items` có tồn tại không.
- Đọc metadata và constraint thực tế; không tin chỉ vào trạng thái file trong repository.
- Database có thể đã được tạo trực tiếp qua SSMS kể cả khi script chưa được lưu vào repo.
- Không drop/recreate bảng, không truncate, không reset `IDENTITY` và không xóa dữ liệu hiện có.
- Ghi lại row counts ban đầu để xác minh migration không thay đổi dữ liệu nghiệp vụ.

Chạy baseline build/test. Ghi chính xác version, test counts, lỗi và các test bị block.

---

## 5. Phase 1 — Database scripts và migration an toàn

Cần duy trì hai script với mục đích khác nhau:

1. `Script_JewelryOrderDB.sql`: baseline script dùng cho database mới/fresh setup.
2. `Script_JewelryOrderDB_Workflow.sql`: additive migration cho database đã tồn tại.

Nếu một script đã tồn tại ở local/repository, hãy inspect và sửa nó; không tạo bản trùng lặp không cần thiết.

### 5.1. `dbo.orders`

Các cột cốt lõi:

| Column | Type/constraints |
|---|---|
| `order_id` | `BIGINT IDENTITY(1,1) PRIMARY KEY` |
| `order_reference` | `VARCHAR(100) NOT NULL`, unique |
| `user_id` | `BIGINT NOT NULL`, check > 0 |
| `status` | `VARCHAR(30) NOT NULL`, check status hợp lệ |
| `total_amount` | `DECIMAL(18,2) NOT NULL`, check >= 0 |
| `idempotency_key` | `VARCHAR(100) NULL` |
| `created_at` | `DATETIME2(3) NOT NULL` |
| `updated_at` | `DATETIME2(3) NOT NULL` |

Order statuses trong constraint phải bao gồm:

- `PENDING_RESERVATION`
- `AWAITING_PAYMENT`
- `CONFIRMED`
- `CANCELLED`
- `RESERVATION_FAILED`
- `CANCELLATION_PENDING`
- `RESERVATION_EXPIRED`
- `RECONCILIATION_REQUIRED`

Constraints/indexes:

- `PK_orders`
- `UQ_orders_order_reference`
- `CK_orders_user_id`
- `CK_orders_total_amount`
- `CK_orders_status`
- Unique filtered index `UX_orders_user_idempotency_key` trên `(user_id, idempotency_key)` với filter `idempotency_key IS NOT NULL`
- Index phù hợp cho `(user_id, created_at)` và status/recovery query

Unique `(user_id, idempotency_key)` là source of truth cuối cùng chống việc hai request đồng thời cùng tạo hai order. Application pre-check không thay thế unique index.

### 5.2. `dbo.order_items`

| Column | Type/constraints |
|---|---|
| `order_item_id` | `BIGINT IDENTITY(1,1) PRIMARY KEY` |
| `order_id` | `BIGINT NOT NULL`, FK `dbo.orders(order_id)` |
| `order_item_reference` | `VARCHAR(100) NOT NULL` |
| `product_id` | `INT NOT NULL`, check > 0 |
| `product_name_snapshot` | `NVARCHAR(100) NOT NULL` |
| `unit_price` | `DECIMAL(18,2) NOT NULL`, check >= 0 |
| `reservation_id` | `BIGINT NULL`, ID tham chiếu Inventory, không phải cross-database FK |
| `inventory_item_id` | `BIGINT NULL`, ID tham chiếu Inventory, không phải cross-database FK |
| `status` | `VARCHAR(30) NOT NULL`, check status hợp lệ |
| `retry_count` | `INT NOT NULL`, default 0, check >= 0 |
| `next_retry_at` | `DATETIME2(3) NULL` |
| `last_error_code` | `VARCHAR(50) NULL` |
| `created_at` | `DATETIME2(3) NOT NULL` |
| `updated_at` | `DATETIME2(3) NOT NULL` |

Item statuses trong constraint phải bao gồm:

- `PENDING_RESERVATION`
- `RESERVED`
- `COMMITTED`
- `RELEASED`
- `RESERVATION_FAILED`
- `EXPIRED`
- `RECONCILIATION_REQUIRED`

Constraints/indexes:

- `PK_order_items`
- `FK_order_items_orders`
- Unique `(order_id, order_item_reference)`
- `CK_order_items_product_id`
- `CK_order_items_unit_price`
- `CK_order_items_retry_count`
- `CK_order_items_status`
- Check constraint đảm bảo `reservation_id` và `inventory_item_id` hoặc cùng NULL hoặc cùng non-NULL.
- Index cho `order_id`, các trạng thái cần recovery và `next_retry_at`.
- Filtered unique index trên `reservation_id` khi non-NULL chỉ giữ nếu định nghĩa nghiệp vụ xác nhận một reservation chỉ thuộc một order item; kiểm tra actual schema trước khi tạo.

### 5.3. Additive migration

`Script_JewelryOrderDB_Workflow.sql` phải:

1. Kiểm tra database/schema đang nhắm đến.
2. Thêm `idempotency_key` nếu thiếu.
3. Tạo filtered unique index nếu chưa tồn tại; nếu index cùng tên nhưng sai định nghĩa, báo lỗi rõ ràng.
4. Thêm `retry_count`, `next_retry_at`, `last_error_code` nếu thiếu.
5. Cập nhật check constraint cho trạng thái mới `RESERVATION_EXPIRED`, `RECONCILIATION_REQUIRED`, `EXPIRED` nếu chưa được hỗ trợ.
6. Kiểm tra unique/check/FK/index definitions, không chỉ kiểm tra tên.
7. Dùng transaction và `TRY/CATCH`; trong catch rollback nếu transaction còn mở và dùng `;THROW;` đúng cú pháp SQL Server.
8. Có verification queries với pass/fail rõ ràng.
9. Chạy được nhiều lần không tạo duplicate object.
10. Không xóa hoặc thay đổi row cũ ngoài các schema changes được mô tả.

Nếu constraint hiện tại có tên hoặc định nghĩa khác, inspect trước khi alter; không đoán tên constraint và không bỏ qua mismatch.

Không tự động chạy migration nếu chưa xác minh đúng `JewelryOrderDemo`. Báo rõ script được tạo, được chạy hay chỉ được cung cấp; không nhập nhằng ba trạng thái này.

---

## 6. Phase 2 — Configuration và application skeleton

Cập nhật `application.properties` theo conventions hiện tại.

Properties cần có:

```properties
spring.application.name=order-service
server.port=8084

spring.datasource.url=${DB_URL:jdbc:sqlserver://Marecelino:1433;databaseName=JewelryOrderDemo;encrypt=true;trustServerCertificate=true}
spring.datasource.username=${DB_USER:sa}
spring.datasource.password=${DB_PASSWORD:123456}
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

app.jwt.issuer=${JWT_ISSUER:http://localhost:8083}
app.jwt.audience=${JWT_AUDIENCE:jewelryshop-api}
app.jwt.public-key-location=${JWT_PUBLIC_KEY_LOCATION:file:D:/java/JewelryShop/login-service/keys/public_key.pem}

product.service.url=${PRODUCT_SERVICE_URL:http://localhost:8082}
inventory.service.url=${INVENTORY_SERVICE_URL:http://localhost:8081}
inventory.internal.api-key=${INVENTORY_INTERNAL_API_KEY:}

app.client.connect-timeout-ms=2000
app.client.read-timeout-ms=5000
app.order.reservation-ttl-minutes=15
app.order.max-items-per-order=20
app.order.recovery-scan-delay-ms=60000
app.order.recovery-batch-size=50
app.order.max-retry-attempts=5
app.order.retry-base-delay-ms=5000
app.order.retry-max-delay-ms=300000
```

Giữ lại các cấu hình local phù hợp với môi trường test hiện tại; không tự ý thay đổi quy trình local credentials trong task này.

Các yêu cầu khác:

- `ddl-auto=none`; không để Hibernate tạo/sửa schema tự động.
- Chỉ đọc public key để validate JWT.
- Timeouts phải cấu hình rõ ràng cho các client.
- Kiểm tra duplicate test dependencies. Nếu `spring-boot-starter-webmvc-test` xuất hiện hai lần, giữ một khai báo phù hợp.
- Dùng test annotations có sẵn trong Spring Boot 4.1.x; xác minh `@MockitoBean`/mocking API thực tế trước khi viết test, không sao chép annotation cũ một cách máy móc.
- Thêm OpenAPI config chỉ nếu cần và theo conventions/phiên bản SpringDoc hiện tại.
- Biên dịch project trước khi tiếp tục.

---

## 7. Phase 3 — Entity, enums, repository

### 7.1. Expected files

- `entity/Order.java`
- `entity/OrderItem.java`
- `enums/OrderStatus.java`
- `enums/OrderItemStatus.java`
- `repository/OrderRepository.java`
- `repository/OrderItemRepository.java`

Không tạo JPA relationship sang database Login/Product/Inventory.

### 7.2. Entity mapping

`Order`:

- `@Table(name = "orders", schema = "dbo")`
- Map chính xác `order_reference`, `user_id`, `status`, `total_amount`, `idempotency_key`, timestamps.
- `@Enumerated(EnumType.STRING)`.
- `Instant` với `DATETIME2(3)` và timestamps được cập nhật rõ ràng.
- `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)` nếu phù hợp với lifecycle; không serialize entity trực tiếp.

`OrderItem`:

- `@Table(name = "order_items", schema = "dbo")`
- `@ManyToOne(fetch = FetchType.LAZY)` với `@JoinColumn(name = "order_id")`.
- Map snapshot name/price, reservation/item IDs, status và retry metadata.
- `reservationId`/`inventoryItemId` là tham chiếu số, không có cross-database relation.

Giữ quan hệ Order/OrderItem đơn giản. Không tạo entity audit/event riêng nếu chưa có yêu cầu thực tế.

### 7.3. Repository capabilities

`OrderRepository` cần hỗ trợ:

- `findByOrderReference(String)`
- `findByUserIdAndIdempotencyKey(Long, String)`
- `findByUserId(Long, Pageable)`
- `findAll(Pageable)` cho admin scope
- Query lấy recovery candidates theo status, `next_retry_at` và batch giới hạn

`OrderItemRepository` cần hỗ trợ:

- Lấy items theo order
- Lấy items theo order và status
- Lấy các item ở `PENDING_RESERVATION` đến hạn retry
- Lấy các item `RESERVED` cần compensation khi order ở `RESERVATION_FAILED`, `CANCELLATION_PENDING` hoặc `RESERVATION_EXPIRED`

Không load toàn bộ order history hoặc unbounded recovery records.

---

## 8. Phase 4 — Product và Inventory RestClient

### 8.1. Expected files

- `client/ProductClient.java`
- `client/InventoryReservationClient.java`
- `config/RestClientConfig.java`
- `dto/external/ProductDetailDto.java`
- `dto/external/InventoryReservationDto.java`

Reuse current client conventions from Product/Inventory. Không thêm WebClient/WebFlux vào Order Service.

### 8.2. ProductClient

- Gọi `GET /api/v1/products/{id}`.
- Deserialize theo response thực tế đã kiểm tra.
- Product không tồn tại: map thành lỗi 404 phù hợp với public Order API.
- Product tồn tại nhưng `active=false`: map thành conflict nghiệp vụ, ví dụ `409`.
- Timeout/connection failure/5xx: `503` nếu chưa tạo order, hoặc xử lý workflow pending nếu đã persist order (theo đúng điểm xảy ra).
- Không nhầm downstream outage với product not found.
- Có connect/read timeout.

### 8.3. InventoryReservationClient

- Header `X-Internal-Service-Key` lấy từ property.
- Gửi đúng `orderReference`, `orderItemReference`, `productId`, `ttlMinutes`.
- Deserialize response theo DTO thật; kiểm tra mọi status sau response.
- `reserve` chỉ báo success nghiệp vụ nếu response reservation đang `ACTIVE`.
- Nếu response `200/201` cho reservation đã `EXPIRED`, `RELEASED` hoặc trạng thái khác không phù hợp, không chuyển local item thành `RESERVED`; đưa workflow sang nhánh reconcile/compensation.
- `release` trả `200`: kiểm tra response và lưu local state phù hợp.
- `release` trả `409`: gọi GET reservation; nếu state là `EXPIRED`, ghi item `EXPIRED` (không ghi `RELEASED`); nếu `COMMITTED`, đánh dấu `RECONCILIATION_REQUIRED`; nếu state khác, xử lý theo actual contract.
- `404` cho reservation hoặc `401/403` không được nhầm thành “đã release”. Xem đây là inconsistency hoặc configuration/authentication issue và không đánh dấu release thành công.
- Timeout/connection error: trạng thái remote là unknown; không giả định thao tác không xảy ra.
- Chỉ retry bằng cùng business references để dùng Inventory idempotency.
- Log request references và error codes cần thiết nhưng không log internal key.

Phải phân loại 409 dựa trên actual error response/contract, không map mọi 409 thành “out-of-stock” một cách mù quáng. Nếu contract không cho phép phân biệt lỗi nghiệp vụ quan trọng, báo blocker cụ thể và đề xuất thay đổi tối thiểu có kiểm soát thay vì âm thầm đoán.

### 8.4. RestClientConfig

- Bean riêng cho Product và Inventory nếu cần base URL/header khác nhau.
- Timeouts được bind từ properties.
- Dùng HTTP request factory tương thích với framework hiện tại.
- Không giữ transaction Order DB trong các lời gọi client.

---

## 9. Phase 5 — Public REST API

### 9.1. Create order

`POST /api/v1/orders`

Header bắt buộc: `Idempotency-Key`.

Request:

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

DTO validation:

- `items`: `@NotEmpty`, kích thước không vượt giới hạn cấu hình.
- `productId`: `@NotNull`, `@Positive`.
- `quantity`: `@NotNull`, `@Min(1)`, `@Max(10)`.
- Validate tổng số physical units không vượt `app.order.max-items-per-order`.
- Validate `Idempotency-Key` không blank và không vượt 100 ký tự.
- Không nhận `userId`, `orderReference`, `unitPrice`, `totalAmount`, `status`, reservation ID từ client.

HTTP behavior:

- Order mới, tất cả reservations đã ACTIVE: `201 Created` + `Location`.
- Request trùng cùng key/payload, workflow đã có: trả representation hiện có `200 OK`; nếu workflow đang pending, `202 Accepted` là hợp lệ và phải được mô tả nhất quán.
- Cùng key nhưng payload khác: `409 Conflict`.
- Request không hợp lệ: `400 Bad Request`.
- Product không tồn tại: `404 Not Found`.
- Product inactive hoặc hết hàng xác định: `409 Conflict`.
- Downstream outage trước khi Order được persist: `503 Service Unavailable`, không tạo Order dở dang.
- Downstream timeout sau khi Order đã persist: `202 Accepted` với `Location`, giữ status để recovery; không tạo Order thứ hai.

### 9.2. Get order

`GET /api/v1/orders/{orderReference}`

- `200 OK` khi order tồn tại và caller có quyền.
- `404 Not Found` nếu không tồn tại.
- USER chỉ xem order thuộc `userId` từ JWT.
- ADMIN có thể xem order của user khác theo policy đã định nghĩa.
- Không leak dữ liệu của người khác khi từ chối quyền. Chọn `403` hoặc `404` theo conventions của project và giữ nhất quán.

### 9.3. List orders

`GET /api/v1/orders?page=0&size=10&sort=createdAt,desc`

- Pagination bắt buộc có giới hạn kích thước.
- USER chỉ thấy order của mình; ADMIN được list mọi order.
- Chỉ cho phép whitelist sort fields như `createdAt`, `updatedAt`, `orderReference`; không chuyển bất kỳ chuỗi sort từ client thành SQL tùy tiện.
- Thêm stable tie-breaker như `orderId` để thứ tự trang ổn định khi nhiều record cùng timestamp.
- Giữ response pagination conventions hiện có nếu tương thích.

### 9.4. Cancellation

`POST /api/v1/orders/{orderReference}/cancellation`

- Chỉ owner hoặc ADMIN có quyền yêu cầu hủy.
- `CANCELLED`: trả representation đã hủy, không lặp side effects.
- `CONFIRMED`: `409`; payment flow ngoài scope.
- `PENDING_RESERVATION`: không đua với workflow tạo order; trả `409` có thông điệp rõ hoặc reconcile/resume workflow theo policy.
- `AWAITING_PAYMENT`: chuyển `CANCELLATION_PENDING`, sau đó release active reservations.
- `CANCELLATION_PENDING`: resume release idempotently.
- `RESERVATION_FAILED`: tiếp tục compensation nếu còn item `RESERVED`; chỉ đánh dấu `CANCELLED` khi không còn reservation cần xử lý và semantics cho request cancel phù hợp.
- Nếu reservation đã `EXPIRED`, đánh dấu item `EXPIRED`; vì Inventory đã trả item về AVAILABLE, không coi `409` đó là lỗi release còn chờ.
- Nếu một reservation thực tế `COMMITTED` nhưng Order chưa có trạng thái thanh toán tương ứng, chuyển `RECONCILIATION_REQUIRED` và không đánh dấu hủy thành công.
- Tất cả release thành công: `200 OK`, order thành `CANCELLED`.
- Release tạm thời thất bại: `202 Accepted`, order vẫn `CANCELLATION_PENDING`.

### 9.5. HTTP error mapping

Dùng actual error response convention. Mapping tối thiểu:

- Validation error → `400`
- Invalid/missing JWT → `401`
- Role/ownership forbidden → `403` hoặc `404` theo policy nhất quán
- Order not found → `404`
- Product not found → `404`
- Product inactive/out-of-stock/invalid state → `409`
- Idempotency key/payload mismatch → `409`
- Irrecoverable state conflict → `409` hoặc `RECONCILIATION_REQUIRED` workflow theo kết quả đã persist
- Downstream unavailable trước khi persist → `503`
- Workflow persisted nhưng đang chờ retry/compensation → `202`

Không map mọi downstream error thành `409` hoặc `503` bất kể ngữ cảnh.

---

## 10. Phase 6 — Order workflow, idempotency và compensation

### 10.1. Chuẩn hóa payload để so sánh Idempotency-Key

Định nghĩa normalized payload trước khi so sánh:

- Aggregate quantity theo `productId` thành map `productId -> total quantity`.
- Không phụ thuộc thứ tự items trong request.
- Hai dòng giống `productId` được gộp để so sánh logical payload.
- Nếu tổng quantity theo từng product khác nhau thì payload khác và trả `409`.

Khi request có key đã tồn tại, so sánh với items đã persist. Không dựa vào giá hiện tại của Product Service để so payload vì giá có thể thay đổi sau khi Order được tạo.

### 10.2. Concurrent Idempotency-Key requests

Hai request cùng `(userId, Idempotency-Key)` có thể cùng vượt qua pre-check. Vì vậy:

1. Pre-check giúp fast path nhưng không bảo đảm uniqueness.
2. Persist order qua một transaction ngắn, có unique filtered index làm bảo vệ cuối cùng.
3. Request thua nhận unique constraint violation phải rollback transaction tạo mới hoàn toàn.
4. Sau rollback, đọc lại order đã thắng qua transaction/persistence context mới.
5. Payload khớp: trả/resume cùng order; payload khác: `409`.
6. Không truy vấn tiếp bằng transaction đã bị đánh dấu rollback-only.
7. Không gọi Product/Inventory trong transaction tạo Order/OrderItem.
8. Viết SQL Server concurrency integration test: N request đồng thời dùng cùng user/key tạo đúng một Order và không phát sinh reservation dư.

Đảm bảo các lần retry dùng đúng persisted `orderReference` và `orderItemReference`; không generate reference mới cho một idempotency key đã tồn tại.

### 10.3. `OrderService.createOrder()` — workflow

1. Lấy `userId` từ authenticated JWT subject và parse sang `Long`.
2. Đọc/validate `Idempotency-Key`.
3. Tìm order theo `(userId, idempotencyKey)`; nếu có, so normalized payload rồi return hoặc resume.
4. Trước khi persist order mới, gọi Product Service cho các `productId` unique và xác minh product tồn tại/active. Nếu Product Service không khả dụng ở giai đoạn này, trả `503`, chưa tạo order.
5. Expand quantities thành từng physical order item.
6. Snapshot product name và unit price từ response Product Service.
7. Tính `total_amount` bằng `BigDecimal` từ các item snapshots.
8. Generate unique `orderReference`; generate stable, unique `orderItemReference` cho từng unit.
9. Transaction ngắn: lưu Order và tất cả OrderItems ở `PENDING_RESERVATION`; commit trước khi gọi Inventory.
10. Gọi Inventory tuần tự cho từng item; không chạy song song trong phiên bản đầu.
11. Response reservation phải có trạng thái `ACTIVE` mới được coi là giữ thành công. Lưu `reservationId`, `inventoryItemId`, status `RESERVED` trong transaction ngắn.
12. Nếu reserve trả known definitive out-of-stock/business failure: mark item `RESERVATION_FAILED`, order `RESERVATION_FAILED`, rồi compensation mọi reservation đã được giữ.
13. Nếu reserve timeout/503: remote outcome unknown. Giữ item `PENDING_RESERVATION`, tăng retry metadata, dừng xử lý order hiện tại và trả `202` với resource `Location`.
14. Nếu reserve retry trả reservation `EXPIRED`: mark item `EXPIRED`, order `RESERVATION_EXPIRED`, và compensation các reservation khác còn `ACTIVE`.
15. Nếu gặp remote state không an toàn hoặc conflict không phân loại được, mark `RECONCILIATION_REQUIRED`; không tạo một reservation mới với reference khác để bypass idempotency.
16. Chỉ khi mọi item đã xác nhận `ACTIVE`/local `RESERVED`, chuyển Order sang `AWAITING_PAYMENT`.
17. Trả `201 Created` cho order mới đã giữ đủ hàng; `200`/`202` cho replay/resume theo trạng thái được định nghĩa.

### 10.4. Compensation khi reservation thất bại

1. Đánh dấu `RESERVATION_FAILED` sau lỗi nghiệp vụ xác định.
2. Release từng item đang local `RESERVED` qua Inventory.
3. Khi release trả thành công, persist item `RELEASED`.
4. Nếu release timeout, giữ item ở trạng thái cần recover; không giả định remote release thành công/thất bại.
5. Nếu release trả `409`, GET reservation để xác minh state:
   - `EXPIRED`: local item `EXPIRED`; Inventory đã giải phóng hàng theo contract.
   - `RELEASED`: local item `RELEASED`.
   - `COMMITTED`: `RECONCILIATION_REQUIRED`; không giả định item có thể sử dụng lại.
   - Không tìm thấy/không xác định: reconciliation hoặc xử lý contract-specific, không đánh dấu release thành công dựa trên 404.
6. Giữ `RESERVATION_FAILED` trong khi vẫn còn reserved items cần compensation; scheduler tiếp tục tìm items theo status.
7. Chỉ kết thúc xử lý tự động khi dữ liệu local phản ánh remote state đã xác minh hoặc đã đánh dấu reconciliation có chủ đích.

### 10.5. Reservation expiration reconciliation

Recovery phải quét cả orders `AWAITING_PAYMENT` đến hạn kiểm tra expiration. Không chỉ dựa vào order/item local status vì Inventory có scheduler riêng có thể đã hết hạn reservation.

- GET reservation trước khi đưa ra kết luận nếu local state có thể stale hoặc call reserve retry nhận status terminal.
- Khi một reservation `EXPIRED`, mark item `EXPIRED`, order `RESERVATION_EXPIRED`; release các reservation khác còn ACTIVE.
- Nếu reservation vẫn `ACTIVE` nhưng sắp hoặc đã hết hạn, không giả định được commit; Payment Service chưa triển khai.
- `COMMITTED` từ Inventory khi order đang chờ thanh toán là tình huống lệch trạng thái cần `RECONCILIATION_REQUIRED`.
- Không thể dùng TTL đơn thuần để kết luận payment success/failure.

### 10.6. Cancellation workflow

1. Tìm order, kiểm tra ownership/ADMIN.
2. Nếu `CANCELLED`, trả resource hiện có.
3. Nếu `CONFIRMED`, trả `409` vì payment flow chưa có.
4. Nếu `PENDING_RESERVATION`, không đồng thời cancel và reserve mà không đồng bộ; trả `409` có mô tả hoặc xử lý thông qua workflow state guard.
5. Với `AWAITING_PAYMENT`, persist `CANCELLATION_PENDING` trước các remote release calls.
6. Với `CANCELLATION_PENDING`, resume từ persisted item states.
7. Release từng item đang `RESERVED`, ghi nhận kết quả từng item.
8. Với remote `EXPIRED`, mark local item `EXPIRED`; không retry release vô hạn.
9. Nếu mọi remote reservation đã được release/expired theo xác minh, order thành `CANCELLED` (nếu semantics là người dùng yêu cầu hủy) hoặc giữ `RESERVATION_EXPIRED` nếu expiration là nguyên nhân kết thúc. Không trộn hai kết quả thành một trạng thái sai.
10. Nếu còn lỗi tạm thời, trả `202` và giữ `CANCELLATION_PENDING`.
11. Nếu remote state `COMMITTED` hoặc bất nhất không thể tự sửa an toàn, chuyển `RECONCILIATION_REQUIRED`.

### 10.7. `OrderStateService` và transaction boundary

Tạo `OrderStateService` chỉ khi cần để đặt transaction boundary rõ ràng.

- Phương thức lưu state ngắn và local DB only.
- Dùng `REQUIRES_NEW` khi cần commit một state transition độc lập với lỗi bên ngoài.
- Các lời gọi phải đi qua Spring bean proxy thật; không tự gọi method cùng bean rồi kỳ vọng propagation `REQUIRES_NEW` hoạt động.
- Không ném runtime exception trong cùng transaction sau khi muốn lưu một state transition, trừ khi `noRollbackFor` hoặc transaction boundary đã được thiết kế và test rõ ràng.
- Không giữ transaction khi gọi HTTP client.

---

## 11. Phase 7 — Retry policy và OrderRecoveryScheduler

### 11.1. Persisted retry metadata

Dùng `order_items.retry_count`, `next_retry_at`, `last_error_code` để lưu tiến trình retry cho từng physical item.

Cấu hình:

- `app.order.max-retry-attempts`
- `app.order.retry-base-delay-ms`
- `app.order.retry-max-delay-ms`
- `app.order.recovery-scan-delay-ms`
- `app.order.recovery-batch-size`

Dùng exponential backoff đơn giản với mức trần; tránh vòng retry mỗi 60 giây vô hạn.

### 11.2. Phân loại lỗi

**Có thể retry** (theo policy):

- Connect timeout/read timeout
- Connection refused
- HTTP 5xx/503
- Một số lỗi tạm thời được xác định trong contract

**Không retry mù quáng:**

- `400` validation/payload lỗi
- `401/403` do internal key/configuration sai
- `404` reservation mà local đang ghi nhận đã tồn tại (cần reconciliation)
- `409` terminal state (`EXPIRED`, `COMMITTED`) hoặc identity conflict

Với lỗi auth/configuration, log không chứa secret; đánh dấu error code thích hợp và đưa vào `RECONCILIATION_REQUIRED` hoặc policy alert có chủ đích thay vì retry vô hạn. Nếu có thể phân loại là lỗi cấu hình có thể sửa rồi retry thủ công, giữ reference và retry metadata.

### 11.3. Scheduler

Expected file: `scheduler/OrderRecoveryScheduler.java`.

- `@EnableScheduling` được bật tại application class theo conventions hiện có.
- Chạy các pass có batch giới hạn:
  1. `PENDING_RESERVATION` với items tới `next_retry_at`.
  2. `CANCELLATION_PENDING` với items cần release.
  3. `RESERVATION_FAILED` với items còn `RESERVED` cần compensation.
  4. `RESERVATION_EXPIRED` với các item khác còn `RESERVED` cần compensation.
  5. `AWAITING_PAYMENT` cần reconciliation expiration theo policy/expiry timestamps.
- Dùng query có trạng thái và thời gian retry; không load toàn bộ bảng.
- Đảm bảo một order đang được xử lý không bị scheduler và request API xử lý cùng lúc theo cách làm hỏng state. Dùng lock/conditional state transition hoặc một cơ chế claim đơn giản phù hợp với single-instance local scope; tránh transaction dài qua HTTP calls.
- Process từng order/item với transaction ngắn và try/catch từng record để lỗi một record không dừng batch.
- Khi hết retry budget, không bỏ record âm thầm; persist code/trạng thái `RECONCILIATION_REQUIRED` và nêu rõ cách xem xét thủ công.
- Ghi log theo `orderReference`/`orderItemReference`, không ghi key hoặc credential.

Đây là scheduler phù hợp cho môi trường local/single instance hiện tại. Multi-instance production cần cơ chế claim/lease/distributed coordination, nhưng chưa đưa vào scope này.

---

## 12. Phase 8 — JWT Security và Gateway integration

### 12.1. Order Service

Tạo `config/SecurityConfig.java` theo Servlet/MVC Spring Security conventions hiện tại.

- Verify RS256 signature bằng public key từ `app.jwt.public-key-location`.
- Validate issuer, audience và timestamps.
- Map claim `roles` với prefix `ROLE_`.
- Session stateless; CSRF/form login/HTTP Basic theo conventions resource server.
- Permit health và API docs nếu conventions hiện tại cho phép.
- Require JWT cho tất cả Order endpoints.
- Ownership check trong Order Service, không chỉ ở Gateway.
- Không copy WebFlux security code từ Gateway vào MVC Order Service.
- Verify trực tiếp cổng 8084 không JWT phải bị từ chối.

### 12.2. Gateway

Files có thể thay đổi:

- `api-gateway/src/main/resources/application.properties`
- `api-gateway/src/main/java/com/example/gateway/config/SecurityConfig.java`

Yêu cầu:

1. Đọc toàn bộ route hiện tại trước khi chỉnh.
2. Sửa route index trùng, đảm bảo mọi index là duy nhất và không ghi đè route hiện hữu.
3. Giữ Login, Product, Inventory Items và các policy hiện tại.
4. Không tạo public route `/api/v1/inventory-summaries` theo mặc định.
5. Thêm route `/api/v1/orders` và `/api/v1/orders/**` tới `http://localhost:8084`, dùng index trống kế tiếp sau khi đã làm sạch config.
6. Thêm explicit security matchers trước `.anyExchange().denyAll()`.
7. GET `/api/v1/orders` và `/api/v1/orders/**`: cho `USER`/`ADMIN` qua JWT; Order Service vẫn enforcement ownership.
8. POST `/api/v1/orders`: cho role được phép tạo order (USER/ADMIN theo policy đã chọn).
9. POST cancellation subresource: cho USER/ADMIN; ownership/ADMIN check tại Order Service.
10. Không permit toàn bộ POST `/api/v1/orders/**` nếu có thể giới hạn cụ thể `/api/v1/orders` và `/api/v1/orders/*/cancellation`.
11. Không tạo route `/internal/**` qua Gateway.
12. Chạy `clean verify` riêng cho Gateway sau thay đổi.

---

## 13. Phase 9 — Automated testing

Dùng testing libraries có sẵn trong project. Với Spring Boot 4.1.x, xác minh annotation test tương thích (`@WebMvcTest`, `MockMvc`, mock bean annotation hiện hành) thay vì sao chép các annotation deprecated/không còn được hỗ trợ.

### 13.1. OrderService unit tests

Ít nhất cần test:

1. Tạo order với một product, quantity 1.
2. Tạo order có nhiều product types.
3. Quantity > 1 tạo N item rows/N reservation attempts.
4. BigDecimal total chính xác.
5. Snapshot tên/giá không lấy từ client.
6. Product không tồn tại trước khi persist order.
7. Product inactive trước khi persist order.
8. Product Service outage trước khi persist → 503, không có order.
9. Tất cả reservations ACTIVE → `AWAITING_PAYMENT`.
10. Out-of-stock definitive → `RESERVATION_FAILED` và compensation.
11. Timeout sau khi Inventory có thể đã tạo reservation → `PENDING_RESERVATION`, stable references.
12. Retry nhận reservation ACTIVE → cập nhật local item RESERVED.
13. Retry nhận reservation EXPIRED → order/item expiration flow, không set RESERVED.
14. Retry nhận `COMMITTED` bất ngờ → reconciliation required.
15. Cùng idempotency key/payload → không tạo order mới.
16. Cùng idempotency key khác normalized payload → 409.
17. Concurrent duplicate key được xử lý sau rollback và re-read.
18. Cancellation toàn bộ release thành công.
19. Release timeout → cancellation pending/retry metadata.
20. Release 409 + GET EXPIRED → item EXPIRED, không retry release vô hạn.
21. Release 409 + GET COMMITTED → reconciliation, không cancel thành công.
22. Owner mismatch/ADMIN override.
23. Confirmed order không thể bị cancel trong scope hiện tại.
24. Không có public flow gọi Inventory commit trước khi payment flow tồn tại.
25. Scheduler retry/backoff và retry limit.

### 13.2. Controller tests

`OrderControllerTest` dùng MVC test slice và mock service tương thích phiên bản hiện tại.

Bao gồm:

- `201` và `Location`
- `200` idempotent replay/read
- `202` persisted pending workflow
- `400` validation
- `401` missing/invalid JWT
- `403`/`404` theo ownership policy
- `409` business/idempotency conflict
- `503` downstream unavailable trước persistence
- Error body theo format chung
- Pagination, sort allowlist và bounded page size

### 13.3. Repository/SQL Server integration tests

Dùng SQL Server thật hoặc environment test được cô lập.

- PK/FK/unique/check constraints
- Filtered index và migration metadata
- `idempotency_key` null/non-null semantics
- BigDecimal và Instant/DATETIME2 mapping
- Order + items transaction behavior
- Paging/ownership query behavior
- Concurrent identical idempotency requests chỉ tạo một order
- Không dùng `deleteAll()` hoặc thao tác cleanup rộng trên developer database
- Dùng unique test references và chỉ cleanup record do test tạo ra
- Phân biệt `@DataJpaTest` với DB thật và test chỉ mock/H2; không tuyên bố SQL Server integration nếu test không kết nối SQL Server.

### 13.4. Recovery/integration tests

- Scheduler không skip record khi status thay đổi trong batch.
- Một record lỗi không làm dừng batch.
- Backoff/max retry được persist và tôn trọng.
- Reservation timeout retry dùng cùng references.
- Expired reservation được reconcile, không biến thành RESERVED.
- Cancellation một phần và compensation có thể resume.
- Không tạo duplicate reservation/order trong retry.

---

## 14. Phase 10 — Manual Postman verification

Cung cấp URL, headers, body, expected status/body và expected database/Inventory state cho từng test.

1. Login lấy JWT USER và ADMIN.
2. GET Products để xác định product ID đang active.
3. Kiểm tra Inventory Summary để chọn product còn hàng.
4. POST Order một physical item → `201` + Location.
5. POST Order quantity 2 → hai `order_items` và hai reservation khác nhau.
6. Retry cùng `Idempotency-Key`/payload → không tạo order/reservation mới.
7. Same key, different normalized payload → `409`.
8. GET order by reference.
9. List orders, page bounds và sort allowlist.
10. USER đọc order người khác → policy `403` hoặc `404` nhất quán.
11. ADMIN đọc order user khác → `200`.
12. Cancel order đang `AWAITING_PAYMENT` → all releases verified rồi `CANCELLED`.
13. Release partial failure → `202`, scheduler phục hồi sau khi Inventory trở lại.
14. Reservation hết hạn trong `AWAITING_PAYMENT` → `RESERVATION_EXPIRED`, các reservation còn lại được compensation.
15. Inventory timeout sau khi request có thể đã tới Inventory → retry với stable refs, không duplicate.
16. Bad internal key/401 hoặc 403: không retry vô hạn, record được đánh dấu reconciliation theo policy.
17. Invalid/expired JWT → `401`.
18. Direct Order port 8084 without JWT → `401`.
19. Gateway route/matchers không làm hỏng Login/Product/Inventory Items.
20. Xác nhận Gateway không công khai Inventory Summary hoặc `/internal/**`.

Chỉ chạy các scenario thay đổi dữ liệu với test IDs/references duy nhất; không dùng ID reservation cố định có thể không tồn tại.

---

## 15. Phase 11 — Build, verification và báo cáo

Chạy từ `D:\java\JewelryShop\order-service`:

```powershell
.\mvnw.cmd clean verify
```

Nếu Gateway thay đổi, chạy riêng:

```powershell
cd D:\java\JewelryShop\api-gateway
.\mvnw.cmd clean verify
```

### Final report phải gồm

1. Files created/modified và lý do thay đổi.
2. Database nào đã được kiểm tra; script được tạo, chạy và verify ở mức nào.
3. Metadata/check constraints/indexes thực tế.
4. Entity/DTO/API contract và state machine cuối cùng.
5. Product/Inventory client behavior và HTTP error mapping.
6. Idempotency và concurrent request evidence.
7. Compensation/cancellation/expiration reconciliation.
8. Retry/backoff/max attempts và trạng thái reconciliation.
9. JWT validation, ownership authorization và Gateway routing.
10. Maven/Surefire counts: tests run, failures, errors, skipped.
11. Phân biệt unit, MVC, SQL Server integration và manual tests.
12. Các tests còn blocked/chưa chạy.
13. Known limitations: Payment Service chưa có; không có distributed scheduler cho nhiều instance; bảo mật endpoint Inventory cũ cần được quản lý riêng.

### Acceptance checklist

- [ ] Migration schema idempotent và bảo toàn dữ liệu có sẵn.
- [ ] JPA mapping đúng database schema với `ddl-auto=none`.
- [ ] Tạo order snapshots đúng và tính tổng `BigDecimal`.
- [ ] Quantity mở rộng thành các item/reservation riêng.
- [ ] Không có reservation mới khi retry cùng order item reference.
- [ ] Concurrent Idempotency-Key chỉ tạo một Order.
- [ ] Timeout/unknown outcome không bị nhầm với definitive failure.
- [ ] Không mark reservation EXPIRED thành RESERVED.
- [ ] Compensation/release có thể resume và không bỏ qua remote state.
- [ ] Expired reservation được phản ánh đúng ở Order và OrderItem.
- [ ] Retry được giới hạn/backoff; không retry lỗi permanent vô hạn.
- [ ] USER ownership, ADMIN policy và JWT kiểm tra tại Order Service.
- [ ] Gateway route indices không trùng và không expose `/internal/**` hoặc Inventory Summary ngoài ý muốn.
- [ ] Không có public order flow commit Inventory khi chưa có Payment Service.
- [ ] Unit/controller/SQL integration tests được báo cáo trung thực.
- [ ] Full `clean verify` pass cho Order Service và Gateway (nếu chỉnh Gateway).
- [ ] Manual Create → Get → List → Cancel đã được kiểm thử hoặc ghi rõ chưa kiểm thử.

---

## 16. File dự kiến tạo/chỉnh sửa

### Order Service

```text
order-service/
├── Script_JewelryOrderDB.sql
├── Script_JewelryOrderDB_Workflow.sql
└── src/
    ├── main/java/com/example/order/
    │   ├── OrderServiceApplication.java
    │   ├── entity/
    │   │   ├── Order.java
    │   │   └── OrderItem.java
    │   ├── enums/
    │   │   ├── OrderStatus.java
    │   │   └── OrderItemStatus.java
    │   ├── repository/
    │   │   ├── OrderRepository.java
    │   │   └── OrderItemRepository.java
    │   ├── client/
    │   │   ├── ProductClient.java
    │   │   └── InventoryReservationClient.java
    │   ├── config/
    │   │   ├── RestClientConfig.java
    │   │   ├── SecurityConfig.java
    │   │   └── OpenApiConfig.java (only if needed)
    │   ├── dto/
    │   │   ├── external/
    │   │   │   ├── ProductDetailDto.java
    │   │   │   └── InventoryReservationDto.java
    │   │   ├── request/
    │   │   │   ├── CreateOrderRequest.java
    │   │   │   └── OrderItemRequest.java
    │   │   └── response/
    │   │       ├── OrderResponse.java
    │   │       └── OrderItemResponse.java
    │   ├── exception/
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── OrderNotFoundException.java
    │   │   ├── OrderConflictException.java
    │   │   ├── IdempotencyConflictException.java
    │   │   ├── ProductUnavailableException.java
    │   │   ├── DownstreamServiceException.java
    │   │   ├── OrderAccessDeniedException.java
    │   │   └── ApiErrorResponse.java (reuse equivalent if available)
    │   ├── controller/
    │   │   └── OrderController.java
    │   ├── service/
    │   │   ├── OrderService.java
    │   │   └── OrderStateService.java
    │   └── scheduler/
    │       └── OrderRecoveryScheduler.java
    └── test/java/com/example/order/
        ├── controller/OrderControllerTest.java
        ├── service/OrderServiceTest.java
        ├── repository/OrderRepositoryTest.java
        ├── security/SecurityConfigTest.java (if needed)
        └── scheduler/OrderRecoverySchedulerTest.java
```

Only create files justified by source conventions and implementation needs. Reuse a current `ApiErrorResponse` or mapper if the project already has an equivalent.

### Files outside Order Service that may change

- `api-gateway/src/main/resources/application.properties`
- `api-gateway/src/main/java/com/example/gateway/config/SecurityConfig.java`

Do not modify Product/Inventory production code unless an actual contract defect blocks the Order workflow. If such a change is necessary, document it and add regression tests in that service.

---

## 17. Implementation order for the agent

1. Finish Phase 0 audit and schema verification.
2. Finalize status transitions and schema/migration before coding the entities.
3. Implement Entity/Repository, compile and run repository tests.
4. Implement RestClient configuration and clients, with contract-specific error mapping.
5. Implement transaction-safe state persistence and idempotency boundary.
6. Implement Order creation and compensation workflow.
7. Implement GET/list/cancellation REST APIs and ownership validation.
8. Implement expiration reconciliation and bounded recovery with retry policy.
9. Implement JWT Resource Server and Gateway route/security changes.
10. Add/fix unit, MVC, SQL Server integration and scheduler tests.
11. Run full builds, inspect reports and perform manual Postman tests.
12. Deliver a factual audit report and stop if a critical correctness/security requirement remains unverified.

*Updated plan date: 2026-10-27.*
