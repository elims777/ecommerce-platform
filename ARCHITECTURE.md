# ARCHITECTURE.md — РФСнаб ecommerce platform

Последнее обновление: 2026-06-17

---

## Стек

| Слой | Технология |
|---|---|
| Язык | Java 21 (virtual threads) |
| Фреймворк | Spring Boot 3.x |
| База данных | PostgreSQL 16 + Flyway |
| Кэш | Redis 7 |
| Очередь | Apache Kafka |
| Фронтенд | React + TypeScript + Ant Design 5 |
| Контейнеры | Docker + Docker Compose |
| Шлюз | Spring Cloud Gateway |

---

## Сервисы

| Сервис | Порт | DB порт | Описание |
|---|---|---|---|
| gateway-service | 8080 | — | JWT-фильтр, маршрутизация |
| auth-service | 9000 | 5432 (user-db) | JWT, регистрация, email-верификация, B2B login |
| user-service | 8081 | 5432 (user-db) | Профиль, юрлица, избранное |
| product-service | 8083 | 5435 (product-db) | Каталог, категории, импорт, картинки |
| order-service | 8084 | 5436 (order-db) | Корзина (Redis), заказы, state machine |
| payment-service | 8090 | 5438 (payment-db) | Tochka Bank, CARD/SBP |
| notification-service | 8082 | 5434 (notification-db) | Kafka consumers, email |
| integration-service | 8085 | 5437 (integration-db) | 1С CommerceML, ФТК XML/XLS импорт |
| frontend | 80 | — | React SPA, nginx |

---

## Модели данных (product-service)

### products
- `id`, `external_id`, `name`, `slug`, `sku`
- `price`, `wholesale_price`, `stock_quantity`
- `category_id` → categories
- `is_active`, `is_featured`, `is_variant_child`, `parent_product_id`
- `source` (INTERNAL / FTK)
- `material`, `unit_of_measure`, `vat_rate`, `barcode`, `country_of_origin`
- `short_description`, `description`

Варианты — дочерние записи в той же таблице (`is_variant_child=true`, `parent_product_id`).

### categories
- `id`, `external_id` (UUID из 1С/ФТК), `name`, `slug`
- `parent_category_id` (self-reference), `display_order`, `is_active`

### product_attributes
- `id`, `product_id`, `attribute_name`, `attribute_value`

---

## Интеграция с 1С (CommerceML 2.08)

**Протокол**: 1С сама обращается по HTTP к `integration-service` (`/1c-exchange/`).  
**Файлы**: `import.xml` (товары + классификатор), `offers.xml` (цены + остатки).

**Pipeline `CatalogImportService.processImport()`**:
1. Parse `import.xml` → `CommerceInfo` (JAXB)
2. Parse `offers.xml` → цены/остатки
3. Построить `ClassifierData` из `Classifier.groups` (рекурсивный walkGroups)
4. `FtkCategoryMapper.loadClassifier(data, "import-1c")` — создаёт дерево категорий в product-service
5. Для каждого товара: резолвинг `categoryId` по первому `groupId`
6. Merge товаров и офферов → `ProductImportItemDto[]`
7. Chunk (100 шт) + параллельная отправка в product-service (`/api/v1/products/import/batch`)
8. Постановка задач на обработку картинок

**Категории 1С**: создаются как дочерние от `import-1c` (slug), иерархия из Классификатора.

---

## Интеграция с ФТК (CommerceML 3.1)

**Источник**: FTP-сервер `31.44.91.154:21` (пассивный режим, порты от 65000).  
**Папка каталога**: `/webdata/000000003/goods/1/`  
**Запуск**: `POST /api/v1/integration/ftk/import-xml` или `@Scheduled` 03:00.

**Pipeline `FtkImportService.importFromFtp()`** — 6 стадий:
1. `parseClassifier` → `ClassifierData` (группы, свойства, ед. изм.)
2. `parseProducts` → `Map<uuid, ProductData>`
3. `parseOffers` → `Map<uuid, OfferData>`
4. `parsePrices` (StAX, 125 MB файл) → `Map<uuid, BigDecimal>`
5. `parseRests` → `Map<uuid, RestData>`
6. `assemble` → `List<FtkProduct>` (с вариантами)

**Категории ФТК**: создаются как дочерние от `ftk` (slug), иерархия из классификатора ФТК.

---

## FtkCategoryMapper (общий для 1С и ФТК)

```
loadClassifier(ClassifierData, rootSlug)  — инициализация перед импортом
resolveCategory(groupUuid)                — lazy upsert категории в product-service
resetCache()                              — очистка после импорта
```

- **ФТК**: `rootSlug = "ftk"`
- **1С**: `rootSlug = "import-1c"`
- Кэш `groupUuid → categoryId` живёт один сеанс импорта.

---

## Ключевые бизнес-правила

| Правило | Значение |
|---|---|
| `price` | B2B/оптовая (из 1С "Оптовая цена") |
| `wholesalePrice` | B2C/розничная (из 1С "Розничная цена") |
| ФТК цена | `price = wholesalePrice = ftk_price` (только розничная) |
| externalId 1С | UUID из 1С |
| externalId ФТК товар | `FTK-{article}` |
| externalId ФТК вариант | `FTK-{article.NNN}` |
| Варианты | Дочерние Product (is_variant_child=true) |
| Корзина | Только для авторизованных |

---

## Order state machine

```
CREATED → PROCESSING → ASSEMBLED → SHIPPED → DELIVERED → COMPLETED
        ↓                                               ↓
     CANCELLED                                      CANCELLED

Спецстатусы: INVOICE_SENT, AWAITING_CONFIRMATION, PENDING_PAYMENT, PAYMENT_FAILED, REFUNDED
```

---

## Flyway (последние миграции)

**product-service**:
- V20260616100000 — refactor_variants_to_products (варианты → дочерние Product)

**order-service**:
- V20260616120000 — drop variant_id from cart_items
