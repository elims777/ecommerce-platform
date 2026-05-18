# Project Structure — RFSnab Ecommerce Platform

Last updated: 2026-05-18

## Services Overview

| Service | Port | DB Port | Purpose |
|---|---|---|---|
| gateway-service | 8080 | — | Spring Cloud Gateway, JWT filter, Redis rate limiting |
| auth-service | 9000 | — | JWT + Yandex OAuth2, login, registration |
| user-service | 8081 | 5433 | Users, legal entities, roles |
| product-service | 8083 | 5432 | Products, categories, Yandex S3, images |
| order-service | 8084 | 5435 | Cart (Redis), orders, warehouse points |
| notification-service | 8082 | 5434 | Email via Kafka, verification tokens |
| integration-service | — | — | 1С Fresh УНФ, CommerceML 2.08 |
| frontend | 5173 | — | React + TypeScript + Vite |

---

## auth-service

```
ru.rfsnab.authservice/
├── AuthServiceApplication.java
├── aspect/
│   └── LoggingAspect.java
├── configuration/
│   ├── AppConfig.java
│   ├── JWTProperties.java          — jwt.secret, expiration-ms, refresh-expiration-ms
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java         — all /api/**, /v1/** public; OAuth2 chain separate
├── controllers/
│   ├── AuthController.java          — POST /v1/auth/login, /refresh, /me, /logout
│   ├── AuthPageController.java
│   ├── EmailVerificationController.java
│   ├── OAuth2RegisterController.java
│   └── RegistrationController.java  — POST /api/v1/register
├── exceptions/
│   └── GlobalExceptionHandler.java
├── models/dto/
│   ├── AuthResponse.java
│   ├── ErrorResponse.java
│   ├── RoleEntity.java
│   ├── SimpleAuthRequest.java       — email + password
│   └── VerificationResponse.java
├── service/
│   ├── AuthService.java             — calls user-service /v1/users/authenticate
│   └── RemoteUserDetailsService.java
└── utils/
    ├── JWTService.java              — generateToken(userId, email, roles): sub=userId, claims: email, roles
    ├── OAuth2LoginSuccessHandler.java
    └── RoleExtractor.java
```

**JWT claims (current):** `sub=userId`, `email`, `roles`, `iat`, `exp`

**Kafka:** not used

---

## user-service

```
ru.rfsnab.userservice/
├── UserServiceApplication.java
├── aspect/LoggingAspect.java
├── configuration/UserServiceConfig.java
├── controllers/
│   ├── AdminController.java
│   ├── LegalEntityAdminController.java   — GET/POST /api/v1/admin/legal-entities
│   ├── LegalEntityController.java        — POST /api/v1/legal-entities/register, confirm-email, addresses, bank-accounts
│   ├── UserController.java
│   └── UserLegalEntityController.java    — POST /api/v1/users/me/legal-entities/link
├── exceptions/
│   ├── GlobalExceptionHandler.java
│   ├── LegalEntityAlreadyExistsException.java
│   ├── LegalEntityNotFoundException.java
│   ├── LegalEntityNotVerifiedException.java
│   └── UserAlreadyExistsException.java
├── mappers/
│   ├── LegalEntityMapper.java
│   ├── RoleMapper.java
│   └── UserMapper.java
├── models/
│   ├── LegalEntity.java                  — VerificationStatus: PENDING/VERIFIED/REJECTED
│   ├── LegalEntityAddress.java
│   ├── LegalEntityBankAccount.java
│   ├── RoleEntity.java
│   ├── UserEntity.java
│   ├── UserLegalEntity.java
│   ├── UserLegalEntityId.java
│   ├── dto/legal/
│   │   ├── BankAccountDto.java
│   │   ├── LegalEntityAddressDto.java
│   │   ├── LegalEntityDto.java
│   │   ├── RegisterLegalEntityRequest.java
│   │   ├── SaveBankAccountRequest.java
│   │   └── SaveLegalEntityAddressRequest.java
│   ├── dto/
│   │   ├── ErrorResponse.java
│   │   ├── RoleDto.java
│   │   ├── SimpleAuthRequest.java
│   │   └── UserDtoResponse.java
│   ├── enums/
│   │   ├── LinkStatus.java               — PENDING, CONFIRMED
│   │   └── VerificationStatus.java       — PENDING, VERIFIED, REJECTED
│   └── kafka/
│       ├── LegalEntityEvent.java
│       └── UserEvent.java
├── repository/
│   ├── BankAccountRepository.java
│   ├── LegalEntityAddressRepository.java
│   ├── LegalEntityRepository.java
│   ├── RoleRepository.java
│   ├── UserLegalEntityRepository.java
│   └── UserRepository.java               — existsByEmail(String)
└── services/
    ├── KafkaProducerService.java          — topic: user-events
    ├── LegalEntityKafkaProducerService.java — topic: legal-entity-events
    ├── LegalEntityService.java
    ├── RoleService.java
    └── UserService.java
```

**Kafka topics produced:** `user-events`, `legal-entity-events`

**DB migrations:**
- `V20251119120000__create_users_table.sql`
- `V20260517180000__add_legal_entities.sql` — legal_entities, user_legal_entities, legal_entity_bank_accounts, legal_entity_addresses

---

## product-service

