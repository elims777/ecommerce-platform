# Current Issues

## Frontend — Resolved ✅

All critical frontend issues from the design audit are resolved as of 2026-05-16:
- ✅ Font replaced (Inter → Golos Text)
- ✅ Color replaced (#1677ff → #C0272D brand red)
- ✅ Glassmorphism removed from ClientLayout
- ✅ CheckoutPage split (657 lines → 344 + 4 sub-components)
- ✅ Code splitting added (React.lazy on all routes)
- ✅ Skeleton loaders added (CatalogPage, ProductPage, OrdersPage, CartPage)
- ✅ Optimistic UI in cartStore
- ✅ Empty states teach the interface
- ✅ ErrorBoundary around all routes
- ✅ ProfilePage syncs authStore after save

## Frontend — Minor (low priority)
- Empty states for admin tables (DashboardPage, AdminOrdersPage, AdminUsersPage)
- Skeleton loaders for admin pages

## Backend — order-service ✅ Resolved (2026-05-17)

4 bugs found in deep audit on 2026-05-14. All fixed in session 2026-05-17.
Plan: `.claude/wiki/plan-order-service-refactor.md`

### ✅ Bug 1: AWAITING_CONFIRMATION unused
Fixed: now used in B2B postpayment flow.
AWAITING_CONFIRMATION added to ALLOWED_TRANSITIONS: INVOICE_SENT → AWAITING_CONFIRMATION → SHIPPED.

### ✅ Bug 2: 1С export fires too early (CRITICAL)
Fixed: removed `order1CKafkaProducer.sendOrderFor1C()` from `createOrder()`.
Now fires only in `confirmOrder()` (CREATED → PROCESSING) — after client confirms with full data.
New endpoint: `POST /api/v1/orders/{id}/confirm`.

### ✅ Bug 3: PICKUP loses recipient in 1С export
Fixed: added `pickupRecipientName` + `pickupRecipientPhone` fields to `CreateOrderRequest`, `Order` entity, `OrderMapper`.
`Order1CKafkaProducer` now uses pickup fields when `deliveryAddress == null`.
Flyway migration: `V20260517120000__add_invoice_sent_and_pickup_recipient.sql`.

### ✅ Bug 4: confirmPayment double transition
Fixed: removed automatic PAID → PROCESSING from `confirmPayment()`.
Method now stops at PAID only. PROCESSING is reached only via `confirmOrder()`.

## Backend — B2B/B2C (Plan B) ✅ Resolved (2026-05-18 evening)

Bugs found during manual end-to-end testing after Plan B implementation:

### ✅ Bug 1: PostgreSQL reserved word `primary` in JPA entities
`LegalEntityBankAccount` and `LegalEntityAddress`: field `primary` mapped to column named `primary` — PostgreSQL reserved word, causes `ERROR: column does not exist`.
Fixed: added `@Column(name = "is_primary")` to both entities.
Files: `models/LegalEntityBankAccount.java`, `models/LegalEntityAddress.java`.

### ✅ Bug 2: SecurityConfig missing internal-call public paths
auth-service calls user-service internally (no JWT) for `link-status` and GET `/{id}` during switch-context.
These paths required authentication — caused 401 → 500 in auth-service.
Fixed: added `permitAll()` for `/api/v1/legal-entities/authenticate`, `/api/v1/legal-entities/link-status/**`, and GET `/api/v1/legal-entities/*`.
File: `configuration/SecurityConfig.java`.

### ✅ Bug 3: Gateway JWT filter missing authenticate path
`/api/v1/legal-entities/authenticate` not in PUBLIC_PATHS — gateway rejected unauthenticated B2B login.
Fixed: added path to `PUBLIC_PATHS` list in `JwtAuthenticationFilter`.
File: `gateway-service/.../JwtAuthenticationFilter.java`.

## Backend — Remaining / Future

### ✅ Plan C: order-service B2B snapshot + frontend — DONE (2026-05-20)
- Flyway V20260519000000: customer_type, company_name, inn в orders
- order-service: B2B snapshot, X-Client-Type header, выбор цены
- auth-service: AuthResponse + switchContext B2C↔B2B
- frontend: useDisplayPrice, ContextSwitcher, ProfilePage Организация, CheckoutPage B2B
- Task 5: auth-service — AuthResponse, SwitchContextRequest, JWTService (B2B claims), AuthService.switchContext rework
- Task 6: Frontend types (User+Product) + authStore (clientType/companyName/inn + switchContext action)
- Task 7: useDisplayPrice hook + ProductCard + ProductPage price display
- Task 8: ContextSwitcher pill in ClientLayout + B2B→B2C password modal
- Task 9: legalEntity.ts API + OrganizationSection in ProfilePage (3 states)
- Task 10: CheckoutPage B2B fields + SummaryStep company display
- Task 11: Wiki update
