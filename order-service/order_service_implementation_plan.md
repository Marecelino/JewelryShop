# Order Service — Implementation Plan

> **Trạng thái:** Audit hoàn tất. Đây là plan thuần, chưa có code.
> **Workspace:** `D:\java\JewelryShop\order-service`
> **Package gốc:** `com.example.order`

---

## Kết quả Audit (Phase 0)

### Thực trạng order-service
| Hạng mục | Giá trị xác minh |
|---|---|
| Spring Boot | 4.1.2 |
| Java | 21 |
| Package | `com.example.order` |
| Codebase hiện tại | Chỉ có `OrderServiceApplication.java` (skeleton) |
| `application.properties` | Chỉ có `spring.application.name=order-service` |
| SQL script Order DB | **KHÔNG tìm thấy** — cần tạo toàn bộ schema |
| Dependencies chính | `spring-boot-starter-data-jpa`, `security-oauth2-resource-server`, `validation`, `webmvc`, `springdoc-openapi-starter-webmvc-ui:3.2.0`, `mssql-jdbc`, `actuator` |
| Dependencies test | `spring-boot-starter-test`, `webmvc-test` (x2, có thể cần dọn), `data-jpa-test`, `actuator-test`, `security-oauth2-resource-server-test` |

### Các service khác — facts đã xác minh
| Service | Port | DB | Package |
|---|---|---|---|
| login-service | 8083 | JewelryLoginDemo | `com.example.login` |
| product-service | 8082 | JewelryStoreDemo | `com.example.product` |
| inventory-service | 8081 | JewelryInventoryDemo | `com.example.inventory` |
| api-gateway | 8085 | — | `com.example.gateway` (Spring Cloud Gateway WebFlux) |

### JWT Contract đã xác minh
- **Issuer:** `http://localhost:8083`
- **Audience:** `jewelryshop-api`
- **Algorithm:** RS256
- **sub:** `user.getId()` (Long → String)
- **Claim `roles`:** list, ví dụ `["USER"]` hoặc `["ADMIN"]`
- **Public key:** `file:D:/java/JewelryShop/login-service/keys/public_key.pem`

### Inventory API Contract đã xác minh
**POST `/internal/api/v1/inventory/reservations`**
```json
{
  "orderReference": "ORD-...",
  "orderItemReference": "ITEM-...",
  "productId": 1,
  "ttlMinutes": 15
}
```
**Response (record):** `reservationId`, `orderReference`, `orderItemReference`, `productId`, **`inventoryItemId`** (Long, NOT NULL trong DB), `status`, `ttlMinutes`, `reservedAt`, `expiresAt`, `completedAt`, `createdAt`, `updatedAt`

**Reservation status:** `ACTIVE`, `COMMITTED`, `RELEASED`, `EXPIRED`

**Security:** Header `X-Internal-Service-Key` so sánh constant-time với `${inventory.internal.auth.api-key}`

**Idempotency:** cùng `(orderReference, orderItemReference)` + `productId` + `ttlMinutes` → 200 OK; khác → 409

**Other endpoints:**
- `GET /internal/api/v1/inventory/reservations/{reservationId}` → 200
- `POST /internal/api/v1/inventory/reservations/{reservationId}/commit` → 200
- `POST /internal/api/v1/inventory/reservations/{reservationId}/release` → 200 (idempotent nếu RELEASED, 409 nếu COMMITTED/EXPIRED)

### Product API Contract đã xác minh
- `GET /api/v1/products/{id}` → `ProductResponse`
- Fields cần: `productId` (Integer), `productName` (String), `price` (BigDecimal), `active` (Boolean)
- 404 nếu không tìm thấy

### Gateway đã xác minh
- WebFlux + `@EnableWebFluxSecurity`
- `JwtGrantedAuthoritiesConverter`, claim `roles`, prefix `ROLE_`
- `/api/v1/orders/**` **chưa có route** → cần thêm
- `anyExchange().denyAll()` — order route phải khai báo tường minh
- Format: `spring.cloud.gateway.server.webflux.routes[N]...`
- Lỗi hiện tại: route index `[3]` trùng lặp — cần sửa

