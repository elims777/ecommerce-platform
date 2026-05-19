# Plan C — B2B Order Snapshot + Frontend Context Switcher

**Date:** 2026-05-19
**Branch:** feature/b2b-frontend (new, off feature/order-service-refactor)
**Depends on:** Plan A (user-service legal entities), Plan B (JWT clientType, gateway headers, wholesalePrice)

---

## Overview

Plan C completes the B2B/B2C separation by:
1. Persisting B2B customer identity snapshot in orders (so 1C export carries legal entity data)
2. Wiring `clientType` from JWT into the frontend state and UI
3. Adding a context switcher to the header (B2C ↔ B2B) — B2C→B2B without password, B2B→B2C requires personal password
4. Showing correct price (wholesale vs retail) based on active context
5. Adding a Legal Entity dashboard section in ProfilePage

---

## Section 1 — order-service: B2B Snapshot Fields

### Problem
`Order` entity has `userId` (Long, not nullable). For B2B orders, `userId` stores `legalEntityId` — same field, same JWT `sub` semantics (Plan B: `sub=legalEntityId` when `clientType=B2B`). No schema change needed for the ID field.

B2B snapshot needed for 1C export: `companyName`, `inn`. Email is already captured in the existing `customer_email` column (same field serves both B2C and B2B customers).

### Flyway migration: `V20260519000000__add_b2b_snapshot_to_orders.sql`
```sql
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS customer_type VARCHAR(10) NOT NULL DEFAULT 'B2C',
    ADD COLUMN IF NOT EXISTS company_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS inn           VARCHAR(12);

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
```

`customer_email` already exists — no change needed. For B2B orders it stores the legal entity email.

### CreateOrderRequest — extended
Add `companyName` and `inn` fields (both optional at DTO level). Service-level validation: if `clientType == B2B` then `companyName` and `inn` must be non-null.

`clientType` is NOT in the request body — it comes from the `X-Client-Type` header (gateway-forwarded).

### OrderController — read headers
`OrderController.createOrder()` reads:
- `X-Client-Type` → `clientType` (String: "B2C" or "B2B")
- `X-User-Id` → `userId` (Long — for B2B this is `legalEntityId`, same field)

Both headers are already forwarded by gateway (Plan B). Controller passes them to service.

**Chosen approach:** controller extracts headers, passes `clientType` and `userId` to service. Service handles snapshot logic. Keeps service testable without HTTP context.

### OrderService — populate snapshot
```java
public Order createOrder(Long userId, String customerEmail, String clientType, CreateOrderRequest request) {
    // ...
    order.setCustomerType(clientType);
    if ("B2B".equals(clientType)) {
        // companyName and inn must be non-null for B2B — validated here
        if (request.companyName() == null || request.inn() == null) {
            throw new InvalidOrderStateException("B2B order requires companyName and inn");
        }
        order.setCompanyName(request.companyName());
        order.setInn(request.inn());
    }
    // ...
}
```

`order.setUserId(userId)` — for B2B this stores legalEntityId. No change to field name.

### OrderDto — expose snapshot fields
Add `customerType`, `companyName`, `inn` to `OrderDto`. `OrderMapper` maps them.

### Order1CKafkaProducer — use snapshot
`Order1CExportEvent` already exists. Add `customerType`, `companyName`, `inn` fields to the event record so 1C can find the legal entity by INN.

---

## Section 2 — Frontend: clientType in authStore

### Problem
JWT already contains `clientType` (Plan B), but `User` type and `authStore` ignore it. `decodeJwtPayload` is already present in `authStore.ts`.

### JWT structure (Plan B)
```
sub = userId (B2C) | legalEntityId (B2B)   ← same field, different value
clientType = "B2C" | "B2B"
email = user or legal entity email
```

### Changes to `frontend/src/types/auth.ts`
```typescript
export type ClientType = 'B2C' | 'B2B';

export interface User {
    // existing fields unchanged
    id: number;           // userId for B2C, legalEntityId for B2B — from JWT sub
    email: string;
    firstname: string;
    lastname: string;
    surname: string | null;
    phone: string | null;
    emailVerified: boolean;
    roles: UserRole[];
    createdAt: string;
    updatedAt: string;
    // new fields
    clientType: ClientType;
    companyName?: string | null;   // present when B2B
}
```

No separate `legalEntityId` field — `id` already carries it when `clientType === 'B2B'`.

