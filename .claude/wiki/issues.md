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

## Backend — Remaining / Future

### Future: B2B/B2C entity separation
B2B & B2C clients should be separate entity types in user-service.
Enables: different pricing, promotions, payment conditions per client type.
Scope: user-service (new entity), auth-service (token claims), product-service (prices),
order-service (validate client type on transitions).
Not yet scheduled.