### Error Response Pattern đã xác minh (nhất quán trong toàn hệ thống)
```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "path": "/api/v1/...",
  "validationErrors": { "field": "message" }
}
```

---

## Các vấn đề phát hiện khi audit

> **[WARNING] Schema không tìm thấy:** `Script_JewelryOrderDB.sql` không có trong repo. Phase 1 sẽ tạo cả script tạo DB mới lẫn migration script.

> **[WARNING] Gateway route trùng lặp:** Route index `[3]` khai báo hai lần. Phải sửa khi thêm order route.

> **[NOTE] `inventoryItemId` luôn có trong response** — `inventory_item_id BIGINT NOT NULL` trong schema inventory. Order Service lưu được giá trị này.

> **[NOTE] Không có Lombok** — Tuân thủ convention hiện tại, viết getters/setters thủ công.

> **[CAUTION] Duplicate test dependency:** `spring-boot-starter-webmvc-test` xuất hiện hai lần trong pom.xml — theo dõi khi build.

---

## Cấu trúc file sẽ tạo

```
order-service/
├── Script_JewelryOrderDB.sql              [MỚI — Phase 1]
├── Script_JewelryOrderDB_Workflow.sql     [MỚI — Phase 1]
└── src/main/
    ├── resources/
    │   └── application.properties         [SỬA — Phase 2]
    └── java/com/example/order/
        ├── OrderServiceApplication.java   [GIỮ NGUYÊN, thêm @EnableScheduling]
        ├── entity/
        │   ├── Order.java                 [MỚI — Phase 3]
        │   └── OrderItem.java             [MỚI — Phase 3]
        ├── enums/
        │   ├── OrderStatus.java           [MỚI — Phase 3]
        │   └── OrderItemStatus.java       [MỚI — Phase 3]
        ├── repository/
        │   ├── OrderRepository.java       [MỚI — Phase 3]
        │   └── OrderItemRepository.java   [MỚI — Phase 3]
        ├── client/
        │   ├── ProductClient.java         [MỚI — Phase 4]
        │   └── InventoryReservationClient.java [MỚI — Phase 4]
        ├── config/
        │   ├── RestClientConfig.java      [MỚI — Phase 4]
        │   ├── SecurityConfig.java        [MỚI — Phase 7]
        │   └── OpenApiConfig.java         [MỚI — Phase 2]
        ├── dto/
        │   ├── external/
        │   │   ├── ProductDetailDto.java  [MỚI — Phase 4]
        │   │   └── InventoryReservationDto.java [MỚI — Phase 4]
        │   ├── request/
        │   │   ├── CreateOrderRequest.java [MỚI — Phase 5]
        │   │   └── OrderItemRequest.java  [MỚI — Phase 5]
        │   └── response/
        │       ├── OrderResponse.java     [MỚI — Phase 5]
        │       └── OrderItemResponse.java [MỚI — Phase 5]
        ├── exception/
        │   ├── ApiErrorResponse.java      [MỚI — Phase 5]
        │   ├── GlobalExceptionHandler.java [MỚI — Phase 5]
        │   ├── OrderNotFoundException.java [MỚI — Phase 5]
        │   ├── OrderConflictException.java [MỚI — Phase 5]
        │   ├── IdempotencyConflictException.java [MỚI — Phase 5]
        │   ├── ProductUnavailableException.java [MỚI — Phase 5]
        │   └── DownstreamServiceException.java [MỚI — Phase 5]
        ├── controller/
        │   └── OrderController.java       [MỚI — Phase 5]
        ├── service/
        │   ├── OrderService.java          [MỚI — Phase 6]
        │   └── OrderStateService.java     [MỚI — Phase 6]
        └── scheduler/
            └── OrderRecoveryScheduler.java [MỚI — Phase 6]
```

**Files ngoài order-service thay đổi:**
- `api-gateway/src/main/resources/application.properties` — thêm route + sửa trùng lặp
- `api-gateway/src/main/java/com/example/gateway/config/SecurityConfig.java` — thêm order matchers

---

## PHASE 1 — Database Schema

### A. `Script_JewelryOrderDB.sql` (tạo từ đầu, idempotent)

