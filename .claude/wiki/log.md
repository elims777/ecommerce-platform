# Session Log

## 2026-05-20 (Plan C)
- Flyway V20260519000000: колонки customer_type, company_name, inn в orders
- order-service: B2B snapshot в createOrder, заголовок X-Client-Type, выбор цены (B2B→price, B2C→wholesalePrice)
- auth-service: AuthResponse обогащён clientType/companyName/inn, switchContext переписан (B2C→B2B без пароля, B2B→B2C с паролем)
- frontend: ClientType в User, authStore.switchContext(), хук useDisplayPrice
- frontend: пилюля переключения контекста в header с модалкой пароля
- frontend: секция Организация в ProfilePage (3 состояния + форма регистрации)
- frontend: CheckoutPage отправляет B2B поля, SummaryStep показывает компанию и ИНН

## 2026-05-19
- Design update: replaced SVG-triangle watermark with real logo assets (logo-light.png, logo-dark.png, logo-v2.png)
- LoginPage: logo-light.png as top logo + watermark (opacity 0.14, h=360) on dark brand panel
- ClientLayout header: logo-dark.png (was logo.png)
- ClientLayout footer: logo-light.png + wordmark "РФснаб / комплексное снабжение" on dark background
- HomePage hero: logo-light.png watermark (opacity 0.12, h=280) replacing SVG triangle
- All 3 new logo files copied to frontend/public/
- Plan C brainstormed, spec written: docs/superpowers/specs/2026-05-19-plan-c-design.md
- Plan C implementation plan written: docs/superpowers/plans/2026-05-19-plan-c-b2b-frontend.md (11 tasks)
- Key architecture: customer_type+company_name+inn snapshot in orders; switch-context B2C→B2B no password, B2B→B2C with password; userId reused for legalEntityId in B2B JWT; useDisplayPrice hook for price by clientType
- Decision: docs committed to ecommerce-platform repo (not Obsidian) — to revisit
- Next session: execute Plan C via subagent-driven approach, starting with Task 1 (Flyway migration)

## 2026-05-18 (evening — manual testing)
- Rebuilt user-service Docker image with Plan B SecurityConfig changes
- Gateway restarted in IntelliJ to apply JwtAuthenticationFilter + application.yml changes
- Bugs found and fixed:
  - LegalEntityBankAccount/LegalEntityAddress: `primary` field missing `@Column(name = "is_primary")` — PostgreSQL reserved word conflict
  - SecurityConfig: missing public paths for `/api/v1/legal-entities/authenticate`, `/api/v1/legal-entities/link-status/**`, GET `/{id}` (needed for internal auth-service calls)
  - JwtAuthenticationFilter: missing `/api/v1/legal-entities/authenticate` in PUBLIC_PATHS
- Full B2B flow verified end-to-end:
  - Legal entity registration via gateway → 200 OK
  - B2B login (`/api/v1/auth/login/legal`) → JWT with clientType=B2B
  - Products with B2B token → wholesalePrice field present (null until 1C import)
  - switch-context B2C→B2B → new JWT with clientType=B2B

## 2026-05-18
- Plan B brainstormed and designed: auth-service B2B login/switch-context, gateway headers, product-service wholesalePrice, integration-service dual price, notification-service legal-entity-events
- Key decisions: JWT unified structure (sub=id, clientType=B2C|B2B), switch-context one-directional (user→legal, reverse requires re-auth), "Оптовая"→price (B2B), "Розничная"→wholesalePrice (B2C)
- Fix identified: LegalEntityEvent missing token field — added to plan as Task 1
- Spec: docs/superpowers/specs/2026-05-18-plan-b-auth-product-notification.md
- Plan: docs/superpowers/plans/2026-05-18-plan-b-auth-product-notification.md (8 tasks)
- architecture.md updated with full project structure (all services, packages, files)
- Implemented Plan A (user-service B2B legal entity support) — 11 tasks, all tests green (71 total)
- Task 1: Flyway migration V20260517180000 — 4 tables (legal_entities, user_legal_entities, legal_entity_bank_accounts, legal_entity_addresses)
- Task 2: VerificationStatus, LinkStatus enums + LegalEntityEvent Kafka record
- Task 3: JPA entities — LegalEntity, LegalEntityBankAccount, LegalEntityAddress, UserLegalEntity, UserLegalEntityId
- Task 4: 4 repositories (LegalEntityRepository, BankAccountRepository, AddressRepository, UserLegalEntityRepository)
- Task 5: DTOs (RegisterLegalEntityRequest, LegalEntityDto, BankAccountDto, etc.) + LegalEntityMapper
- Task 6: LegalEntityKafkaProducerService + separate KafkaTemplate<String, LegalEntityEvent> bean + topic config
- Task 7: 3 exceptions (NotFound, AlreadyExists, NotVerified) + handlers in GlobalExceptionHandler + existsByEmail in UserRepository
- Task 8: LegalEntityService (TDD) — 7 unit tests passing
- Task 9: 3 REST controllers (LegalEntityController, LegalEntityAdminController, UserLegalEntityController)
- Task 10: LegalEntityControllerTest — 5 HTTP tests passing
- Task 11: Full build — 71 tests, 0 failures, BUILD SUCCESS
- Branch: feature/order-service-refactor — ready to merge into master
- Next: Plan B in new branch (auth-service login/register for legal entities + product-service wholesalePrice)

