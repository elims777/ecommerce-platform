# Plan C — B2B Order Snapshot + Frontend Context Switcher

**Date:** 2026-05-19
**Branch:** feature/plan-c-b2b-frontend (new, off feature/order-service-refactor)
**Depends on:** Plan A (user-service legal entities), Plan B (JWT clientType, gateway headers, wholesalePrice)

---

## Overview

Plan C completes the B2B/B2C separation by:
1. Persisting B2B customer identity snapshot in orders (so 1C export carries legal entity data)
2. Wiring `clientType` from JWT into the frontend state and UI
3. Adding a context switcher to the header (B2C ↔ B2B)
4. Showing correct price (wholesale vs retail) based on active context
5. Adding a Legal Entity dashboard section in ProfilePage

---

## Section 1 — order-service: B2B Snapshot Fields

### Problem
`Order` entity has `userId` but no B2B identity snapshot. When a B2B customer places an order, 1C export (`Order1CKafkaProducer`) needs `companyName`, `inn`, `ogrn`. These must be captured at order creation time (snapshot pattern — same as `productName`/`price` in `OrderItem`).

### Flyway migration: `V20260519000000__add_b2b_snapshot_to_orders.sql`
```sql
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(10) NOT NULL DEFAULT 'B2C',
    ADD COLUMN IF NOT EXISTS company_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS inn           VARCHAR(12),
    ADD COLUMN IF NOT EXISTS kpp           VARCHAR(9),
    ADD COLUMN IF NOT EXISTS ogrn          VARCHAR(15);

ALTER TABLE orders
    ADD CONSTRAINT orders_customer_type_check
    CHECK (customer_type IN ('B2C', 'B2B'));
```

### Order entity — new fields
```java
@Column(name = "customer_type", nullable = false, length = 10)
private String customerType = "B2C";

@Column(name = "company_name", length = 255)
private String companyName;

@Column(name = "inn", length = 12)
private String inn;

@Column(name = "kpp", length = 9)
private String kpp;

@Column(name = "ogrn", length = 15)
private String ogrn;
```

### CreateOrderRequest — extended
Add `clientType`, `companyName`, `inn`, `kpp`, `ogrn` fields. All optional — required only when `clientType = B2B`. Validation: if `clientType == B2B` then `companyName` and `inn` must be non-null (`@AssertTrue` at record level or service-level check).

### OrderController — read headers
`OrderController.createOrder()` reads `X-Client-Type` and `X-User-Id` headers forwarded by gateway (already configured in Plan B). If `X-Client-Type = B2B`, passes B2B snapshot fields into `CreateOrderRequest` or enriches the `Order` directly in service.

**Chosen approach:** controller extracts `clientType` from header and passes it to service. Service reads B2B fields from request. This keeps the service testable without HTTP context.

### OrderService — populate snapshot
```java
order.setCustomerType(clientType);
if ("B2B".equals(clientType)) {
    order.setCompanyName(request.companyName());
    order.setInn(request.inn());
    order.setKpp(request.kpp());
    order.setOgrn(request.ogrn());
}
```

### OrderDto — expose snapshot fields
Add `customerType`, `companyName`, `inn`, `kpp`, `ogrn` to `OrderDto`. `OrderMapper` maps them.

### Order1CKafkaProducer — use snapshot
`Order1CExportEvent` already exists. Add B2B fields to the event record so 1C receives full legal entity identity.

---

## Section 2 — Frontend: clientType in authStore

### Problem
JWT already contains `clientType` (Plan B), but `User` type and `authStore` ignore it. `decodeJwtPayload` is already present in `authStore.ts`.

### Changes to `frontend/src/types/auth.ts`
```typescript
export type ClientType = 'B2C' | 'B2B';

export interface User {
    // ... existing fields ...
    clientType: ClientType;          // new — from JWT claim
    companyName?: string | null;     // new — present when B2B
    legalEntityId?: number | null;   // new — present when B2B
}
```

### Changes to `authStore.ts`
- `login()` — auth-service already returns `clientType` in JWT; extract from `tokens.user` (auth-service `AuthResponse` must include it — see Section 5)
- `restoreSession()` — when falling back to JWT payload decoding, extract `clientType`, `legalEntityId` from claims
- Add `switchContext(targetType: ClientType): Promise<void>` action that calls `POST /v1/auth/switch-context` and replaces tokens + user in store