**Bảng `dbo.orders`:**
```
order_id         BIGINT IDENTITY(1,1) PRIMARY KEY
order_reference  VARCHAR(100) NOT NULL
user_id          BIGINT NOT NULL
status           VARCHAR(30) NOT NULL
                 CHECK (status IN ('PENDING_RESERVATION','AWAITING_PAYMENT',
                   'CONFIRMED','CANCELLED','RESERVATION_FAILED','CANCELLATION_PENDING'))
total_amount     DECIMAL(18,2) NOT NULL
idempotency_key  VARCHAR(100) NULL
created_at       DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
updated_at       DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
```
Constraints: `PK`, `UQ_orders_order_reference`, `CK_orders_status`
Indexes:
- `UX_orders_user_idempotency_key` UNIQUE FILTERED `(user_id, idempotency_key) WHERE idempotency_key IS NOT NULL`
- `IX_orders_user_id`
- `IX_orders_status`

**Bảng `dbo.order_items`:**
```
order_item_id        BIGINT IDENTITY(1,1) PRIMARY KEY
order_id             BIGINT NOT NULL FK dbo.orders(order_id)
order_item_reference VARCHAR(100) NOT NULL
product_id           INT NOT NULL
product_name_snapshot NVARCHAR(100) NOT NULL
unit_price           DECIMAL(18,2) NOT NULL
reservation_id       BIGINT NULL
inventory_item_id    BIGINT NULL
status               VARCHAR(30) NOT NULL
                     CHECK (status IN ('PENDING_RESERVATION','RESERVED',
                       'COMMITTED','RELEASED','RESERVATION_FAILED'))
created_at           DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
updated_at           DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
```
Constraints: `PK`, `FK_order_items_orders`, `UQ_order_items_order_id_ref(order_id, order_item_reference)`, `CK_order_items_status`
Indexes:
- `IX_order_items_order_id`
- `IX_order_items_status_pending` FILTERED WHERE `status = 'PENDING_RESERVATION'`
- `IX_order_items_reservation_id` FILTERED WHERE `reservation_id IS NOT NULL`

### B. `Script_JewelryOrderDB_Workflow.sql` (additive migration)

Chạy được nhiều lần:
1. Thêm `idempotency_key` nếu chưa có
2. Tạo `UX_orders_user_idempotency_key` nếu chưa có
3. Cập nhật check constraint status nếu chưa có `CANCELLATION_PENDING`
4. In PASS/FAIL verification queries

Bọc trong `BEGIN TRANSACTION ... COMMIT` với `BEGIN CATCH ... ROLLBACK TRANSACTION; THROW`.

---

## PHASE 2 — Configuration

**`application.properties` sau khi sửa:**
```properties
spring.application.name=order-service
server.port=8084

# SQL Server
spring.datasource.url=${DB_URL:jdbc:sqlserver://Marecelino:1433;databaseName=JewelryOrderDemo;encrypt=true;trustServerCertificate=true}
spring.datasource.username=${DB_USER:sa}
spring.datasource.password=${DB_PASSWORD:123456}
spring.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver

# JPA
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl

# JWT (RS256, public key only)
app.jwt.issuer=${JWT_ISSUER:http://localhost:8083}
app.jwt.audience=${JWT_AUDIENCE:jewelryshop-api}
app.jwt.public-key-location=${JWT_PUBLIC_KEY_LOCATION:file:D:/java/JewelryShop/login-service/keys/public_key.pem}

# Downstream services
product.service.url=${PRODUCT_SERVICE_URL:http://localhost:8082}
inventory.service.url=${INVENTORY_SERVICE_URL:http://localhost:8081}
inventory.internal.api-key=${INVENTORY_INTERNAL_API_KEY:}

# HTTP client timeouts
app.client.connect-timeout-ms=2000
app.client.read-timeout-ms=5000

# Order workflow
app.order.reservation-ttl-minutes=15
app.order.max-items-per-order=20

# Recovery scheduler
app.order.recovery-scan-delay-ms=60000
app.order.recovery-batch-size=50
```

---

## PHASE 3 — Domain Model

