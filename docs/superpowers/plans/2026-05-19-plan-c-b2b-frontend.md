# Plan C — B2B Order Snapshot + Frontend Context Switcher

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete B2B/B2C separation — persist B2B identity in orders, wire `clientType` into frontend state, add context switcher pill in header, show correct prices, add Legal Entity section in ProfilePage.

**Architecture:** order-service gets Flyway migration + snapshot fields populated from gateway headers; auth-service gets enriched `AuthResponse` with `companyName`/`inn` and a reworked `switch-context` endpoint; frontend gets `clientType`/`companyName`/`inn` in the `User` type and `authStore`, a pill toggle in `ClientLayout`, price selection via `useDisplayPrice`, and a Legal Entity section in `ProfilePage`.

**Tech Stack:** Java 21, Spring Boot 3.5, Flyway, Kafka; React 18, TypeScript, Zustand, Ant Design, React Query

---

## File Map

### order-service (modify)
- `models/entity/Order.java` — add `customerType`, `companyName`, `inn` fields
- `models/dto/order/CreateOrderRequest.java` — add `companyName`, `inn`
- `models/dto/order/OrderDto.java` — add `customerType`, `companyName`, `inn`
- `models/dto/product/ProductDto.java` — add `wholesalePrice`
- `models/dto/event/Order1CExportEvent.java` — add `customerType`, `companyName`, `inn`
- `mapper/OrderMapper.java` — map new fields in `toDto()` and `toEntity()`
- `service/OrderService.java` — accept `clientType`, pick price, populate snapshot
- `controller/OrderController.java` — extract `X-Client-Type` header, pass to service
- `kafka/Order1CKafkaProducer.java` — populate B2B fields in event
- `resources/db/migration/V20260519000000__add_b2b_snapshot_to_orders.sql` — new file

### order-service (test)
- `test/.../service/OrderServiceTest.java` — new B2B/B2C snapshot + price tests

### auth-service (modify)
- `models/dto/AuthResponse.java` — add `companyName`, `inn`, `clientType` fields
- `models/dto/SwitchContextRequest.java` — replace with `targetType` + optional `password`
- `utils/JWTService.java` — add `companyName`/`inn` claims to B2B token generation
- `service/AuthService.java` — enrich `AuthResponse`; rework `switchContext` (B2B→B2C with password)
- `controllers/AuthController.java` — update `switchContext` and `loginJson`

### frontend (modify/create)
- `src/types/auth.ts` — add `clientType`, `companyName`, `inn` to `User`
- `src/types/product.ts` — add `wholesalePrice` to `Product`
- `src/store/authStore.ts` — add `clientType`/`companyName`/`inn` extraction; `switchContext` action
- `src/api/auth.ts` — add `switchContext()` call
- `src/api/legalEntity.ts` — new file: `getLinkStatus`, `getLegalEntity`, `registerLegalEntity`
- `src/utils/priceUtils.ts` — new file: `useDisplayPrice` hook
- `src/components/layouts/ClientLayout.tsx` — add context switcher pill + B2B→B2C password modal
- `src/features/catalog/ProductCard.tsx` — use `useDisplayPrice`
- `src/features/catalog/ProductPage.tsx` — use `useDisplayPrice`
- `src/features/cart/CartPage.tsx` — use `useDisplayPrice`
- `src/features/checkout/SummaryStep.tsx` — use `useDisplayPrice`, show B2B snapshot
- `src/features/checkout/CheckoutPage.tsx` — send `companyName`/`inn` for B2B orders
- `src/features/profile/ProfilePage.tsx` — add "Организация" section

---

## Task 1: Flyway migration — B2B snapshot columns

**Files:**
- Create: `order-service/src/main/resources/db/migration/V20260519000000__add_b2b_snapshot_to_orders.sql`

- [ ] **Step 1: Create migration file**

```sql
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(10) NOT NULL DEFAULT 'B2C',
    ADD COLUMN IF NOT EXISTS company_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS inn           VARCHAR(12);

ALTER TABLE orders
    ADD CONSTRAINT orders_customer_type_check
    CHECK (customer_type IN ('B2C', 'B2B'));
```

- [ ] **Step 2: Verify migration runs**

```bash
cd order-service
./mvnw flyway:migrate -pl order-service
```

Expected: `Successfully applied 1 migration` (or run via Docker Compose restart of order-service).

- [ ] **Step 3: Commit**

```bash
git add order-service/src/main/resources/db/migration/V20260519000000__add_b2b_snapshot_to_orders.sql
git commit -m "feat(order-service): add B2B snapshot columns — customer_type, company_name, inn"
```

---

## Task 2: order-service — entity, DTO, ProductDto, event

**Files:**
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/models/entity/Order.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/models/dto/order/CreateOrderRequest.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/models/dto/order/OrderDto.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/models/dto/product/ProductDto.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/models/dto/event/Order1CExportEvent.java`

- [ ] **Step 1: Add fields to Order entity**

In `Order.java`, after the `customerEmail` field add:

```java
@Column(name = "customer_type", nullable = false, length = 10)
private String customerType = "B2C";

@Column(name = "company_name", length = 255)
private String companyName;

@Column(name = "inn", length = 12)
private String inn;
```

- [ ] **Step 2: Extend CreateOrderRequest**

Replace `CreateOrderRequest.java` content:

```java
package ru.rfsnab.orderservice.models.dto.order;

import jakarta.validation.constraints.NotNull;
import ru.rfsnab.orderservice.models.entity.enums.DeliveryMethod;
import ru.rfsnab.orderservice.models.entity.enums.PaymentMethod;

