# Session Log

## 2026-05-15
- Full frontend redesign applied from design-reference (tokens.css + screens-*.jsx)
- CSS design tokens added to index.css: OKLCH warm neutrals, brand-red, brand-navy, Geologica/Golos Text/Unbounded fonts
- Ant Design theme updated: colorPrimary → #C0272D, fontFamily → Golos Text
- ClientLayout: replaced glass-sphere logo with flat logo + Unbounded wordmark, new dark navy footer, fade+Y page transitions
- CatalogPage: custom sidebar (no AntD Card), 3-col grid, warm-neutral breadcrumbs
- CategoryTreeMenu: red active indicator (brand-red left border), warm hover
- ProductCard: brand-red price (Geologica 600), custom add-to-cart button, hover lift
- ProductPage: 3-col grid (gallery|info|buybox), sticky buy box, custom quantity stepper
- LoginPage/RegisterPage: split-layout (navy brand panel + white form), decorative SVG triangle, no more centered Card
- OrdersPage: custom StatusBadge with OKLCH tinted backgrounds per status group
- CartPage: custom grid layout (no AntD Table), sidebar summary with sticky buy button
- ProfilePage: custom section cards replacing AntD Card, native form layout

## 2026-05-14 (end of session)
- Rolled back all premature order-service changes (lesson: read everything before touching)
- Full deep read of order-service + integration-service + notification-service
- Found 4 real issues: AWAITING_CONFIRMATION unused, 1C called on CREATED not CONFIRMED,
  PICKUP loses recipient in export, confirmPayment has wrong double transition
- Saved detailed refactor plan to plan-order-service-refactor.md
- All questions clarified: confirmed on client click, AWAITING=postpayment, recipient from frontend

## 2026-05-14 (continued)
- Updated order statuses: CREATED→DRAFT, added INVOICE_SENT
- Rewrote ALLOWED_TRANSITIONS for B2B prepayment / B2B postpayment / B2C scenarios
- CANCELLABLE_STATUSES: cancellation allowed up to (not including) SHIPPED
- Flyway migration V7: UPDATE CREATED→DRAFT + CHECK constraint on status column
- Created wiki/order-lifecycle.md — full order lifecycle documentation

## 2026-05-14
- Wiki initialized (first-time setup)
- Reviewed full frontend codebase (~50 components, 5000 LOC)
- Expert design assessment completed:
  - Inter font used — violates CLAUDE.md standards
  - Default Ant Design blue (#1677ff) — looks like template
  - Glassmorphism in ClientLayout header — violates standards
  - CheckoutPage.tsx 657 lines — needs splitting
  - No code splitting (React.lazy/Suspense) on routes
  - No skeleton loaders, no empty states, no optimistic UI in cart
- Test coverage: product-service brought to 45% (parent pom minimum)
- All services meet 45% LINE coverage threshold
- Next: frontend redesign — theme, visual, UX polish