### State Machines

**OrderStatus:**
```
PENDING_RESERVATION ──(all reserved)──> AWAITING_PAYMENT ──(payment confirmed, future)──> CONFIRMED
        │                                     │
        │(definitive 409 from inventory)       │(cancel requested)
        ▼                                     ▼
RESERVATION_FAILED               CANCELLATION_PENDING ──(all released)──> CANCELLED
```

**OrderItemStatus:**
```
PENDING_RESERVATION ──(reserve success)──> RESERVED ──(payment commit, future)──> COMMITTED
        │                                     │
        │(definitive fail)                    │(cancel/release)
        ▼                                     ▼
RESERVATION_FAILED                         RELEASED
```

### Entity `Order`
- `@Table(name = "orders", schema = "dbo")`
- `@Enumerated(EnumType.STRING)` cho `status`
- `Instant` cho `createdAt`, `updatedAt` — `@Column(columnDefinition = "DATETIME2(3)")`
- `@OneToMany(mappedBy = "order", cascade = ALL, fetch = LAZY)` cho items
- Không có JPA relationship sang tables ngoài hệ thống

### Entity `OrderItem`
- `@Table(name = "order_items", schema = "dbo")`
- `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "order_id")`
- `reservationId` (Long, nullable) — ID từ Inventory Service
- `inventoryItemId` (Long, nullable) — ID physical item từ Inventory Service
- Không có JPA FK sang bảng inventory

### Repositories
**`OrderRepository`:**
```
findByOrderReference(String) → Optional<Order>
findByUserIdAndIdempotencyKey(Long, String) → Optional<Order>
findByUserId(Long, Pageable) → Page<Order>       // USER scope
findAll(Pageable) → Page<Order>                  // ADMIN scope
// Tìm để recover:
findTop{N}ByStatusOrderByCreatedAtAsc(OrderStatus) → List<Order>
findByStatusIn(Collection<OrderStatus>, Pageable) → Page<Order>
```

**`OrderItemRepository`:**
```
findByOrderId(Long) → List<OrderItem>
findByOrderIdAndStatus(Long, OrderItemStatus) → List<OrderItem>
// Tìm RESERVED items cần release:
findByOrder_StatusAndStatus(OrderStatus, OrderItemStatus) → List<OrderItem>
```

---

## PHASE 4 — External Clients

### `ProductClient`
Gọi Product Service, trả về `ProductDetailDto(productId, productName, price)`.
- 404 → `ProductUnavailableException("Product X not found")`
- `active = false` → `ProductUnavailableException("Product X is inactive")`
- `ResourceAccessException` / 5xx → `DownstreamServiceException(...)`
- Phân biệt 404 với timeout/5xx — không nhầm lẫn

### `InventoryReservationClient`
Gọi Inventory Service với header `X-Internal-Service-Key` từ config.
- `reserve()`: 201/200 → InventoryReservationDto; 409 → `OrderConflictException`; 404 → `ProductUnavailableException`; 401/403 → `DownstreamServiceException` + log.error; timeout → `DownstreamServiceException`
- `release()`: 200 → success (idempotent nếu đã RELEASED); 409 (COMMITTED/EXPIRED) → `OrderConflictException`; 404 → `OrderConflictException`; timeout → `DownstreamServiceException`
- `getReservation()`: 200 / 404

### `RestClientConfig`
- `productRestClient` bean: `baseUrl = ${product.service.url}`, explicit connect/read timeouts
- `inventoryRestClient` bean: `baseUrl = ${inventory.service.url}`, `defaultHeader("X-Internal-Service-Key", apiKey)`, timeouts
- Dùng `JdkClientHttpRequestFactory` (JDK 21 built-in) hoặc `SimpleClientHttpRequestFactory`
- Timeout bắt buộc — `RestClient` không có timeout mặc định

---

## PHASE 5 — Public REST API

### Request DTOs
```
CreateOrderRequest:
  items: List<OrderItemRequest> @NotEmpty, @Size(max=20)

OrderItemRequest:
  productId: Integer @NotNull @Positive
  quantity:  Integer @NotNull @Min(1) @Max(10)
```
Service-layer validation thêm: tổng quantity không vượt `max-items-per-order`.

