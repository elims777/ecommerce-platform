# Plan: Order Service Refactor + Status Machine

Created: 2026-05-14
Updated: 2026-05-17 — finalized business logic after discussion

## Context

Current state: order-service has 11 statuses but AWAITING_CONFIRMATION is unused,
order goes to 1C immediately on creation (not after client confirmation),
PICKUP orders lose recipient contact in 1C export.

## Finalized Business Logic (2026-05-17)

### Order creation flow (UX)
1. Client adds items to cart
2. Client clicks "Оформить заказ" → order created with status CREATED (stays local, NOT sent to 1C)
3. Client fills in delivery, recipient, payment details (can edit while CREATED)
4. Client clicks "Подтвердить заказ" → CREATED → PROCESSING, order sent to 1C via Kafka with full data

### After confirmation — B2B (юрлица)
- All status changes come FROM 1С automatically after manager actions
- Manager issues invoice in 1С → 1С sends invoice email → status → INVOICE_SENT (auto from 1С)
- Manager chooses scenario:
  - Prepayment: INVOICE_SENT → PENDING_PAYMENT
  - Postpayment: INVOICE_SENT → AWAITING_CONFIRMATION
- Then continues per scenario below

### After confirmation — B2C (физлица)
- After PROCESSING, client sees "Перейти к оплате" button → clicks → PENDING_PAYMENT
- Payment via QR code or card
- On success → PAID; on failure → PAYMENT_FAILED → client can retry (PENDING_PAYMENT again)

### Client type distinction
- Currently: registration split on frontend (юр/физ)
- Future: separate entity types in user-service (separate task, not in scope here)
- For now: both B2B and B2C transitions are allowed — PROCESSING can go to INVOICE_SENT (B2B from 1С)
  OR to PENDING_PAYMENT (B2C, client clicks pay) — whichever fires first

### Cancellation rules
- Client can cancel: CREATED, PROCESSING, INVOICE_SENT, PENDING_PAYMENT, PAYMENT_FAILED
- AWAITING_CONFIRMATION and beyond: manager only
- After PAID: manager → CANCELLED → REFUNDED

---

## Target Status Machine

### All allowed transitions
```
CREATED               → PROCESSING, CANCELLED
PROCESSING            → INVOICE_SENT (B2B, from 1С), PENDING_PAYMENT (B2C, client), CANCELLED
INVOICE_SENT          → PENDING_PAYMENT, AWAITING_CONFIRMATION, CANCELLED
PENDING_PAYMENT       → PAID, PAYMENT_FAILED, CANCELLED
PAYMENT_FAILED        → PENDING_PAYMENT, CANCELLED
AWAITING_CONFIRMATION → SHIPPED, CANCELLED (manager only)
PAID                  → SHIPPED
SHIPPED               → IN_TRANSIT
IN_TRANSIT            → DELIVERED
DELIVERED             → PAID   (B2B postpayment final step)
CANCELLED             → REFUNDED
```

### B2B prepayment scenario
```
CREATED → PROCESSING → INVOICE_SENT → PENDING_PAYMENT → PAID → SHIPPED → IN_TRANSIT → DELIVERED
```

### B2B postpayment scenario
```
CREATED → PROCESSING → INVOICE_SENT → AWAITING_CONFIRMATION → SHIPPED → IN_TRANSIT → DELIVERED → PAID
```

### B2C scenario
```
CREATED → PROCESSING → PENDING_PAYMENT → PAID → SHIPPED → IN_TRANSIT → DELIVERED
                                ↓ failed
                          PAYMENT_FAILED → PENDING_PAYMENT (retry)
```

---

## Status display names (for 1С mapping)

| Code | displayName (RU) | 1С name |
|------|-----------------|---------|
| CREATED | Заказ создан | — (internal only) |
| PROCESSING | В работе | В работе |
| INVOICE_SENT | Счёт выставлен | Счёт выставлен |
| PENDING_PAYMENT | Ожидает оплаты | Ожидает оплаты |
| AWAITING_CONFIRMATION | Ожидает подтверждения оплаты | Ожидает подтверждения оплаты |
| PAID | Оплачен | Оплачен |
| PAYMENT_FAILED | Ошибка оплаты | — (internal only) |
| SHIPPED | Отгружен | Отгружен |
| IN_TRANSIT | В пути | В пути |
| DELIVERED | Доставлен | Доставлен |
| CANCELLED | Отменён | Отменён |
| REFUNDED | Возврат средств | Возврат средств |

CREATED and PAYMENT_FAILED are platform-internal — not synced to 1С.

---

## Step-by-step Plan

### Step 1 — Update OrderStatus.java ✅ DONE (2026-05-17)
- Added: INVOICE_SENT ("Счёт выставлен")
- Renamed: PROCESSING → "В работе", AWAITING_CONFIRMATION → "Ожидает подтверждения оплаты"
- Reordered enum values to match lifecycle sequence

