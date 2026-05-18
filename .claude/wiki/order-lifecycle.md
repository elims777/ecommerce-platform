# Order Lifecycle — Жизненный цикл заказа

Last updated: 2026-05-17

## Статусы

| Статус | Отображение | Сценарий | В 1С |
|--------|-------------|----------|------|
| `CREATED` | Заказ создан | Технический: клиент перешёл к оформлению, заполняет форму. Можно редактировать. В 1С не передаётся. | — |
| `PROCESSING` | В работе | Клиент нажал "Подтвердить заказ" → заказ отправлен в 1С через Kafka с полными данными. | В работе |
| `INVOICE_SENT` | Счёт выставлен | 1С выставила счёт и отправила клиенту на email. Статус приходит автоматом из 1С через integration-service. | Счёт выставлен |
| `PENDING_PAYMENT` | Ожидает оплаты | B2B предоплата: клиент оплачивает счёт. B2C: ожидание оплаты через платёжный шлюз (QR/карта). | Ожидает оплаты |
| `AWAITING_CONFIRMATION` | Ожидает подтверждения оплаты | B2B постоплата: товар отгружается, оплата — после доставки. | Ожидает подтверждения оплаты |
| `PAID` | Оплачен | Оплата получена и подтверждена. | Оплачен |
| `PAYMENT_FAILED` | Ошибка оплаты | B2C: платёж не прошёл. Клиент может повторить попытку (кнопка "Оплатить"). Внутренний статус. | — |
| `SHIPPED` | Отгружен | Товар передан в доставку. **После этого статуса клиент не может отменить.** | Отгружен |
| `IN_TRANSIT` | В пути | Товар у перевозчика, есть трекинг-номер. | В пути |
| `DELIVERED` | Доставлен | Товар получен клиентом. | Доставлен |
| `CANCELLED` | Отменён | Отменён до отгрузки. | Отменён |
| `REFUNDED` | Возврат средств | Возврат после отмены оплаченного заказа. | Возврат средств |

---

## Сценарии переходов

### B2B предоплата (счёт → оплата → отгрузка)
```
CREATED  (клиент заполняет форму)
  └─► PROCESSING       (клиент нажал "Подтвердить", отправлен в 1С)
        └─► INVOICE_SENT    (1С выставила и отправила счёт — автомат из 1С)
              └─► PENDING_PAYMENT  (менеджер выбрал предоплату)
                    └─► PAID
                          └─► SHIPPED
                                └─► IN_TRANSIT
                                      └─► DELIVERED
```

### B2B постоплата (отгрузка → оплата по факту)
```
CREATED
  └─► PROCESSING
        └─► INVOICE_SENT
              └─► AWAITING_CONFIRMATION  (менеджер выбрал постоплату)
                    └─► SHIPPED
                          └─► IN_TRANSIT
                                └─► DELIVERED
                                      └─► PAID   (оплата после получения)
```

### B2C (QR/карта — платёжный шлюз)
```
CREATED
  └─► PROCESSING       (клиент подтвердил)
        └─► PENDING_PAYMENT   (клиент нажал "Перейти к оплате")
              ├─► PAID
              │     └─► SHIPPED → IN_TRANSIT → DELIVERED
              └─► PAYMENT_FAILED
                    └─► PENDING_PAYMENT  (повтор попытки)
```

### Отмена
```
Клиент сам (CANCELLABLE_STATUSES):
  CREATED / PROCESSING / INVOICE_SENT / PENDING_PAYMENT / PAYMENT_FAILED
    └─► CANCELLED → REFUNDED

Только менеджер:
  AWAITING_CONFIRMATION / PAID и далее
    └─► CANCELLED → REFUNDED
```

---

## Все допустимые переходы (ALLOWED_TRANSITIONS)

```
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

---

## Взаимодействие сервисов

### Создание заказа (корзина → форма)
```
Клиент нажал "Оформить заказ"
  → POST /api/v1/orders  (order-service)
      → Валидация корзины (Redis)
      → Получение данных товаров (product-service, REST)
      → Сохранение заказа в БД (статус: CREATED)
      → Kafka: order-events → notification-service (email "заказ оформлен")
      ← НЕ отправляется в 1С
```

### Подтверждение заказа (CREATED → PROCESSING)
```
Клиент нажал "Подтвердить заказ" (все данные заполнены)
  → POST /api/v1/orders/{id}/confirm  (order-service)
      → Статус: PROCESSING
      → Kafka: order-events → notification-service
      → Kafka: order-1c-export → integration-service
          → HTTP: CommerceML POST → 1С УНФ (с полными данными заказа)
```

### Счёт выставлен (PROCESSING → INVOICE_SENT) — автомат из 1С
```
Менеджер в 1С оформил и отправил счёт клиенту
  → 1С инициирует обмен (CommerceML)
      → integration-service получает обновление
          → PATCH /api/v1/orders/{id}/1c-sync  (order-service)
              → Статус: INVOICE_SENT, externalId из 1С сохранён
              → Kafka: order-events → notification-service
                  → Email клиенту: "Счёт выставлен, ожидайте письма со счётом"
```

### Оплата подтверждена (→ PAID)
```
B2B: менеджер фиксирует оплату в 1С
  → 1С отправляет обновление статуса через CommerceML
      → integration-service → PATCH /orders/{id}/1c-sync → PAID

B2C: платёжный шлюз (будущий payment-service)
  → Webhook от банка → payment-service → Kafka → order-service → PAID
```

---

## Правила отмены

- Клиент сам отменяет: `CREATED`, `PROCESSING`, `INVOICE_SENT`, `PENDING_PAYMENT`, `PAYMENT_FAILED`
- `AWAITING_CONFIRMATION` и позже — только менеджер через admin panel
- При отмене оплаченного заказа → `CANCELLED` → `REFUNDED`
- `syncFrom1C()` — меняет статус без проверки `ALLOWED_TRANSITIONS` (только для integration-service)

---

## Ключевые файлы

| Файл | Назначение |
|------|-----------|
| `order-service/.../enums/OrderStatus.java` | Enum всех статусов с displayName |
| `order-service/.../service/OrderService.java` | ALLOWED_TRANSITIONS, CANCELLABLE_STATUSES, confirmOrder, вся бизнес-логика |
| `order-service/.../controller/OrderController.java` | REST endpoints, включая POST /confirm |
| `order-service/.../mapper/OrderMapper.java` | Начальный статус CREATED, маппинг pickup recipient |
| `order-service/.../kafka/Order1CKafkaProducer.java` | Сборка Order1CExportEvent, поддержка PICKUP recipient |
| `integration-service/.../OrderStatusImportService.java` | Парсинг статусов из 1С CommerceML |
| `notification-service/.../OrderHandler.java` | Email уведомления по статусам (INVOICE_SENT, AWAITING_CONFIRMATION, и др.) |
| `notification-service/.../EmailService.java` | Шаблоны писем |

---

## Kafka топики

| Топик | Producer | Consumer | Когда |
|-------|----------|----------|-------|
| `order-events` | order-service | notification-service | Создание, смена статуса, отмена |
| `order-1c-export` | order-service | integration-service | Только при confirmOrder (CREATED → PROCESSING) |