### Response DTOs
```
OrderResponse:
  orderReference, userId, status, totalAmount
  items: List<OrderItemResponse>
  createdAt, updatedAt

OrderItemResponse:
  orderItemReference, productId, productNameSnapshot, unitPrice
  status, reservationId (nullable), inventoryItemId (nullable)
```

### Exception → HTTP Mapping
```
OrderNotFoundException       → 404
OrderConflictException       → 409
IdempotencyConflictException → 409
ProductUnavailableException  → 409 (product invalid) hoặc 503 (service down)
DownstreamServiceException   → 503
OrderAccessDeniedException   → 403
MethodArgumentNotValidException → 400
```

### Endpoints
```
POST   /api/v1/orders                              createOrder       Header: Idempotency-Key (required)
GET    /api/v1/orders/{orderReference}             getOrder
GET    /api/v1/orders?page=0&size=10&sort=...     listOrders
POST   /api/v1/orders/{orderReference}/cancellation  requestCancellation
```

---

## PHASE 6 — Order Workflow

### `OrderService.createOrder()`

```
1. Extract userId từ JWT sub (Long.parseLong(jwt.getSubject()))
2. Đọc Idempotency-Key từ header (required, validate not blank)
3. Kiểm tra (userId, idempotencyKey) trong DB:
   - Tồn tại + same normalized payload → resume/return (200 OK hoặc 202 nếu vẫn PENDING)
   - Tồn tại + khác payload → IdempotencyConflictException (409)
4. Validate products qua ProductClient (unique productIds):
   - Lưu vào Map<Integer, ProductDetailDto>
   - Fail trước persist → 409 hoặc 503 tùy loại lỗi (order CHƯA được tạo)
5. Expand quantity → danh sách OrderItem (mỗi unit là 1 row)
6. Tính totalAmount = BigDecimal sum(unitPrice per item)
7. Generate stable orderReference = "ORD-" + alphanumeric unique
8. Generate stable orderItemReference = "ITEM-{N}-{orderRef}" hoặc tương tự
9. [TRANSACTION 1] Lưu Order(PENDING_RESERVATION) + tất cả OrderItems(PENDING_RESERVATION) → COMMIT
10. Sequential reserve per item (không parallel):
    - reserve(orderRef, itemRef, productId, ttlMinutes)
    - Thành công → [TRANSACTION 2] cập nhật item: RESERVED + reservationId + inventoryItemId
    - 409 (out of stock) → record item RESERVATION_FAILED, goto compensation
    - timeout/503 → giữ item PENDING_RESERVATION, thoát loop (retry bởi scheduler)
11. Nếu có item RESERVATION_FAILED:
    a. [TRANSACTION] Order → RESERVATION_FAILED
    b. Release mọi item RESERVED → gọi Inventory release()
    c. Sau mỗi release thành công → [SHORT TX] item → RELEASED
    d. Release fail → giữ RESERVED (scheduler cleanup)
    → Return 409
12. Nếu tất cả RESERVED:
    a. [TRANSACTION] Order → AWAITING_PAYMENT
    → 201 Created (order mới) hoặc 200 OK (idempotent)
13. Nếu còn item PENDING (timeout):
    → 202 Accepted + Location header (scheduler retry)
```

### `OrderService.requestCancellation()`
```
1. Tìm order, kiểm tra ownership
2. CANCELLED → return 200 (idempotent)
3. CONFIRMED → 409 "Cannot cancel confirmed order"
4. PENDING_RESERVATION → 409 "Order creation in progress"
5. RESERVATION_FAILED (không có RESERVED items) → mark CANCELLED, return 200
6. AWAITING_PAYMENT hoặc CANCELLATION_PENDING:
   a. [TX] Order → CANCELLATION_PENDING
   b. Lấy tất cả items RESERVED
   c. release() từng item:
      - Success → [SHORT TX] item → RELEASED
      - Fail → giữ RESERVED, log
   d. Còn RESERVED → 202 Accepted
   e. Tất cả RELEASED → [TX] Order → CANCELLED → 200 OK
```