## 2026-05-17 (evening)
- Completed order-service refactor: all 11 steps done, tests green, committed
- Fixed test: shouldThrowWhenNotCreatedStatus — initiatePayment → confirmOrder (status machine change)
- Brainstormed and designed B2B/B2C client separation — full design doc approved
- Spec: `docs/superpowers/specs/2026-05-17-b2b-b2c-separation-design.md`
- Plan A (user-service): `docs/superpowers/plans/2026-05-17-b2b-legal-entity-user-service.md`
- Wiki: created index.md, plan-b2b-legal-entity.md
- Next: implement Plan A — user-service B2B legal entity support (11 tasks)

## 2026-05-17
- Finalized order-service business logic: discussed B2B/B2C flows, CREATED as technical status, confirmOrder sends to 1С
- OrderStatus.java: added INVOICE_SENT, renamed PROCESSING→"В работе", AWAITING_CONFIRMATION→"Ожидает подтверждения оплаты"
- OrderService: rewrote ALLOWED_TRANSITIONS (11 transitions), CANCELLABLE_STATUSES (client-only: CREATED/PROCESSING/INVOICE_SENT/PENDING_PAYMENT/PAYMENT_FAILED), removed 1С Kafka from createOrder, removed auto PAID→PROCESSING from confirmPayment, added confirmOrder method
- OrderController: added POST /api/v1/orders/{orderId}/confirm endpoint
- CreateOrderRequest + Order entity: added pickupRecipientName/pickupRecipientPhone for PICKUP orders
- OrderMapper: maps pickup recipient fields
- Order1CKafkaProducer: uses pickup fields when deliveryAddress is null
- Flyway V20260517120000: adds pickup_recipient columns + updates status CHECK constraint with INVOICE_SENT
- EmailService: added sendInvoiceSentEmail, sendAwaitingConfirmationEmail
- OrderHandler: INVOICE_SENT and AWAITING_CONFIRMATION now send specific emails instead of generic status-change email
- ClientLayout: added wordmark next to logo — "РФснаб" (Unbounded bold, navy) + "комплексное снабжение" (small uppercase, gray)
- plan-order-service-refactor.md: fully updated with finalized business logic, B2B/B2C flows, status table with 1С mapping, future B2B/B2C entity separation task noted

## 2026-05-16 (evening)
- Centralized company config: created frontend/src/config/company.ts with all company data (ООО «МСВ», INN, OGRN, phones, email, address, hours, description)
- Replaced all hardcoded company data in ClientLayout, AboutPage, ContactsPage, PersonalDataPage, PrivacyPolicyPage, LoginPage with company.ts references
- React.lazy + Suspense code splitting for all 19 page/feature routes in App.tsx
- Skeleton loaders: CatalogPage (4-col grid, 12 cards), ProductPage (3-col layout), OrdersPage (list), CartPage (2-col grid)
- Optimistic UI in cartStore: addItem/updateQuantity/removeItem update UI immediately with rollback on error
- Improved empty states: CartPage and OrdersPage now teach the interface with B2B-specific copy (44-ФЗ, payment by invoice)
- CheckoutPage split: 657-line monolith → DeliveryStep, RecipientStep, PaymentStep, SummaryStep (~344 lines remaining)
- ErrorBoundary class component added, wraps all routes in App.tsx
- ProfilePage: calls restoreSession() after profile save so header username updates immediately
- Build fixes: antd 6 Divider orientation→titlePlacement, missing phone field in JWT user fallback, unused imports

## 2026-05-15 (continued — gap fixes)
- AuthLayout created: /login and /register moved out of ClientLayout into separate AuthLayout (no header/footer)
- LoginPage: removed `margin: '0 -48px'` hack, added Юр/Физ account type toggle, "Забыли пароль?" link, 152-ФЗ security notice, inline TriMark SVG logo
- RegisterPage: removed `margin: '0 -48px'` hack (now uses minHeight: 100vh correctly)
- ClientLayout: full 3-level header — TopBar (36px navy, address+phone+links) + Main (76px, TriMark logo+wordmark, search input, cart/favorites/user actions) + Categories bar (46px, red catalog button + nav links + availability count). Footer: 5-column grid (#1A1A1A background), columns: brand+description, catalog, company, buyers, contacts
- index.css: added full `.rf-*` utility classes (rf-btn, rf-btn-primary/secondary/ghost/quiet/sm/lg, rf-badge, rf-badge-success/warn/red/navy/neutral/dot, rf-card, rf-input, rf-label, rf-mono, rf-tabular)
- ProductCard: price color fixed (brand-red → ink-1 per design reference), added favorite heart button, SKU line, isFeatured "Хит" badge, removed AntD Badge.Ribbon
- CatalogPage: grid changed 3→4 columns per design reference

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