```typescript
switchContext: async (targetType) => {
    const { data } = await apiClient.post<AuthTokens>('/v1/auth/switch-context', { targetType });
    localStorage.setItem('accessToken', data.access_token);
    localStorage.setItem('refreshToken', data.refresh_token);
    set({ user: data.user, isAuthenticated: true });
},
```

---

## Section 3 — Frontend: Context Switcher in Header

### Placement
In `ClientLayout.tsx`, inside the authenticated user section (currently a `Dropdown` with `userMenuItems`). Add a **pill toggle** above the dropdown button showing `B2C | B2B`.

### Behavior
- Pill is visible only when `isAuthenticated && user.legalEntityId != null`
- Active segment highlighted with `var(--brand-red)` background
- Clicking the inactive segment calls `authStore.switchContext(target)`
  - Optimistic UI: update `user.clientType` immediately, revert on error
  - Show brief loading state on the pill during the request
- After switch: cart is re-fetched (clientType affects pricing), page does not reload

### Switch B2C → B2B
Calls `POST /v1/auth/switch-context` with `{ targetType: 'B2B' }`. Auth-service already implemented this in Plan B (uses linked legal entity from user-service). Returns new JWT with `clientType=B2B`, `sub=legalEntityId`.

### Switch B2B → B2C
Same endpoint with `{ targetType: 'B2C' }`. Returns JWT with `clientType=B2C`, `sub=userId`. **No password re-entry required** (this is the same session — the user already authenticated as B2C originally).

> **Note:** Plan B spec says "legal→user requires password". Re-evaluate: if the physical user's session is still tracked server-side (refresh token), a B2B→B2C switch within the same session should not require password. Auth-service implementation determines this — if it already works without password, use it. If it requires password, add a modal prompt.

### No legal entity linked yet
If `user.legalEntityId == null`, the pill is hidden. Instead, add a "Подключить организацию" link in `userMenuItems` dropdown that navigates to `/profile#legal-entity`.

---

## Section 4 — Frontend: Price Display by clientType

### Problem
`product-service` returns both `price` (wholesale/B2B) and `wholesalePrice` (retail/B2C) in `ProductResponse`. Frontend currently always shows `price`.

### Correct mapping (from Plan B spec)
| Field in DB | 1C source | Shown to |
|---|---|---|
| `price` | "Оптовая" | B2B customers |
| `wholesalePrice` | "Розничная" | B2C customers |

### Implementation
Add `useDisplayPrice(product)` hook or inline helper in `ProductCard.tsx`, `ProductPage.tsx`, `CartPage.tsx`, `CheckoutPage.tsx`, `SummaryStep.tsx`:

```typescript
const useDisplayPrice = (product: { price: number; wholesalePrice?: number | null }) => {
    const clientType = useAuthStore((s) => s.user?.clientType ?? 'B2C');
    return clientType === 'B2B' ? product.price : (product.wholesalePrice ?? product.price);
};
```

Unauthenticated users see `wholesalePrice` (retail price, same as B2C).

### Order total
`OrderService.createOrder` in backend uses `product.price()` (from `ProductDto`) for B2B and needs to use `wholesalePrice` for B2C. Currently it always uses `product.price()`.

**Backend fix needed:** `ProductDto` in order-service must include `wholesalePrice`. `addItemsFromCart` and `addItemsFromDto` must pick price by `clientType` header. `OrderItem.price` stores the snapshot price (already correct field).

```java
BigDecimal snapshotPrice = "B2B".equals(clientType)
    ? product.price()
    : (product.wholesalePrice() != null ? product.wholesalePrice() : product.price());
```

---

## Section 5 — Frontend: Legal Entity Section in ProfilePage

### New route: `/profile` (existing) — add B2B tab/section

`ProfilePage` currently has two sections: "Личные данные" and "Аккаунт". Add a third section: **"Организация"**.

### States of the "Организация" section
1. **No legal entity linked** (`user.legalEntityId == null`):
   - Show empty state: "Работаете от юридического лица? Подключите организацию."
   - CTA button: "Подать заявку" → modal/inline form