### `OrderStateService`
`@Transactional(propagation = REQUIRES_NEW)` cho các phương thức cập nhật state đơn lẻ.
Pattern giống `ReservationStateService` trong inventory-service.

### `OrderRecoveryScheduler`
`@Scheduled(fixedDelayString = "${app.order.recovery-scan-delay-ms}")` — cần `@EnableScheduling`.

3 luồng recovery, bounded batch:
1. **PENDING_RESERVATION**: retry reserve với stable references
2. **CANCELLATION_PENDING**: retry release cho items còn RESERVED
3. **RESERVATION_FAILED cleanup**: release items RESERVED còn sót

Mỗi order xử lý độc lập (REQUIRES_NEW), lỗi một order không dừng batch.

---

## PHASE 7 — JWT Security

### `SecurityConfig` trong order-service (Servlet MVC)
Pattern giống login-service `SecurityConfig.java`:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // JwtDecoder: NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(RS256)
    //             + validators: timestamp, issuer (http://localhost:8083), audience (jewelryshop-api)
    // JwtAuthenticationConverter: claim "roles", prefix "ROLE_"
    // SecurityFilterChain:
    //   CSRF off, stateless
    //   Permit: /actuator/health, /swagger-ui/**, /v3/api-docs/**
    //   Require auth: POST/GET /api/v1/orders/**
    //   anyRequest().denyAll()
}
```

Đọc public key: `RsaKeyConverters.x509()` với Resource từ `app.jwt.public-key-location`.
Không cần RSAPrivateKey — chỉ validate token.

### API Gateway
**`application.properties`** — thêm (sau khi sửa index trùng lặp):
```properties
# Fix trùng lặp: đổi inventory route thứ hai thành đúng index
spring.cloud.gateway.server.webflux.routes[3].id=inventory-summary-service
...