### Changes to `authStore.ts`
- `login()` — extract `clientType`, `companyName` from `tokens.user` (auth-service `AuthResponse` must include them — see Section 6)
- `restoreSession()` — when falling back to JWT payload decoding, extract `clientType`, `companyName` from claims
- Add `switchContext(targetType: ClientType, password?: string): Promise<void>` action

```typescript
switchContext: async (targetType, password) => {
    const { data } = await apiClient.post<AuthTokens>('/v1/auth/switch-context', {
        targetType,
        ...(password ? { password } : {}),
    });
    localStorage.setItem('accessToken', data.access_token);
    localStorage.setItem('refreshToken', data.refresh_token);
    set({ user: data.user, isAuthenticated: true });
},
```

---

## Section 3 — Frontend: Context Switcher in Header

### Placement
In `ClientLayout.tsx`, inside the authenticated user section. Add a **pill toggle** `Физлицо | Организация` rendered to the left of the user dropdown button.

### Visibility
Pill is visible only when `isAuthenticated && user.companyName != null` (meaning a verified legal entity is linked — populated from auth response).

If no legal entity linked: pill is hidden. Add "Подключить организацию" item to `userMenuItems` dropdown → navigates to `/profile#organization`.

### Switch B2C → B2B (no password)
- Optimistic: set `user.clientType = 'B2B'` immediately in store
- Call `authStore.switchContext('B2B')` (no password)
- On success: replace tokens and user in store, re-fetch cart
- On error: revert `clientType` to 'B2C', show error message
- Auth-service validates that the physical user has a verified linked legal entity

### Switch B2B → B2C (password required)
- Show a small modal: "Введите пароль от личного аккаунта"
- On submit: call `authStore.switchContext('B2C', password)`
- Auth-service validates the physical user's password before issuing B2C JWT
- On success: replace tokens, store updated, re-fetch cart
- On error: show "Неверный пароль"

**Rationale:** User controls legal entity data, not vice versa. Switching back to personal account requires proving personal identity — password acts as confirmation.

### After any switch
- Cart re-fetched (prices depend on clientType)
- Page does not reload — reactive store update propagates to all components

---

## Section 4 — Frontend: Price Display by clientType

### Correct mapping (from Plan B spec)
| DB field | 1C source | Shown to |
|---|---|---|
| `price` | "Оптовая" | B2B customers |
| `wholesalePrice` | "Розничная" | B2C customers + unauthenticated |

### Implementation
Single hook `useDisplayPrice` in `frontend/src/utils/priceUtils.ts`:

```typescript
export const useDisplayPrice = (product: { price: number; wholesalePrice?: number | null }): number => {
    const clientType = useAuthStore((s) => s.user?.clientType ?? 'B2C');
    return clientType === 'B2B' ? product.price : (product.wholesalePrice ?? product.price);
};
```

Use in: `ProductCard.tsx`, `ProductPage.tsx`, `CartPage.tsx`, `SummaryStep.tsx`.

### Backend: price selection in order-service
`ProductDto` in order-service must add `wholesalePrice` field (currently missing). `addItemsFromCart` and `addItemsFromDto` select price by `clientType`:

```java
BigDecimal snapshotPrice = "B2B".equals(clientType)
    ? product.price()
    : (product.wholesalePrice() != null ? product.wholesalePrice() : product.price());
```

`OrderItem.price` stores the snapshot — field already exists, just needs correct value.

---

## Section 5 — Frontend: Legal Entity Section in ProfilePage

`ProfilePage` currently has sections "Личные данные" and "Аккаунт". Add third section: **"Организация"**.

### State 1 — no legal entity linked (`user.companyName == null`)
- Empty state: "Работаете от юридического лица? Подключите организацию."
- CTA: "Подать заявку" → inline registration form

### State 2 — linked, pending verification
- API call: `GET /api/v1/legal-entities/link-status/{userId}` → status field
- Show: company name, INN, status badge "На проверке"
- Context switcher pill in header hidden (not verified yet)

### State 3 — verified
- Show: companyName, INN, email
- Context switcher pill in header active

### Registration form (inline, shown in State 1)
Fields: `companyName` (required), `inn` (required), `email` (required — legal entity contact email), `password` (required — sets B2B login password).
Calls: `POST /api/v1/legal-entities/register` (Plan A, already implemented).
On success: `restoreSession()` to refresh user data.

### New API file: `frontend/src/api/legalEntity.ts`
```typescript
getLinkStatus(userId): GET /api/v1/legal-entities/link-status/{userId}
getLegalEntity(id): GET /api/v1/legal-entities/{id}
registerLegalEntity(request): POST /api/v1/legal-entities/register
```