### Step 2 — Rewrite ALLOWED_TRANSITIONS in OrderService
```java
CREATED               → PROCESSING, CANCELLED
PROCESSING            → INVOICE_SENT, PENDING_PAYMENT, CANCELLED
INVOICE_SENT          → PENDING_PAYMENT, AWAITING_CONFIRMATION, CANCELLED
PENDING_PAYMENT       → PAID, PAYMENT_FAILED, CANCELLED
PAYMENT_FAILED        → PENDING_PAYMENT, CANCELLED
AWAITING_CONFIRMATION → SHIPPED, CANCELLED
PAID                  → SHIPPED
SHIPPED               → IN_TRANSIT
IN_TRANSIT            → DELIVERED
DELIVERED             → PAID
CANCELLED             → REFUNDED
```

### Step 3 — Update CANCELLABLE_STATUSES (client self-cancel)
```java
Set.of(CREATED, PROCESSING, INVOICE_SENT, PENDING_PAYMENT, PAYMENT_FAILED)
```
Note: AWAITING_CONFIRMATION and PAID are NOT in this set — manager only.

### Step 4 — Fix createOrder: remove 1C Kafka call
- Remove `order1CKafkaProducer.sendOrderFor1C(order)` from createOrder()
- Order created with CREATED status — stays local until client confirms

### Step 5 — Fix confirmPayment: remove double transition
- Remove automatic PAID → PROCESSING (no longer exists in new machine)
- confirmPayment stops at PAID only
- Update javadoc

### Step 6 — Add confirmOrder (CREATED → PROCESSING + send to 1С)
New method in OrderService:
```java
public Order confirmOrder(UUID orderId, Long userId)
```
- Validates owner
- changeStatus(order, PROCESSING)
- save
- kafkaProducer.sendOrderStatusChanged(order)
- order1CKafkaProducer.sendOrderFor1C(order)  ← moved here from createOrder

New endpoint in OrderController:
```
POST /api/v1/orders/{orderId}/confirm
```
Returns OrderDto (201 or 200).

### Step 7 — Fix PICKUP recipient in Order1CExportEvent
- DeliveryAddress already has recipientName/phone for DELIVERY orders
- For PICKUP: recipientName + phone must come from CreateOrderRequest or Recipient table
- Action: read CreateOrderRequest.java and DeliveryAddress.java to decide approach before coding

### Step 8 — Flyway migration
```sql
-- status column is VARCHAR(30), no schema change needed
-- Add or update CHECK constraint with full new status list including INVOICE_SENT
-- File: V7__add_invoice_sent_status_constraint.sql
```

### Step 9 — Update tests
- OrderServiceTest.java: update happy path (CREATED not auto-sent to 1С), add confirmOrder tests
- OrderControllerTest.java: add POST /confirm endpoint test
- Verify ALLOWED_TRANSITIONS coverage

### Step 10 — Update notification-service OrderHandler
- Add case INVOICE_SENT → email "Счёт выставлен, ожидайте письма"
- Add case AWAITING_CONFIRMATION → email "Товар отгружен, ожидаем подтверждения оплаты"

### Step 11 — Update wiki
- order-lifecycle.md: final status machine diagram
- issues.md: mark fixed
- log.md: session entry
- 1c-integration-checklist.md: confirm status mapping table is up to date

---

## Files to Change

| File | Change |
|------|--------|
| `order-service/.../enums/OrderStatus.java` | ✅ Done — INVOICE_SENT added, display names updated |
| `order-service/.../service/OrderService.java` | Transitions, CANCELLABLE, remove 1C from createOrder, fix confirmPayment, add confirmOrder |
| `order-service/.../controller/OrderController.java` | Add POST /confirm endpoint |
| `order-service/.../kafka/Order1CKafkaProducer.java` | Fix PICKUP recipient null issue |
| `order-service/src/test/.../OrderServiceTest.java` | Update + add tests |
| `order-service/src/test/.../OrderControllerTest.java` | Add confirm test |
| `order-service/src/main/resources/db/migration/V7_*.sql` | CHECK constraint with INVOICE_SENT |
| `notification-service/.../OrderHandler.java` | Add INVOICE_SENT + AWAITING_CONFIRMATION emails |

---

## Future Task: B2B/B2C Entity Separation
Agreed on 2026-05-17: B2B & B2C clients should be separate entity types in user-service.
This will enable: different pricing, promotions, payment conditions per client type.
Scope: user-service (new entity), auth-service (token claims), product-service (prices),
order-service (validate client type on transitions).
NOT in scope for current refactor — separate task.

---

# Plan: Frontend Design Migration

Created: 2026-05-14

## Context

Claude Design generated a full design project. Files are saved to:
`frontend/design-reference/`

Design files:
- `tokens.css` — design tokens (colors, fonts, spacing)
- `parts.jsx` — reusable UI components
- `design-canvas.jsx` — full canvas with all screens
- `screens-brand-home.jsx` — home/landing page
- `screens-catalog.jsx` — catalog page
- `screens-cart-checkout.jsx` — cart and checkout pages
- `screens-auth-admin.jsx` — login, register, admin pages
- `assets/uploads/` — images and assets