# Route 4: Order Service
spring.cloud.gateway.server.webflux.routes[4].id=order-service
spring.cloud.gateway.server.webflux.routes[4].uri=http://localhost:8084
spring.cloud.gateway.server.webflux.routes[4].predicates[0]=Path=/api/v1/orders,/api/v1/orders/**
```

**`SecurityConfig.java`** — thêm order matchers trước `anyExchange().denyAll()`:
```java
// USER và ADMIN: đọc orders
.pathMatchers(HttpMethod.GET, "/api/v1/orders", "/api/v1/orders/**")
    .hasAnyRole("USER", "ADMIN")
// USER và ADMIN: tạo order, yêu cầu cancellation
.pathMatchers(HttpMethod.POST, "/api/v1/orders", "/api/v1/orders/**")
    .hasAnyRole("USER", "ADMIN")
```

Ownership check (USER chỉ xem order của mình) được enforce trong OrderService, không chỉ Gateway.

---

## PHASE 8 — Testing Plan

### A. Unit Tests (`OrderServiceTest`)
`@ExtendWith(MockitoExtension.class)`, mock tất cả dependencies.

| # | Test case | Verify |
|---|---|---|
| 1 | `createOrder_singleItem_success` | 1 row, AWAITING_PAYMENT |
| 2 | `createOrder_multipleProductTypes` | expand correctly |
| 3 | `createOrder_quantityTwo_expandsToTwoRows` | 2 rows, 2 reservations |
| 4 | `createOrder_totalAmountBigDecimal` | precision correct |
| 5 | `createOrder_snapshotNameAndPrice` | từ ProductClient, không từ request |
| 6 | `createOrder_productNotFound_noOrderCreated` | ProductUnavailableException |
| 7 | `createOrder_productInactive_noOrderCreated` | ProductUnavailableException |
| 8 | `createOrder_productServiceDown_503_noOrderCreated` | DownstreamServiceException |
| 9 | `createOrder_allReserved_awaitingPayment` | status transition |
| 10 | `createOrder_outOfStock_compensation` | RESERVATION_FAILED, items RELEASED |
| 11 | `createOrder_inventoryTimeout_orderPending` | PENDING_RESERVATION, no FAILED |
| 12 | `createOrder_stableReferences_onRetry` | same orderRef/itemRef |
| 13 | `createOrder_idempotentKey_samePayload` | return existing |
| 14 | `createOrder_idempotentKey_differentPayload` | 409 |
| 15 | `createOrder_pendingOrder_resume` | tiếp tục từ PENDING |
| 16 | `cancelOrder_awaitingPayment_allReleased` | CANCELLED |
| 17 | `cancelOrder_partialReleaseFail_202` | CANCELLATION_PENDING |
| 18 | `cancelOrder_alreadyCancelled_idempotent` | 200 |
| 19 | `cancelOrder_confirmed_409` | không cho cancel |
| 20 | `cancelOrder_differentUser_403` | ownership |
| 21 | `cancelOrder_admin_canCancel` | ADMIN override |
| 22 | `noFlow_commitsInventory_beforePayment` | verify commit() không được gọi |

### B. Controller Tests (`OrderControllerTest`)
`@WebMvcTest` + `@MockBean` service + mock JWT.

HTTP status coverage: 201, 200, 202, 400, 401, 403, 404, 409, 503

### C. Repository Tests (`OrderRepositoryTest`)
`@DataJpaTest` với SQL Server thật.
Verify: unique constraints, filtered index, FK, check constraint, BigDecimal precision, Instant mapping.

### D. Recovery Tests
Scheduler batch isolation, idempotent retries, bounded batch.

---

## PHASE 9 — Manual Postman Test Outline

1. Login → JWT (USER + ADMIN)
2. GET products → active productIds
3. Create order (1 item, qty=1) → 201 + Location
4. Create order (qty=2) → verify 2 order_item rows
5. Same Idempotency-Key → 200
6. Same key, different body → 409
7. GET order → 200, check snapshots
8. List orders → pagination works
9. Cross-user GET → 403
10. ADMIN GET other user → 200
11. Cancel order → 200 + verify inventory RELEASED
12. Inventory service down → 202 (if order persisted), 503 (if before persist)
13. Recovery scheduler → order recovered after service back up
14. Invalid/expired JWT → 401
15. Direct port 8084 without JWT → 401
16. Existing routes (product, inventory, login) still work via Gateway

---

## PHASE 10 — Build & Acceptance

```bash
# Order Service
cd D:\java\JewelryShop\order-service
.\mvnw.cmd clean verify

# API Gateway (chỉ nếu đã sửa)
cd D:\java\JewelryShop\api-gateway
.\mvnw.cmd clean verify
```

### Acceptance Checklist
- [ ] Schema tạo/migrate thành công, verification queries PASS
- [ ] Order Service compiles (0 errors)
- [ ] Unit tests: tất cả PASS
- [ ] Controller tests: tất cả PASS
- [ ] Repository tests: PASS (hoặc ghi rõ nếu skip vì thiếu DB)
- [ ] Gateway compiles và tests PASS
- [ ] Manual: full Create→Get→List→Cancel flow qua Gateway
- [ ] Manual: Idempotency hoạt động đúng
- [ ] Manual: Ownership enforced
- [ ] Manual: JWT validated ở Gateway VÀ port 8084 trực tiếp
- [ ] Existing service routes không bị ảnh hưởng

---

## Phụ lục — Quyết định thiết kế

| Quyết định | Lý do |
|---|---|
| `OrderStateService` với `REQUIRES_NEW` | Tránh 1 item failure rollback cả batch |
| Không forward user JWT sang Inventory | Inventory dùng internal key riêng biệt |
| Sequential HTTP calls (không parallel) | Đơn giản, dễ trace, state machine rõ ràng |
| `Idempotency-Key` là header | RESTful convention, tách khỏi domain body |
| quantity expand thành N rows | Mỗi jewelry item vật lý = 1 reservation riêng |
| Price snapshot từ ProductClient | Ngăn client gửi giá giả |
| Recovery scheduler single-instance | Không có Kafka/distributed lock trong scope |
| Public key path → login-service/keys | Chỉ đọc public key, không copy private key |
| `@EnableScheduling` trên Application | Cần thiết cho scheduler, theo pattern inventory |

---

*Plan tạo lúc: 2026-10-27. Bắt đầu implementation từ Phase 1.*