2. **Legal entity linked, pending verification** (`legalEntityId != null`, status = `PENDING` or `UNVERIFIED`):
   - Show company name, INN
   - Status badge: "На проверке"
   - No switch context pill in header yet (can't use B2B until verified)

3. **Legal entity verified** (`status = VERIFIED`):
   - Show: companyName, INN, KPP, OGRN, email, bank accounts
   - Context switcher pill in header becomes active

### Registration form (inline in ProfilePage)
Fields: `companyName`, `inn`, `kpp` (optional), `ogrn` (optional), `email` (legal entity email), `password`.
Calls: `POST /api/v1/legal-entities/register` (already implemented in Plan A via user-service/gateway).
On success: refetch user session to get updated `legalEntityId`.

### API calls needed
- `GET /api/v1/legal-entities/{id}` — get legal entity details (already exists, Plan A)
- `GET /api/v1/legal-entities/link-status/{userId}` — check link status (already exists, Plan A)
- `POST /api/v1/legal-entities/register` — already exists

New file: `frontend/src/api/legalEntity.ts`

---

## Section 6 — auth-service: Ensure AuthResponse includes clientType and legalEntityId

Plan B implemented `switch-context` and B2B login. Verify that `AuthResponse` DTO (auth-service) includes:
- `clientType: String` — "B2C" or "B2B"
- `legalEntityId: Long` (nullable) — present when B2B

If missing, add to `AuthResponse` and populate in `JwtTokenService` / login handlers. Frontend depends on this to populate `User.clientType` and `User.legalEntityId` without JWT decoding on every load.

---

## Section 7 — CheckoutPage: B2B fields

When `user.clientType == 'B2B'`, `CheckoutPage` sends B2B snapshot fields in `CreateOrderRequest`:
- `companyName`, `inn`, `kpp`, `ogrn` — taken from `user` store (already populated from auth response)
- No extra user input required — snapshot is automatic

`SummaryStep` shows company name and INN in order summary when B2B.

---

## Section 8 — Data Flow Summary

```
Login (B2B) → JWT { sub=legalEntityId, clientType=B2B, companyName, inn }
                ↓
authStore.user { clientType:'B2B', legalEntityId, companyName, inn }
                ↓
Header pill shows B2B active
ProductCard/Page shows price (wholesale)
CheckoutPage sends { clientType:'B2B', companyName, inn, ... }
OrderService creates Order { customerType:'B2B', companyName, inn, ... }
Order1CKafkaProducer sends event with B2B identity to 1C
```

---

## Section 9 — Testing

### Backend (order-service)
- `OrderServiceTest`: `createOrder_B2B_snapshotsCompanyData` — verify `customerType`, `companyName`, `inn` set
- `OrderServiceTest`: `createOrder_B2C_noSnapshotFields` — verify B2B fields null for B2C orders
- `OrderServiceTest`: `createOrder_B2B_usesWholesalePrice` / `createOrder_B2C_usesRetailPrice`
- `OrderControllerTest`: header `X-Client-Type: B2B` flows into order entity

### Frontend
- Manual: login as B2B → pill shows B2B active → prices show wholesale
- Manual: switch to B2C → pill updates → prices show retail
- Manual: checkout as B2B → order summary shows company name

---

## Section 10 — Implementation Order

1. **order-service backend** — Flyway migration + entity fields + service + mapper + DTO + controller header extraction + ProductDto wholesalePrice + price selection logic
2. **auth-service** — verify/add clientType + legalEntityId to AuthResponse
3. **frontend authStore + types** — add clientType, legalEntityId, companyName to User; add switchContext action
4. **frontend header** — context switcher pill in ClientLayout
5. **frontend price display** — useDisplayPrice in ProductCard, ProductPage, CartPage, CheckoutPage, SummaryStep
6. **frontend ProfilePage** — Организация section (view + registration form)
7. **frontend CheckoutPage** — send B2B snapshot fields in CreateOrderRequest
8. **Tests** — order-service unit tests

---

## Out of Scope for Plan C

- B2B-specific payment flows (invoice upload, post-payment) — separate plan
- Admin UI for legal entity verification — separate plan
- Email notifications for legal entity status changes — already done in Plan B (notification-service)
