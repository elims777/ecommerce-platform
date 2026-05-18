# Roadmap

## Current Priority: Frontend (urgent)

### P0 — Visual / Design (do first, biggest impact)
- [x] New Ant Design theme: Golos Text, #C0272D, border-radius 6
- [x] Remove glassmorphism from ClientLayout header
- [x] OKLCH palette defined in index.css
- [x] Typography hierarchy applied

### P1 — Performance
- [x] React.lazy + Suspense on all routes in App.tsx
- [x] Skeleton loaders for CatalogPage, ProductPage, OrdersPage, CartPage

### P2 — UX Polish
- [x] Optimistic UI in cartStore (addItem/updateQuantity/removeItem with rollback)
- [x] Empty states: CartPage, OrdersPage — teach the interface, B2B-aware copy
- [x] Split CheckoutPage.tsx into DeliveryStep, RecipientStep, PaymentStep, SummaryStep

### P3 — Nice to have
- [ ] Dark mode toggle (Ant Design supports it)
- [x] Error boundaries around main feature areas
- [x] ProfilePage: refresh authStore after profile update

### Remaining
- [ ] Empty states for admin tables (DashboardPage, AdminOrdersPage, AdminUsersPage)
- [ ] Skeleton loaders for admin pages

## Completed
- [x] Test coverage ≥ 45% for all services (parent pom JaCoCo check)
- [x] product-service brought to 45% coverage
- [x] Wiki initialized