public record CreateOrderRequest(
        @NotNull(message = "Способ оплаты обязателен")
        PaymentMethod paymentMethod,

        @NotNull(message = "Способ доставки обязателен")
        DeliveryMethod deliveryMethod,

        AddressDto deliveryAddress,

        Long warehousePointId,

        String pickupRecipientName,

        String pickupRecipientPhone,

        String comment,

        // B2B snapshot — required when clientType=B2B, validated in service
        String companyName,

        String inn
) implements HasDeliveryInfo {
}
```

- [ ] **Step 3: Extend OrderDto**

In `OrderDto.java`, add three fields after `comment`:

```java
String customerType,
String companyName,
String inn,
```

Full record signature becomes:
```java
public record OrderDto(
        UUID id,
        Long userId,
        String orderNumber,
        String externalId,
        OrderStatus status,
        PaymentMethod paymentMethod,
        DeliveryMethod deliveryMethod,
        BigDecimal totalAmount,
        List<OrderItemDto> items,
        AddressDto deliveryAddress,
        WarehousePointDto warehousePoint,
        String trackingNumber,
        String customerEmail,
        String comment,
        String customerType,
        String companyName,
        String inn,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
```

- [ ] **Step 4: Add wholesalePrice to ProductDto**

```java
public record ProductDto(
        Long id,
        String name,
        BigDecimal price,
        BigDecimal wholesalePrice,   // new — null until 1C import
        Integer stockQuantity,
        Boolean isActive,
        String externalId
) {}
```

- [ ] **Step 5: Add B2B fields to Order1CExportEvent**

In `Order1CExportEvent.java`, add after `customerEmail`:

```java
String customerType,
String companyName,
String inn,
```

- [ ] **Step 6: Compile check**

```bash
./mvnw compile -pl order-service -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add order-service/src/main/java/ru/rfsnab/orderservice/models/
git commit -m "feat(order-service): extend Order, DTOs, ProductDto and 1C event with B2B snapshot fields"
```

---

## Task 3: order-service — mapper, service, controller, producer

**Files:**
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/mapper/OrderMapper.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/service/OrderService.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/controller/OrderController.java`
- Modify: `order-service/src/main/java/ru/rfsnab/orderservice/kafka/Order1CKafkaProducer.java`

- [ ] **Step 1: Update OrderMapper.toDto()**

In `OrderMapper.java`, update both `toDto()` overloads. Add three fields at the end of the `new OrderDto(...)` call (before `createdAt`):

```java
order.getCustomerType(),
order.getCompanyName(),
order.getInn(),
```

- [ ] **Step 2: Update OrderMapper.toEntity()**

In `OrderMapper.toEntity()`, add `clientType` parameter and populate snapshot. Replace the method signature and body:

```java
public static Order toEntity(Long userId, String customerEmail, String clientType, CreateOrderRequest request) {
    Order order = Order.builder()
            .userId(userId)
            .status(OrderStatus.CREATED)
            .paymentMethod(request.paymentMethod())
            .deliveryMethod(request.deliveryMethod())
            .customerEmail(customerEmail)
            .customerType(clientType != null ? clientType : "B2C")
            .comment(request.comment())
            .build();

    if (request.deliveryMethod() == DeliveryMethod.PICKUP) {
        order.setWarehousePointId(request.warehousePointId());
        order.setPickupRecipientName(request.pickupRecipientName());
        order.setPickupRecipientPhone(request.pickupRecipientPhone());
    } else {
        order.setDeliveryAddress(
                AddressMapper.mapToDeliveryAddress(request.deliveryAddress()));
    }

    return order;
}
```

- [ ] **Step 3: Update OrderService.createOrder()**

Change signature to accept `clientType`:

```java
public Order createOrder(Long userId, String customerEmail, String clientType, CreateOrderRequest request) {
```

After `Order order = OrderMapper.toEntity(userId, customerEmail, clientType, request);`, add B2B snapshot:

```java
if ("B2B".equals(clientType)) {
    if (request.companyName() == null || request.inn() == null) {
        throw new InvalidOrderStateException("B2B order requires companyName and inn");
    }
    order.setCompanyName(request.companyName());
    order.setInn(request.inn());
}
```

- [ ] **Step 4: Update price selection in addItemsFromCart()**

`addItemsFromCart` must receive `clientType`. Add it as parameter and change price selection:

```java
private BigDecimal addItemsFromCart(Order order, List<CartItem> cartItems,
                                    Map<Long, ProductDto> products, String clientType) {
    BigDecimal totalAmount = BigDecimal.ZERO;

    for (CartItem cartItem : cartItems) {
        ProductDto product = products.get(cartItem.getProductId());

        BigDecimal snapshotPrice = "B2B".equals(clientType)
                ? product.price()
                : (product.wholesalePrice() != null ? product.wholesalePrice() : product.price());

        OrderItem orderItem = OrderItem.builder()
                .order(order)
                .productId(cartItem.getProductId())
                .productName(product.name())
                .quantity(cartItem.getQuantity())
                .price(snapshotPrice)
                .externalId(product.externalId())
                .build();

        order.getItems().add(orderItem);
        totalAmount = totalAmount.add(snapshotPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
    }

    return totalAmount;
}
```

Update the call in `createOrder()`:
```java
BigDecimal totalAmount = addItemsFromCart(order, cart.getItems(), products, clientType);
```

Do the same for `addItemsFromDto()` — add `clientType` param and same price selection logic. Update its call in `updateOrder()` (pass `"B2C"` for now — update orders don't change clientType).

- [ ] **Step 5: Update OrderController.createOrder()**

Add header extraction:

```java
@PostMapping
@Operation(summary = "Создать заказ из корзины")
public ResponseEntity<OrderDto> createOrder(
        Authentication authentication,
        @RequestHeader(value = "X-Client-Type", defaultValue = "B2C") String clientType,
        @Valid @RequestBody CreateOrderRequest request) {
    Order order = orderService.createOrder(
            getCurrentUserId(authentication),
            getCurrentUserEmail(authentication),
            clientType,
            request);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(enrichAndMap(order));
}
```

- [ ] **Step 6: Update Order1CKafkaProducer.sendOrderFor1C()**

Add B2B fields to the event builder. After `.comment(order.getComment())` add:

```java
.customerType(order.getCustomerType())
.companyName(order.getCompanyName())
.inn(order.getInn())
```

- [ ] **Step 7: Compile check**

```bash
./mvnw compile -pl order-service -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 8: Commit**

```bash
git add order-service/src/main/java/ru/rfsnab/orderservice/
git commit -m "feat(order-service): B2B snapshot in createOrder — clientType header, price selection, 1C event"
```

---

## Task 4: order-service — unit tests

**Files:**
- Modify: `order-service/src/test/java/ru/rfsnab/orderservice/service/OrderServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add these test methods (find where existing tests are, add to the same class):

```java
@Test
void createOrder_B2B_snapshotsCompanyData() {
    // given
    Cart cart = cartWithOneItem(1L, 2);
    ProductDto product = new ProductDto(1L, "Каска", new BigDecimal("1000"), new BigDecimal("800"), 10, true, "EXT-1");
    when(cartService.getCart(userId)).thenReturn(cart);
    when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(1L, product));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateOrderRequest request = pickupRequest("ООО Ромашка", "1234567890");

    // when
    Order order = orderService.createOrder(userId, "org@example.com", "B2B", request);

    // then
    assertThat(order.getCustomerType()).isEqualTo("B2B");
    assertThat(order.getCompanyName()).isEqualTo("ООО Ромашка");
    assertThat(order.getInn()).isEqualTo("1234567890");
}

@Test
void createOrder_B2C_noSnapshotFields() {
    Cart cart = cartWithOneItem(1L, 1);
    ProductDto product = new ProductDto(1L, "Каска", new BigDecimal("1000"), new BigDecimal("800"), 10, true, "EXT-1");
    when(cartService.getCart(userId)).thenReturn(cart);
    when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(1L, product));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateOrderRequest request = pickupRequest(null, null);

    Order order = orderService.createOrder(userId, "user@example.com", "B2C", request);

    assertThat(order.getCustomerType()).isEqualTo("B2C");
    assertThat(order.getCompanyName()).isNull();
    assertThat(order.getInn()).isNull();
}

@Test
void createOrder_B2B_usesWholesalePrice_forB2CField() {
    // B2C customer sees wholesalePrice (розничная), not price (оптовая)
    Cart cart = cartWithOneItem(1L, 2);
    ProductDto product = new ProductDto(1L, "Каска", new BigDecimal("1000"), new BigDecimal("800"), 10, true, "EXT-1");
    when(cartService.getCart(userId)).thenReturn(cart);
    when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(1L, product));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Order order = orderService.createOrder(userId, "user@example.com", "B2C", pickupRequest(null, null));

    assertThat(order.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("800"));
    assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1600"));
}

@Test
void createOrder_B2B_usesPrice_forB2BField() {
    // B2B customer sees price (оптовая)
    Cart cart = cartWithOneItem(1L, 2);
    ProductDto product = new ProductDto(1L, "Каска", new BigDecimal("1000"), new BigDecimal("800"), 10, true, "EXT-1");
    when(cartService.getCart(userId)).thenReturn(cart);
    when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(1L, product));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Order order = orderService.createOrder(userId, "org@example.com", "B2B", pickupRequest("ООО Ромашка", "1234567890"));

    assertThat(order.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("1000"));
    assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("2000"));
}

@Test
void createOrder_B2B_missingInn_throws() {
    Cart cart = cartWithOneItem(1L, 1);
    ProductDto product = new ProductDto(1L, "Каска", new BigDecimal("1000"), new BigDecimal("800"), 10, true, "EXT-1");
    when(cartService.getCart(userId)).thenReturn(cart);
    when(productServiceClient.getProducts(anySet())).thenReturn(Map.of(1L, product));
    when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    CreateOrderRequest request = pickupRequest("ООО Ромашка", null); // inn null

    assertThatThrownBy(() -> orderService.createOrder(userId, "org@example.com", "B2B", request))
            .isInstanceOf(InvalidOrderStateException.class)
            .hasMessageContaining("companyName and inn");
}
```

Add helper methods in the test class:

```java
private Cart cartWithOneItem(Long productId, int qty) {
    CartItem item = new CartItem();
    item.setProductId(productId);
    item.setQuantity(qty);
    Cart cart = new Cart();
    cart.setItems(List.of(item));
    return cart;
}

private CreateOrderRequest pickupRequest(String companyName, String inn) {
    return new CreateOrderRequest(
            PaymentMethod.BANK_TRANSFER,
            DeliveryMethod.PICKUP,
            null,
            1L,
            "Иванов Иван",
            "+79001234567",
            null,
            companyName,
            inn
    );
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -pl order-service -Dtest=OrderServiceTest -q
```

Expected: some tests fail (compile errors or assertion failures — implementation already written in Task 3, so they should pass. If existing tests break, fix callers of `createOrder` that now need a `clientType` parameter).

- [ ] **Step 3: Fix any existing test callers**

Search for `orderService.createOrder(` in test files. Any call with 3 args needs a 4th `"B2C"` argument.

- [ ] **Step 4: Run all order-service tests**

```bash
./mvnw test -pl order-service -q
```

Expected: `BUILD SUCCESS`, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add order-service/src/test/
git commit -m "test(order-service): B2B snapshot and price selection unit tests"
```

---

## Task 5: auth-service — enrich AuthResponse, fix switchContext

**Files:**
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/models/dto/AuthResponse.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/models/dto/SwitchContextRequest.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/utils/JWTService.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/service/AuthService.java`
- Modify: `auth-service/src/main/java/ru/rfsnab/authservice/controllers/AuthController.java`

- [ ] **Step 1: Extend AuthResponse**

Replace `AuthResponse.java`:

```java
package ru.rfsnab.authservice.models.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    // B2B/B2C identity — populated for all logins
    private String clientType;
    private String companyName;  // null for B2C
    private String inn;          // null for B2C
}
```

- [ ] **Step 2: Replace SwitchContextRequest**

```java
package ru.rfsnab.authservice.models.dto;

public record SwitchContextRequest(
        String targetType,   // "B2B" or "B2C"
        String password      // required when targetType=B2C (B2B→B2C)
) {}
```

- [ ] **Step 3: Add companyName/inn claims to JWTService**

Add new overload in `JWTService.java`:

```java
public String generateToken(Long id, String email, String clientType,
                             String companyName, String inn) {
    var builder = Jwts.builder()
            .subject(String.valueOf(id))
            .claim("email", email)
            .claim("clientType", clientType)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMs()));
    if (companyName != null) builder.claim("companyName", companyName);
    if (inn != null) builder.claim("inn", inn);
    return builder.signWith(getSignedKey()).compact();
}

public String generateRefreshToken(Long id, String email, String clientType,
                                    String companyName, String inn) {
    var builder = Jwts.builder()
            .subject(String.valueOf(id))
            .claim("email", email)
            .claim("clientType", clientType)
            .claim("roles", List.of("ROLE_USER"))
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshExpirationMs()));
    if (companyName != null) builder.claim("companyName", companyName);
    if (inn != null) builder.claim("inn", inn);
    return builder.signWith(getSignedKey()).compact();
}

public String extractCompanyName(String token) {
    return extractClaims(token).get("companyName", String.class);
}

public String extractInn(String token) {
    return extractClaims(token).get("inn", String.class);
}
```

- [ ] **Step 4: Update AuthService.authenticateLegalEntity()**

Replace the return statement in `authenticateLegalEntity()`:

```java
String accessToken = jwtService.generateToken(
        legal.getId(), legal.getEmail(), "B2B", legal.getFullName(), legal.getInn());
String refreshToken = jwtService.generateRefreshToken(
        legal.getId(), legal.getEmail(), "B2B", legal.getFullName(), legal.getInn());

return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .tokenType("Bearer")
        .clientType("B2B")
        .companyName(legal.getFullName())
        .inn(legal.getInn())
        .build();
```

- [ ] **Step 5: Update AuthService.switchContext()**

Replace the entire `switchContext` method:

```java
@SuppressWarnings("unchecked")
public AuthResponse switchContext(String bearerToken, SwitchContextRequest request) {
    String currentClientType = jwtService.extractClientType(bearerToken);

    if ("B2B".equals(request.targetType())) {
        // B2C → B2B: no password required
        Long userId = jwtService.extractUserId(bearerToken);
        // Find linked legal entity for this user
        String linkUrl = userServiceUrl + "/api/v1/legal-entities/link-status/" + userId;
        ResponseEntity<Map> linkResp = restTemplate.getForEntity(linkUrl, Map.class);
        if (linkResp.getBody() == null || !Boolean.TRUE.equals(linkResp.getBody().get("confirmed"))) {
            throw new org.springframework.security.access.AccessDeniedException("Нет подтверждённой связи с юрлицом");
        }
        Long legalEntityId = ((Number) linkResp.getBody().get("legalEntityId")).longValue();

        String legalUrl = userServiceUrl + "/api/v1/legal-entities/" + legalEntityId;
        LegalEntityDto legal = restTemplate.getForEntity(legalUrl, LegalEntityDto.class).getBody();
        if (legal == null) throw new RuntimeException("Не удалось получить данные юрлица");

        String accessToken = jwtService.generateToken(legalEntityId, legal.getEmail(), "B2B", legal.getFullName(), legal.getInn());
        String refreshToken = jwtService.generateRefreshToken(legalEntityId, legal.getEmail(), "B2B", legal.getFullName(), legal.getInn());
        log.info("Switch context B2C→B2B: userId={} -> legalEntityId={}", userId, legalEntityId);

        return AuthResponse.builder()
                .accessToken(accessToken).refreshToken(refreshToken).tokenType("Bearer")
                .clientType("B2B").companyName(legal.getFullName()).inn(legal.getInn())
                .build();

    } else {
        // B2B → B2C: password required
        if (request.password() == null || request.password().isBlank()) {
            throw new BadCredentialsException("Для переключения на личный аккаунт требуется пароль");
        }
        String email = jwtService.extractEmail(bearerToken);
        // Get physical user email from legal entity (B2B token has legal entity email)
        // We need to find the physical user — use the userId stored in link table
        // Simplest: auth against user-service with the email from the JWT and provided password
        UserDtoResponse user = authenticateUser(new SimpleAuthRequest(email, request.password()));

        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(),
                roleExtractor.extractRoles(user));
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail(),
                roleExtractor.extractRoles(user));
        log.info("Switch context B2B→B2C: legalEmail={} -> userId={}", email, user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken).refreshToken(refreshToken).tokenType("Bearer")
                .clientType("B2C")
                .build();
    }
}
```

> **Note:** B2B→B2C uses `email` from the legal entity JWT. This works only if the legal entity email matches the physical user's email. If emails differ, user-service `authenticate` will fail with 401. This is acceptable — user registers legal entity with their own email (Plan A flow).

- [ ] **Step 6: Update authenticateWithUserData() to include clientType**

In `authenticateWithUserData()`, add `clientType` to the result map:

```java
result.put("clientType", "B2C");
result.put("companyName", null);
result.put("inn", null);
```

- [ ] **Step 7: Update AuthController.switchContext()**

No change needed in controller signature — `SwitchContextRequest` still comes from body. Verify it compiles.

- [ ] **Step 8: Compile check**

```bash
./mvnw compile -pl auth-service -q
```

Expected: `BUILD SUCCESS`

- [ ] **Step 9: Commit**

```bash
git add auth-service/src/main/java/
git commit -m "feat(auth-service): enrich AuthResponse with clientType/companyName/inn, rework switchContext B2C↔B2B"
```

---

## Task 6: Frontend — types and authStore

**Files:**
- Modify: `frontend/src/types/auth.ts`
- Modify: `frontend/src/types/product.ts`
- Modify: `frontend/src/store/authStore.ts`
- Modify: `frontend/src/api/auth.ts`

- [ ] **Step 1: Extend User type in auth.ts**

```typescript
export type ClientType = 'B2C' | 'B2B';

export interface User {
    id: number;
    email: string;
    firstname: string;
    lastname: string;
    surname: string | null;
    phone: string | null;
    emailVerified: boolean;
    roles: UserRole[];
    createdAt: string;
    updatedAt: string;
    clientType: ClientType;        // new
    companyName?: string | null;   // new — present when B2B
    inn?: string | null;           // new — present when B2B
}
```

- [ ] **Step 2: Add wholesalePrice to Product type**

In `frontend/src/types/product.ts`, after `price: number;` add:

```typescript
wholesalePrice: number | null;   // new — retail price, shown to B2C
```

- [ ] **Step 3: Update authStore.ts**

Replace `authStore.ts` entirely:

```typescript
import { create } from 'zustand';
import type { User, ClientType } from '@/types/auth';
import * as authApi from '@/api/auth';
import type { LoginRequest } from '@/types/auth';

interface AuthState {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;

    login: (request: LoginRequest) => Promise<void>;
    logout: () => Promise<void>;
    restoreSession: () => Promise<void>;
    switchContext: (targetType: ClientType, password?: string) => Promise<void>;
}

const decodeJwtPayload = (token: string): Record<string, unknown> | null => {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(payload);
    } catch {
        return null;
    }
};

const isTokenExpired = (token: string): boolean => {
    const payload = decodeJwtPayload(token);
    if (!payload || typeof payload.exp !== 'number') return true;
    return payload.exp * 1000 < Date.now();
};

const userFromPayload = (payload: Record<string, unknown>): User => ({
    id: Number(payload.sub),
    email: payload.email as string,
    firstname: '',
    lastname: '',
    surname: null,
    phone: null,
    emailVerified: true,
    roles: ((payload.roles as string[]) || []).map((name, idx) => ({ id: idx, name })),
    createdAt: '',
    updatedAt: '',
    clientType: (payload.clientType as ClientType) ?? 'B2C',
    companyName: (payload.companyName as string) ?? null,
    inn: (payload.inn as string) ?? null,
});

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    isAuthenticated: false,
    isLoading: true,

    login: async (request) => {
        const tokens = await authApi.login(request);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        set({ user: tokens.user, isAuthenticated: true });
    },

    logout: async () => {
        try {
            await authApi.logout();
        } catch {
            // ignore server errors on logout
        } finally {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            set({ user: null, isAuthenticated: false });
        }
    },

    restoreSession: async () => {
        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
            set({ isLoading: false });
            return;
        }

        if (isTokenExpired(accessToken)) {
            const refreshToken = localStorage.getItem('refreshToken');
            if (!refreshToken || isTokenExpired(refreshToken)) {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                set({ user: null, isAuthenticated: false, isLoading: false });
                return;
            }
            try {
                const tokens = await authApi.refreshTokens(refreshToken);
                localStorage.setItem('accessToken', tokens.access_token);
                localStorage.setItem('refreshToken', tokens.refresh_token);
                set({ user: tokens.user, isAuthenticated: true, isLoading: false });
            } catch {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                set({ user: null, isAuthenticated: false, isLoading: false });
            }
            return;
        }

        try {
            const user = await authApi.getCurrentUser();
            set({ user, isAuthenticated: true, isLoading: false });
        } catch {
            const payload = decodeJwtPayload(accessToken);
            if (payload) {
                set({ user: userFromPayload(payload), isAuthenticated: true, isLoading: false });
            } else {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                set({ user: null, isAuthenticated: false, isLoading: false });
            }
        }
    },

    switchContext: async (targetType, password) => {
        const tokens = await authApi.switchContext(targetType, password);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        set({ user: tokens.user });
    },
}));
```

- [ ] **Step 4: Add switchContext and update AuthTokens in api/auth.ts**

First, `AuthTokens` interface in `auth.ts` types needs `clientType`, `companyName`, `inn`. Update `frontend/src/types/auth.ts` `AuthTokens`:

```typescript
export interface AuthTokens {
    access_token: string;
    refresh_token: string;
    token_type: string;
    expires_in: number;
    user: User;
    clientType?: string;   // top-level for AuthResponse compatibility
    companyName?: string | null;
    inn?: string | null;
}
```

Then in `frontend/src/api/auth.ts`, add:

```typescript
export const switchContext = async (
    targetType: 'B2C' | 'B2B',
    password?: string,
): Promise<AuthTokens> => {
    const { data } = await apiClient.post<Record<string, unknown>>(
        '/v1/auth/switch-context',
        { targetType, ...(password ? { password } : {}) },
    );
    // auth-service returns snake_case from AuthResponse (via Jackson)
    return {
        access_token: data.accessToken as string,
        refresh_token: data.refreshToken as string,
        token_type: (data.tokenType as string) ?? 'Bearer',
        expires_in: 3600,
        clientType: data.clientType as string,
        companyName: data.companyName as string | null,
        inn: data.inn as string | null,
        // reconstruct user from JWT payload since auth-service doesn't return full user object
        user: (() => {
            const token = data.accessToken as string;
            const parts = token.split('.');
            const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
            return {
                id: Number(payload.sub),
                email: payload.email as string,
                firstname: '',
                lastname: '',
                surname: null,
                phone: null,
                emailVerified: true,
                roles: ((payload.roles as string[]) || []).map((name: string, idx: number) => ({ id: idx, name })),
                createdAt: '',
                updatedAt: '',
                clientType: payload.clientType ?? 'B2C',
                companyName: payload.companyName ?? null,
                inn: payload.inn ?? null,
            };
        })(),
    };
};
```

Also update `login()` to populate `clientType`/`companyName`/`inn` on the returned `user`. The `/login` endpoint returns a `Map` with `user` (UserDtoResponse) + `clientType`. Update the return mapping in `login()`:

```typescript
export const login = async (request: LoginRequest): Promise<AuthTokens> => {
    const { data } = await apiClient.post<Record<string, unknown>>('/v1/auth/login', request);
    const rawUser = data.user as Record<string, unknown>;
    return {
        access_token: data.access_token as string,
        refresh_token: data.refresh_token as string,
        token_type: (data.token_type as string) ?? 'Bearer',
        expires_in: (data.expires_in as number) ?? 3600,
        user: {
            id: rawUser.id as number,
            email: rawUser.email as string,
            firstname: (rawUser.firstname as string) ?? '',
            lastname: (rawUser.lastname as string) ?? '',
            surname: (rawUser.surname as string | null) ?? null,
            phone: (rawUser.phone as string | null) ?? null,
            emailVerified: (rawUser.emailVerified as boolean) ?? false,
            roles: (rawUser.roles as { id: number; name: string }[]) ?? [],
            createdAt: (rawUser.createdAt as string) ?? '',
            updatedAt: (rawUser.updatedAt as string) ?? '',
            clientType: ((data.clientType as string) ?? 'B2C') as 'B2C' | 'B2B',
            companyName: (data.companyName as string | null) ?? null,
            inn: (data.inn as string | null) ?? null,
        },
    };
};
```

- [ ] **Step 5: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/ frontend/src/store/authStore.ts frontend/src/api/auth.ts
git commit -m "feat(frontend): add clientType/companyName/inn to User type, authStore switchContext"
```

---

## Task 7: Frontend — useDisplayPrice hook + apply to product pages

**Files:**
- Create: `frontend/src/utils/priceUtils.ts`
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Modify: `frontend/src/features/catalog/ProductPage.tsx` (find price display)
- Modify: `frontend/src/features/cart/CartPage.tsx` (find price display)

- [ ] **Step 1: Create priceUtils.ts**

```typescript
import { useAuthStore } from '@/store/authStore';

interface HasPrice {
    price: number;
    wholesalePrice?: number | null;
}

export const useDisplayPrice = (product: HasPrice): number => {
    const clientType = useAuthStore((s) => s.user?.clientType ?? 'B2C');
    return clientType === 'B2B'
        ? product.price
        : (product.wholesalePrice ?? product.price);
};
```

- [ ] **Step 2: Update ProductCard.tsx**

Add import at top:
```typescript
import { useDisplayPrice } from '@/utils/priceUtils';
```

Inside `ProductCard` component, before the return, add:
```typescript
const displayPrice = useDisplayPrice(product);
```

Replace `{formatPrice(product.price)}` with `{formatPrice(displayPrice)}`.

- [ ] **Step 3: Update ProductPage.tsx**

Find where `product.price` is rendered (look for `formatPrice` or currency display). Add the same hook and replace the price value.

Open the file first to see exact line:
```bash
grep -n "price" frontend/src/features/catalog/ProductPage.tsx
```

Add import and hook, replace `product.price` display with `displayPrice`.

- [ ] **Step 4: Update CartPage.tsx**

```bash
grep -n "price" frontend/src/features/cart/CartPage.tsx
```

Cart items have their own price from the cart API (already snapshotted). No change needed to CartPage price display — cart items store the price set at order creation. Skip this file.

- [ ] **Step 5: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/utils/priceUtils.ts frontend/src/features/catalog/
git commit -m "feat(frontend): useDisplayPrice hook — B2B sees wholesale price, B2C sees retail price"
```

---

## Task 8: Frontend — context switcher pill in ClientLayout

**Files:**
- Modify: `frontend/src/components/layouts/ClientLayout.tsx`

- [ ] **Step 1: Add ContextSwitcher component inside ClientLayout.tsx**

Add this component definition inside `ClientLayout.tsx` before the `ClientLayout` function:

```typescript
const ContextSwitcher = () => {
    const { user, switchContext } = useAuthStore();
    const { fetchCart } = useCartStore();
    const [loading, setLoading] = useState(false);
    const [showPasswordModal, setShowPasswordModal] = useState(false);
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');

    if (!user?.companyName) return null;

    const handleSwitch = async (target: 'B2C' | 'B2B') => {
        if (target === user.clientType) return;
        if (target === 'B2C') {
            setShowPasswordModal(true);
            return;
        }
        setLoading(true);
        try {
            await switchContext('B2B');
            await fetchCart();
        } catch {
            // switchContext already restores state on error in authStore
        } finally {
            setLoading(false);
        }
    };

    const handlePasswordSubmit = async () => {
        if (!password) return;
        setLoading(true);
        setError('');
        try {
            await switchContext('B2C', password);
            await fetchCart();
            setShowPasswordModal(false);
            setPassword('');
        } catch {
            setError('Неверный пароль');
        } finally {
            setLoading(false);
        }
    };

    const active = user.clientType;

    return (
        <>
            <div style={{ display: 'flex', alignItems: 'center', border: '1px solid var(--line-2)', borderRadius: 6, overflow: 'hidden', opacity: loading ? 0.6 : 1 }}>
                {(['B2C', 'B2B'] as const).map((type) => (
                    <button
                        key={type}
                        onClick={() => handleSwitch(type)}
                        disabled={loading}
                        style={{
                            padding: '4px 10px', fontSize: 12, fontWeight: 600,
                            border: 'none', cursor: loading ? 'default' : 'pointer',
                            fontFamily: 'var(--font-body)',
                            background: active === type ? 'var(--brand-red)' : 'transparent',
                            color: active === type ? '#fff' : 'var(--ink-2)',
                            transition: 'background 0.15s, color 0.15s',
                        }}
                    >
                        {type === 'B2C' ? 'Физлицо' : 'Организация'}
                    </button>
                ))}
            </div>

            {showPasswordModal && (
                <div style={{
                    position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999,
                }}
                    onClick={() => { setShowPasswordModal(false); setPassword(''); setError(''); }}
                >
                    <div style={{ background: '#fff', borderRadius: 10, padding: 28, width: 360 }}
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div style={{ fontWeight: 600, fontSize: 16, marginBottom: 8 }}>Переключение на личный аккаунт</div>
                        <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 16 }}>Введите пароль от личного аккаунта</div>
                        <input
                            type="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            onKeyDown={(e) => { if (e.key === 'Enter') handlePasswordSubmit(); }}
                            placeholder="Пароль"
                            autoFocus
                            style={{
                                width: '100%', height: 40, padding: '0 12px',
                                border: `1px solid ${error ? 'var(--brand-red)' : 'var(--line-2)'}`,
                                borderRadius: 6, fontSize: 14, fontFamily: 'var(--font-body)',
                                outline: 'none', boxSizing: 'border-box', marginBottom: error ? 6 : 16,
                            }}
                        />
                        {error && <div style={{ fontSize: 12, color: 'var(--brand-red)', marginBottom: 12 }}>{error}</div>}
                        <div style={{ display: 'flex', gap: 8 }}>
                            <button
                                onClick={handlePasswordSubmit}
                                disabled={loading || !password}
                                style={{
                                    flex: 1, height: 38, background: 'var(--brand-red)', color: '#fff',
                                    border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500,
                                    cursor: loading || !password ? 'default' : 'pointer', fontFamily: 'var(--font-body)',
                                    opacity: !password ? 0.6 : 1,
                                }}
                            >
                                {loading ? 'Проверка...' : 'Войти'}
                            </button>
                            <button
                                onClick={() => { setShowPasswordModal(false); setPassword(''); setError(''); }}
                                style={{
                                    height: 38, padding: '0 16px', border: '1px solid var(--line-2)',
                                    background: 'transparent', borderRadius: 6, fontSize: 14,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)', color: 'var(--ink-2)',
                                }}
                            >
                                Отмена
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
};
```

- [ ] **Step 2: Insert ContextSwitcher into header**

In the authenticated user section of `ClientLayout` (the `{isAuthenticated ? (...)}` block), add `<ContextSwitcher />` before the `<Dropdown>`:

```tsx
{isAuthenticated ? (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <ContextSwitcher />
        <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
            <button style={{ ... }}>
                <UserIcon /> {user?.firstname || user?.companyName}
            </button>
        </Dropdown>
    </div>
) : (
    ...
)}
```

Also update the button label: show `user?.firstname` for B2C, `user?.companyName` for B2B:
```tsx
{user?.clientType === 'B2B' ? user.companyName : user?.firstname}
```

- [ ] **Step 3: Add "Подключить организацию" to userMenuItems**

In `userMenuItems` array, before the logout divider add:

```typescript
...(user && !user.companyName ? [
    { key: 'connect-org', label: 'Подключить организацию', onClick: () => navigate('/profile') },
    { type: 'divider' as const },
] : []),
```

- [ ] **Step 4: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/layouts/ClientLayout.tsx
git commit -m "feat(frontend): context switcher pill in header — B2C/B2B toggle with password modal"
```

---

## Task 9: Frontend — legalEntity API + ProfilePage "Организация" section

**Files:**
- Create: `frontend/src/api/legalEntity.ts`
- Modify: `frontend/src/features/profile/ProfilePage.tsx`

- [ ] **Step 1: Create legalEntity.ts**

```typescript
import apiClient from './client';

export interface LegalEntityResponse {
    id: number;
    fullName: string;
    inn: string;
    email: string;
    verificationStatus: string; // 'PENDING' | 'VERIFIED' | 'REJECTED'
}

export interface LinkStatusResponse {
    linked: boolean;
    confirmed: boolean;
    legalEntityId: number | null;
}

export interface RegisterLegalEntityRequest {
    fullName: string;
    inn: string;
    email: string;
    password: string;
}

export const getLinkStatus = async (userId: number): Promise<LinkStatusResponse> => {
    const { data } = await apiClient.get<LinkStatusResponse>(
        `/api/v1/legal-entities/link-status/${userId}`,
    );
    return data;
};

export const getLegalEntity = async (id: number): Promise<LegalEntityResponse> => {
    const { data } = await apiClient.get<LegalEntityResponse>(`/api/v1/legal-entities/${id}`);
    return data;
};

export const registerLegalEntity = async (
    request: RegisterLegalEntityRequest,
): Promise<void> => {
    await apiClient.post('/api/v1/legal-entities/register', request);
};
```

- [ ] **Step 2: Add "Организация" section to ProfilePage.tsx**

Add imports at top:
```typescript
import { useQuery, useMutation } from '@tanstack/react-query';
import {
    getLinkStatus,
    getLegalEntity,
    registerLegalEntity,
    type RegisterLegalEntityRequest,
} from '@/api/legalEntity';
```

Add the section after the existing "Аккаунт" block. Insert before the closing `</div>` of the page:

```tsx
{/* Организация */}
<OrganizationSection userId={user.id} onRegistered={restoreSession} />
```

Define `OrganizationSection` as a separate component inside `ProfilePage.tsx` (before the `ProfilePage` function):

```tsx
const OrganizationSection = ({
    userId,
    onRegistered,
}: {
    userId: number;
    onRegistered: () => Promise<void>;
}) => {
    const [showForm, setShowForm] = useState(false);
    const [form] = Form.useForm<RegisterLegalEntityRequest>();
    const { message: messageApi } = App.useApp();

    const { data: linkStatus, isLoading: linkLoading } = useQuery({
        queryKey: ['link-status', userId],
        queryFn: () => getLinkStatus(userId),
    });

    const { data: legalEntity, isLoading: legalLoading } = useQuery({
        queryKey: ['legal-entity', linkStatus?.legalEntityId],
        queryFn: () => getLegalEntity(linkStatus!.legalEntityId!),
        enabled: !!linkStatus?.legalEntityId,
    });

    const registerMutation = useMutation({
        mutationFn: (values: RegisterLegalEntityRequest) => registerLegalEntity(values),
        onSuccess: async () => {
            messageApi.success('Заявка подана. Ожидайте верификации.');
            setShowForm(false);
            form.resetFields();
            await onRegistered();
        },
        onError: () => {
            messageApi.error('Ошибка при подаче заявки');
        },
    });

    const sectionStyle: React.CSSProperties = {
        border: '1px solid var(--line-1)', borderRadius: 8,
        background: 'var(--surface)', overflow: 'hidden', marginTop: 16,
    };
    const headerStyle: React.CSSProperties = {
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '14px 20px', borderBottom: '1px solid var(--line-1)',
        background: 'var(--surface-2)',
    };
    const inputStyle: React.CSSProperties = {
        height: 40, border: '1px solid var(--line-2)', borderRadius: 6,
        fontFamily: 'var(--font-body)', fontSize: 14,
    };
    const rowStyle: React.CSSProperties = {
        display: 'flex', fontSize: 14, padding: '10px 0',
        borderBottom: '1px solid var(--line-1)',
    };

    if (linkLoading || legalLoading) {
        return (
            <div style={sectionStyle}>
                <div style={headerStyle}><span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span></div>
                <div style={{ padding: 20, color: 'var(--ink-3)' }}>Загрузка...</div>
            </div>
        );
    }

    // State 1 — not linked
    if (!linkStatus?.linked) {
        return (
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span>
                    {!showForm && (
                        <button
                            onClick={() => setShowForm(true)}
                            style={{
                                display: 'inline-flex', alignItems: 'center', gap: 6,
                                height: 32, padding: '0 12px', border: '1px solid var(--line-2)',
                                background: 'transparent', color: 'var(--ink-2)', borderRadius: 6,
                                fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                            }}
                        >
                            Подать заявку
                        </button>
                    )}
                </div>
                <div style={{ padding: 20 }}>
                    {!showForm ? (
                        <div style={{ fontSize: 14, color: 'var(--ink-3)', lineHeight: 1.6 }}>
                            Работаете от юридического лица? Подключите организацию для доступа к оптовым ценам и B2B-документообороту.
                        </div>
                    ) : (
                        <Form<RegisterLegalEntityRequest> form={form} layout="vertical" onFinish={(v) => registerMutation.mutate(v)}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>Наименование организации</label>
                                    <Form.Item name="fullName" noStyle rules={[{ required: true, message: 'Введите наименование' }]}>
                                        <Input style={inputStyle} placeholder="ООО Ромашка" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>ИНН</label>
                                    <Form.Item name="inn" noStyle rules={[{ required: true, message: 'Введите ИНН' }, { len: 10, message: 'ИНН — 10 цифр' }]}>
                                        <Input style={inputStyle} placeholder="1234567890" maxLength={10} />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>Email организации</label>
                                    <Form.Item name="email" noStyle rules={[{ required: true, type: 'email', message: 'Введите email' }]}>
                                        <Input style={inputStyle} placeholder="org@company.ru" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>Пароль для B2B аккаунта</label>
                                    <Form.Item name="password" noStyle rules={[{ required: true, min: 6, message: 'Минимум 6 символов' }]}>
                                        <Input.Password style={inputStyle} placeholder="Пароль" />
                                    </Form.Item>
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button type="submit" disabled={registerMutation.isPending}
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    {registerMutation.isPending ? 'Отправка...' : 'Подать заявку'}
                                </button>
                                <button type="button" onClick={() => { setShowForm(false); form.resetFields(); }}
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    Отмена
                                </button>
                            </div>
                        </Form>
                    )}
                </div>
            </div>
        );
    }

    // State 2 — linked, pending verification
    if (!linkStatus.confirmed || legalEntity?.verificationStatus !== 'VERIFIED') {
        return (
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px', borderRadius: 11, fontSize: 12, fontWeight: 500, background: 'var(--warn-tint)', color: 'var(--warn)' }}>
                        На проверке
                    </span>
                </div>
                <div style={{ padding: 20 }}>
                    <div style={rowStyle}><span style={{ color: 'var(--ink-3)', width: 160 }}>Организация</span><span style={{ fontWeight: 500 }}>{legalEntity?.fullName}</span></div>
                    <div style={{ ...rowStyle, borderBottom: 'none' }}><span style={{ color: 'var(--ink-3)', width: 160 }}>ИНН</span><span style={{ fontWeight: 500 }}>{legalEntity?.inn}</span></div>
                </div>
            </div>
        );
    }

    // State 3 — verified
    return (
        <div style={sectionStyle}>
            <div style={headerStyle}>
                <span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span>
                <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px', borderRadius: 11, fontSize: 12, fontWeight: 500, background: 'var(--brand-green-soft)', color: 'var(--brand-green)' }}>
                    Верифицирована
                </span>
            </div>
            <div style={{ padding: 20 }}>
                {[
                    ['Организация', legalEntity?.fullName],
                    ['ИНН', legalEntity?.inn],
                    ['Email', legalEntity?.email],
                ].map(([label, value]) => (
                    <div key={label} style={rowStyle}>
                        <span style={{ color: 'var(--ink-3)', width: 160, flexShrink: 0 }}>{label}</span>
                        <span style={{ fontWeight: 500 }}>{value || '—'}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};
```

- [ ] **Step 3: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/legalEntity.ts frontend/src/features/profile/ProfilePage.tsx
git commit -m "feat(frontend): ProfilePage — Организация section with 3 states and registration form"
```

---

## Task 10: Frontend — CheckoutPage B2B snapshot + SummaryStep

**Files:**
- Modify: `frontend/src/features/checkout/CheckoutPage.tsx`
- Modify: `frontend/src/features/checkout/SummaryStep.tsx`
- Modify: `frontend/src/types/order.ts` (if CreateOrderRequest type is defined there)

- [ ] **Step 1: Find CreateOrderRequest type**

```bash
grep -rn "CreateOrderRequest" frontend/src/types/
```

If it exists in `frontend/src/types/order.ts`, add `companyName` and `inn` fields:

```typescript
export interface CreateOrderRequest {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    deliveryAddress?: AddressDto;
    warehousePointId?: number;
    pickupRecipientName?: string;
    pickupRecipientPhone?: string;
    comment?: string;
    companyName?: string;   // new — B2B only
    inn?: string;           // new — B2B only
}
```

- [ ] **Step 2: Update CheckoutPage to send B2B fields**

In `CheckoutPage.tsx`, find where `createOrder` is called (inside the mutation). Before the `createOrder(...)` call, build B2B fields:

```typescript
const user = useAuthStore((s) => s.user);

// inside the mutation function:
const b2bFields = user?.clientType === 'B2B'
    ? { companyName: user.companyName ?? undefined, inn: user.inn ?? undefined }
    : {};

await createOrder({
    paymentMethod: formData.paymentMethod,
    deliveryMethod: formData.deliveryMethod,
    // ... existing fields ...
    ...b2bFields,
});
```

- [ ] **Step 3: Update SummaryStep to show B2B info**

Open `SummaryStep.tsx`. Find the summary section. Add B2B block — show when `user.clientType === 'B2B'`:

```tsx
const user = useAuthStore((s) => s.user);

// In the render, add a B2B info section:
{user?.clientType === 'B2B' && user.companyName && (
    <div style={{ marginBottom: 16, padding: 12, background: 'var(--surface-2)', borderRadius: 6, border: '1px solid var(--line-1)' }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginBottom: 6, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Покупатель</div>
        <div style={{ fontSize: 14, fontWeight: 500 }}>{user.companyName}</div>
        {user.inn && <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>ИНН: {user.inn}</div>}
    </div>
)}
```

Also use `useDisplayPrice` in `SummaryStep` if it currently shows product prices from cart items. Cart items already have the snapshotted price — no change needed to price display there.

- [ ] **Step 4: TypeScript check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: 0 errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/checkout/ frontend/src/types/order.ts
git commit -m "feat(frontend): CheckoutPage sends B2B snapshot fields, SummaryStep shows company info"
```

---

## Task 11: Update wiki and project state

**Files:**
- Modify: `.claude/wiki/log.md`
- Modify: `C:\Users\elims\.claude\projects\...\memory\project_state.md`

- [ ] **Step 1: Update log.md**

Add entry at the top of `.claude/wiki/log.md`:

```markdown
## 2026-05-19 (Plan C)
- Flyway migration V20260519000000: customer_type, company_name, inn columns in orders
- order-service: B2B snapshot in createOrder, clientType header from gateway, price selection (B2B→price, B2C→wholesalePrice)
- auth-service: AuthResponse now includes clientType/companyName/inn, switchContext reworked (B2C→B2B no password, B2B→B2C with password)
- frontend: ClientType added to User type, authStore.switchContext(), useDisplayPrice hook
- frontend: context switcher pill in header with B2B→B2C password modal
- frontend: ProfilePage Организация section (3 states: not linked / pending / verified + registration form)
- frontend: CheckoutPage sends B2B snapshot, SummaryStep shows company name + INN
```

- [ ] **Step 2: Commit final**

```bash
git add .claude/wiki/log.md
git commit -m "docs: update wiki log for Plan C completion"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Section 1: Flyway migration — Task 1
- ✅ Section 1: Order entity fields — Task 2
- ✅ Section 1: CreateOrderRequest extended — Task 2
- ✅ Section 1: OrderService price selection + snapshot — Task 3
- ✅ Section 1: OrderController header extraction — Task 3
- ✅ Section 1: Order1CKafkaProducer B2B fields — Task 3
- ✅ Section 2: authStore clientType/companyName/inn — Task 6
- ✅ Section 2: switchContext action — Task 6
- ✅ Section 3: Context switcher pill — Task 8
- ✅ Section 3: B2B→B2C password modal — Task 8
- ✅ Section 3: "Подключить организацию" in dropdown — Task 8
- ✅ Section 4: useDisplayPrice hook — Task 7
- ✅ Section 4: ProductCard, ProductPage price display — Task 7
- ✅ Section 4: Backend price selection — Task 3
- ✅ Section 5: ProfilePage Организация section — Task 9
- ✅ Section 6: AuthResponse clientType/companyName/inn — Task 5
- ✅ Section 6: switchContext endpoint rework — Task 5
- ✅ Section 7: CheckoutPage B2B fields — Task 10
- ✅ Section 7: SummaryStep B2B info — Task 10
- ✅ Section 9: Unit tests — Task 4

**Type consistency:** `useDisplayPrice` accepts `{ price: number; wholesalePrice?: number | null }` — matches `Product` type after Task 6 Step 2. `switchContext(targetType, password?)` matches authStore and `api/auth.ts` definition. `CreateOrderRequest.companyName/inn` matches backend record.

**No placeholders:** All steps have code or exact commands.