Brand colors (from logo):
- Red: #C0272D (primary actions)
- Navy: #1E3A5F (secondary/accent)
- Green: #1A6B3A (success/availability)
- Near-black: #1A1A1A (text)

---

## Step-by-step Plan

### Step 1 — Setup: copy design files, update .gitignore
- Copy Claude Design output to `frontend/design-reference/`
- Add `frontend/design-reference/` to `.gitignore`

### Step 2 — Read tokens.css
- Extract all CSS variables: colors, fonts, spacing, border-radius, shadows
- Map to Ant Design theme tokens in `App.tsx`
- Create `src/styles/tokens.css` with CSS custom properties for non-Ant parts

### Step 3 — Update Ant Design theme in App.tsx
Replace current theme:
```typescript
// Current (wrong):
colorPrimary: '#1677ff'  // default blue
fontFamily: '"Inter"'    // prohibited

// Target:
colorPrimary: '#C0272D'  // brand red
colorLink: '#1E3A5F'     // navy
fontFamily: '"Golos Text", ...'  // Russian-optimized
borderRadius: ...         // from tokens
```

### Step 4 — Read parts.jsx → create TypeScript components
- For each component in parts.jsx: create equivalent `.tsx` in `src/components/ui/`
- Convert JSX → TypeScript with proper types
- Replace inline styles with CSS modules or tokens

### Step 5 — Migrate ClientLayout (header + footer)
- Read `screens-brand-home.jsx` for header/footer design
- Remove glassmorphism from current ClientLayout.tsx
- Apply new header: logo left, nav center, actions right
- Apply new footer from design

### Step 6 — Migrate home/brand page
- Read `screens-brand-home.jsx`
- Update or create HomePage component
- Apply brand colors, typography, layout

### Step 7 — Migrate catalog page
- Read `screens-catalog.jsx`
- Update CatalogPage.tsx + ProductCard.tsx + CategoryTreeMenu.tsx
- Apply new card design, filters layout

### Step 8 — Migrate cart + checkout pages
- Read `screens-cart-checkout.jsx`
- Update CartPage.tsx
- Split CheckoutPage.tsx (657 lines) into steps:
  - `DeliveryStep.tsx`
  - `RecipientStep.tsx`
  - `PaymentStep.tsx`
  - `SummaryStep.tsx`

### Step 9 — Migrate auth + admin pages
- Read `screens-auth-admin.jsx`
- Update LoginPage.tsx, RegisterPage.tsx
- Update AdminLayout.tsx, DashboardPage.tsx

### Step 10 — Add code splitting
- Wrap all routes in App.tsx with React.lazy + Suspense
- Add skeleton loaders for main pages (CatalogPage, ProductPage, OrdersPage)

### Step 11 — Add optimistic UI to cart
- Update cartStore.ts: update state immediately, sync in background
- Handle rollback on error

### Step 12 — Add empty states
- Cart empty state
- Orders list empty state
- Admin tables empty state

### Step 13 — Test in browser
- Start dev server: `npm run dev`
- Check all pages: catalog, product, cart, checkout, orders, profile, admin
- Check responsive: mobile, tablet, desktop
- Check Framer Motion transitions still work

---

## Files to Change

| File | Change |
|------|--------|
| `frontend/.gitignore` | Add design-reference/ |
| `frontend/src/App.tsx` | New Ant Design theme, React.lazy routes |
| `frontend/src/styles/tokens.css` | CSS custom properties from tokens.css |
| `frontend/src/components/layouts/ClientLayout.tsx` | New header/footer, remove glassmorphism |
| `frontend/src/components/ui/` | New shared components from parts.jsx |
| `frontend/src/features/catalog/CatalogPage.tsx` | New design |
| `frontend/src/features/catalog/ProductCard.tsx` | New card design |
| `frontend/src/features/catalog/CategoryTreeMenu.tsx` | New design |
| `frontend/src/features/cart/CartPage.tsx` | New design + optimistic UI |
| `frontend/src/features/checkout/CheckoutPage.tsx` | Split into 4 step components |
| `frontend/src/features/orders/OrdersPage.tsx` | New design + empty state |
| `frontend/src/pages/LoginPage.tsx` | New design |
| `frontend/src/pages/RegisterPage.tsx` | New design |
| `frontend/src/components/layouts/AdminLayout.tsx` | New design |
| `frontend/src/features/admin/DashboardPage.tsx` | New design |
| `frontend/src/store/cartStore.ts` | Optimistic UI |

---

## Order of Sessions

**Session 1 (tomorrow):**
- Order service refactor (Steps 1-11 from order plan)

**Session 2:**
- Frontend: Steps 1-4 (tokens + theme + shared components)

**Session 3:**
- Frontend: Steps 5-7 (layout + home + catalog)

**Session 4:**
- Frontend: Steps 8-9 (cart/checkout + auth/admin)

**Session 5:**
- Frontend: Steps 10-12 (code splitting + optimistic UI + empty states)
- Final browser test
