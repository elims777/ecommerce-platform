# Frontend — Overview

## Stack
- React 19 + TypeScript (strict) + Vite 8
- Ant Design v6 + @ant-design/icons v6
- Framer Motion v12 (page transitions)
- Zustand v5 (authStore, cartStore)
- TanStack Query v5 (server state, caching)
- React Router v7 (nested layouts, Outlet)
- Axios (apiClient with interceptors)

## Structure
```
src/
  api/          — 11 API modules (auth, cart, products, orders, profile, etc.)
  store/        — authStore.ts, cartStore.ts
  types/        — auth.ts, product.ts, order.ts
  config/       — company.ts (centralized company data)
  components/
    layouts/    — ClientLayout.tsx, AuthLayout.tsx, AdminLayout.tsx
    ErrorBoundary.tsx
  features/
    catalog/    — CatalogPage, ProductPage, ProductCard, CategoryTreeMenu
    cart/       — CartPage
    checkout/   — CheckoutPage + DeliveryStep, RecipientStep, PaymentStep, SummaryStep
    orders/     — OrdersPage
    profile/    — ProfilePage
    admin/      — DashboardPage, AdminCatalogPage, AdminOrdersPage, AdminUsersPage, IntegrationPage
  pages/        — LoginPage, RegisterPage, AboutPage, ContactsPage, static pages
  auth/         — ProtectedRoute.tsx
  utils/        — enumUtils.ts
```

## Routes
- `/` — HomePage (public)
- `/catalog` — CatalogPage (public)
- `/products/:id` — ProductPage (public)
- `/login`, `/register` — AuthLayout (no header/footer)
- `/cart`, `/checkout`, `/orders`, `/profile` — authenticated (ClientLayout)
- `/admin/*` — admin only (AdminLayout)

## Current Theme (App.tsx)
```typescript
colorPrimary: '#C0272D'           // brand red
colorLink: '#1E3A5F'              // brand navy
fontFamily: "'Golos Text', ..."   // approved font
borderRadius: 6
colorBgLayout: '#f7f5f3'          // warm off-white
```

## Design Standards (from CLAUDE.md)
**DO NOT use:**
- Inter as default font
- Purple gradients, glassmorphism, bounce easing
- Cards-in-cards
- Gray text on colored backgrounds
- Emoji as section headings

**DO use:**
- OKLCH tinted neutrals with warm base hue (defined in index.css as CSS vars)
- clamp() for marketing typography, fixed rem for app/dashboard
- 8px baseline grid
- ease-out enter / ease-in exit / spring for interactive
- Animate only transform and opacity
- AnimatePresence for conditional rendering
- Optimistic UI — update immediately, sync in background
- Empty states that teach the interface

## Implemented Polish (as of 2026-05-16)
- React.lazy + Suspense on all 19 routes — each page is a separate chunk
- ErrorBoundary wraps all routes — catches runtime errors gracefully
- Skeleton loaders: CatalogPage (4-col grid), ProductPage (3-col), OrdersPage, CartPage
- Optimistic cart: addItem/updateQuantity/removeItem update UI immediately, rollback on error
- Empty states: CartPage and OrdersPage teach the interface with B2B copy
- CheckoutPage split into 4 sub-components (344 lines remaining)
- ProfilePage refreshes authStore after save

## Company Config
All company data (name, INN, OGRN, phones, email, address, hours) lives in `src/config/company.ts`.
Used in: ClientLayout, AboutPage, ContactsPage, PersonalDataPage, PrivacyPolicyPage, LoginPage.

## Key Files
- `src/App.tsx` — root, routing, Ant Design theme, Suspense/ErrorBoundary
- `src/config/company.ts` — single source of truth for company data
- `src/api/client.ts` — Axios instance, JWT interceptors, 401 auto-refresh
- `src/store/authStore.ts` — login, logout, session restore, JWT decode
- `src/store/cartStore.ts` — optimistic cart CRUD with rollback
- `src/components/layouts/ClientLayout.tsx` — 3-level header, footer, page transitions
- `src/features/checkout/CheckoutPage.tsx` — orchestrator, all state/queries here

## Enum Handling
Backend returns enums as `{code, displayName}`. Use `enumUtils.ts` for parsing.

## API Base
`VITE_API_BASE_URL` env var, falls back to `/api`. Dev proxy: /api → localhost:8080.

## Remaining Frontend Work
- Empty states for admin tables (low priority)
- Skeleton loaders for admin pages (low priority)
- Dark mode (nice to have)