```
ru.rfsnab.productservice/
├── ProductServiceApplication.java
├── configuration/
│   ├── JwtProperties.java
│   ├── SecurityConfig.java          — GET public; POST/PUT/DELETE → ROLE_ADMIN/MANAGER
│   └── YandexStorageConfig.java
├── controller/
│   ├── CategoryController.java
│   ├── ProductAttributeController.java
│   ├── ProductController.java       — CRUD + stock, search, slug, category
│   ├── ProductImageController.java
│   └── ProductVideoController.java
├── dto/
│   ├── CategoryTreeDTO.java
│   ├── ErrorResponse.java
│   ├── ProductAttributeRequest/Response.java
│   ├── ProductImageResponse.java
│   ├── ProductRequest.java          — name, description, price, stockQuantity, ...
│   ├── ProductResponse.java         — all fields incl. price (NO wholesalePrice yet)
│   ├── ProductVideoRequest/Response.java
│   └── ...
├── exception/
│   ├── BusinessException.java
│   ├── CategoryNotFoundException.java
│   ├── GlobalExceptionHandler.java
│   ├── InvalidFileException.java
│   └── ProductNotFoundException.java
├── mapper/
│   ├── AttributeMapper.java
│   ├── ImageMapper.java
│   ├── ProductMapper.java
│   └── VideoMapper.java
├── model/
│   ├── Category.java
│   ├── Product.java                 — price DECIMAL (retail only, NO wholesalePrice yet)
│   ├── ProductAttribute.java
│   ├── ProductImage.java
│   └── ProductVideo.java
├── repository/
│   ├── ProductAttributeRepository.java
│   ├── ProductImageRepository.java
│   ├── ProductRepository.java
│   └── ProductVideoRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   └── JwtService.java              — extracts email, roles (NO clientType yet)
└── service/
    ├── ProductAttributeService.java
    ├── ProductService.java
    ├── ProductVideoService.java
    └── StorageService.java          — Yandex S3, WebP conversion
```

**DB migrations:**
- `V20251209204550__create_products_tables.sql`
- `V20260310210040__add_fields_1c_integration.sql` — externalId, sku, externalCode, unitOfMeasure, vatRate

---

## order-service

```
ru.rfsnab.orderservice/
├── OrderServiceApplication.java
├── aspect/LoggingAspect.java
├── config/
│   ├── JwtProperties.java
│   ├── JwtService.java
│   ├── OpenApiSwagger.java
│   ├── RedisConfig.java
│   └── SecurityConfig.java
├── controller/
│   ├── CartController.java
│   ├── OrderController.java         — CRUD orders, confirm, cancel, status
│   └── WarehousePointController.java
├── exception/ (7 exceptions + GlobalExceptionHandler)
├── mapper/
│   ├── AddressMapper.java
│   ├── OrderMapper.java
│   └── WarehousePointMapper.java
├── models/
│   ├── dto/cart/ (AddToCartRequest, CartDto, CartItemDto, UpdateCartItemRequest)
│   ├── dto/order/ (AddressDto, CreateOrderRequest, OrderDto, ...)
│   ├── entity/ (Order, OrderItem, DeliveryAddress, WarehousePoint)
│   └── enums/ (OrderStatus, DeliveryType, PaymentMethod)
├── producer/
│   └── Order1CKafkaProducer.java    — topic: order-events
├── repository/
│   ├── OrderItemRepository.java
│   ├── OrderRepository.java
│   └── WarehousePointRepository.java
└── service/
    ├── CartService.java              — Redis-based cart
    ├── OrderService.java
    └── ProductServiceClient.java     — REST call to product-service
```

**Kafka topics produced:** `order-events`

**OrderStatus:** CREATED → PROCESSING → INVOICE_SENT → AWAITING_CONFIRMATION → PAID → SHIPPED → DELIVERED | CANCELLED | PAYMENT_FAILED | REFUNDED

---

## notification-service

```
ru.rfsnab.notificationservice/
├── NotificationServiceApplication.java
├── aspect/LoggingAspect.java
├── controller/
│   └── VerificationController.java
├── handler/
│   ├── NotificationHandler.java     — interface
│   ├── NotificationRouter.java      — routes by eventType
│   ├── OrderHandler.java            — ORDER_CREATED/PAID/CANCELLED/STATUS_CHANGED
│   └── UserRegisteredHandler.java   — USER_REGISTERED → verification email
├── kafka/
│   └── KafkaListenerService.java    — consumes user-events, order-events
├── models/
│   ├── EmailVerificationToken.java
│   ├── OrderEvent.java
│   ├── UserEvent.java
│   └── VerificationResponse.java
├── repository/
│   └── EmailVerificationTokenRepository.java
└── service/
    ├── EmailService.java             — JavaMailSender, plain text, no HTML templates
    └── EmailVerificationTokenService.java
```

**Kafka topics consumed:** `user-events`, `order-events`
**NOT yet consuming:** `legal-entity-events` (Plan B adds this)

---

## gateway-service

Spring Cloud Gateway on port 8080. Routes all traffic under `/api`. JWT filter, Redis rate limiting. Forwards `X-User-Email` header downstream.

---

## integration-service

1С Fresh УНФ integration, CommerceML 2.08. Consumes `order-events` topic for order sync.

---

## frontend

```
frontend/src/
├── api/          — axios apiClient with interceptors
├── components/   — shared UI components
├── config/
│   └── company.ts — ООО МСВ, INN, OGRN, phones, address
├── pages/        — all pages (lazy-loaded via React.lazy)
├── store/
│   ├── authStore.ts   — Zustand
│   └── cartStore.ts   — Zustand, optimistic UI
└── utils/
    └── enumUtils.ts   — {code, displayName} enum handling
```

Tech: React 18, TypeScript, Vite, Ant Design, Framer Motion, React Router v6, Zustand, Axios.