---

## Section 6 — auth-service: Verify AuthResponse

Plan B implemented `switch-context` and B2B login. Verify that `AuthResponse` DTO includes:
- `clientType: String` — "B2C" or "B2B"
- `companyName: String` (nullable) — present for B2B, null for B2C

If missing, add to `AuthResponse` and populate in login/switch-context handlers. Frontend relies on these fields to avoid JWT decoding on every page load.

`legalEntityId` does NOT need to be a separate field in `AuthResponse` — it's already in `sub` (JWT). Frontend reads it as `user.id` when `clientType === 'B2B'`.

---

## Section 7 — CheckoutPage: B2B fields

When `user.clientType === 'B2B'`, `CheckoutPage` automatically adds B2B snapshot to `CreateOrderRequest`:
```typescript
const b2bFields = user.clientType === 'B2B'
    ? { companyName: user.companyName, inn: /* from legalEntity API or user store */ }
    : {};
await createOrder({ ...deliveryFields, ...b2bFields });
```

`SummaryStep` shows company name and INN in order summary when B2B.

**INN in store:** `inn` needs to be available on the frontend without extra API calls. Two options:
- Include `inn` in `AuthResponse` + `User` type (preferred — symmetric with `companyName`)
- Fetch from `GET /api/v1/legal-entities/{id}` on checkout

**Decision:** Add `inn` to `AuthResponse` and `User` type (same as `companyName`). One extra field, zero extra API calls.

---

## Section 8 — Data Flow Summary

```
B2C Login  → JWT { sub=userId, clientType=B2C }
B2B Login  → JWT { sub=legalEntityId, clientType=B2B, companyName, inn }
                ↓
authStore.user { id=userId|legalEntityId, clientType, companyName?, inn? }
                ↓
Header: pill "Физлицо | Организация" (if companyName != null)
Products: useDisplayPrice → price (B2B) or wholesalePrice (B2C)
Checkout: CreateOrderRequest { ..., companyName, inn } when B2B
OrderService: Order { customerType=B2B, userId=legalEntityId, companyName, inn }
Order1CKafkaProducer → event with companyName + inn for 1C lookup
```

---

## Section 9 — Testing

### Backend (order-service)
- `createOrder_B2B_snapshotsCompanyData` — verify `customerType=B2B`, `companyName`, `inn` set correctly
- `createOrder_B2C_noSnapshotFields` — verify `companyName` and `inn` null, `customerType=B2C`
- `createOrder_B2B_usesWholesalePrice` — verify `OrderItem.price` = `product.price()` for B2B
- `createOrder_B2C_usesRetailPrice` — verify `OrderItem.price` = `product.wholesalePrice()` for B2C
- `createOrder_B2B_missingInn_throws` — verify exception when B2B without inn/companyName
- `OrderControllerTest`: `X-Client-Type: B2B` header flows into order entity

### Frontend
- Manual: B2B login → pill shows "Организация" active, prices show wholesale
- Manual: switch to B2C (with password modal) → pill switches, prices show retail
- Manual: checkout as B2B → summary shows company name + INN

---

## Section 10 — Implementation Order

1. **order-service** — Flyway migration + entity fields (`customerType`, `companyName`, `inn`) + `ProductDto` add `wholesalePrice` + `OrderService` price selection + snapshot logic + `OrderDto` + `OrderMapper` + `Order1CExportEvent` fields + unit tests
2. **auth-service** — verify/add `clientType`, `companyName`, `inn` to `AuthResponse`; verify `switch-context` handles password param for B2B→B2C
3. **frontend types + authStore** — add `clientType`, `companyName`, `inn` to `User`; `switchContext(targetType, password?)` action
4. **frontend ClientLayout** — context switcher pill with B2B→B2C password modal
5. **frontend price display** — `useDisplayPrice` hook + apply in ProductCard, ProductPage, CartPage, SummaryStep
6. **frontend ProfilePage** — "Организация" section (3 states + registration form) + `legalEntity.ts` API
7. **frontend CheckoutPage** — send `companyName`/`inn` in `CreateOrderRequest` when B2B; SummaryStep shows them
8. **Tests** — order-service unit tests (already listed above)

---

## Out of Scope for Plan C

- B2B-specific payment flows (invoice upload, post-payment) — separate plan
- Admin UI for legal entity verification — separate plan
- Email notifications for legal entity status changes — already done in Plan B (notification-service)
