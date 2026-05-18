# РФСнаб — ecommerce B2B платформа

## Стек и архитектура
Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x, Maven монорепо.
Микросервисы общаются через Kafka и REST. Gateway маршрутизирует всё под `/api`.

| Сервис | Порт | Назначение |
|---|---|---|
| gateway-service | 8080 | Spring Cloud Gateway, JWT-фильтр, Redis rate limiting |
| auth-service | 9000 | JWT + Yandex OAuth2 |
| user-service | 8081 | Пользователи |
| product-service | 8083 | Товары, Yandex S3, WebP |
| order-service | — | Корзина (Redis), заказы, получатели |
| integration-service | — | 1С Fresh УНФ, CommerceML 2.08 |
| notification-service | — | Email через Kafka |

## БД и инфра
- Все сервисы: `user=user`, `password=secret` (psql: `-U user`)
- Flyway миграции — обязательно для всех изменений схемы
- Redis — корзина и rate limiting
- Kafka топики: `order.created`, `catalog.imported`, `user.registered`
- Docker Compose — основная среда разработки

## Java conventions
- Constructor injection везде, никаких `@Autowired` на полях
- `GlobalExceptionHandler` (`@RestControllerAdvice`) — в каждом сервисе
- Custom exceptions для business logic
- MapStruct для маппинга entity ↔ DTO, entity из контроллера не возвращать
- AOP logging — в каждом сервисе
- `@Transactional` — явно управлять границами транзакций
- `CompletableFuture` / virtual threads — там где реальный выигрыш в производительности
- Никаких magic numbers — только константы и enums
- SLF4J + Logback, sensitive data не логировать

## Auth / токены
- JWT: `userId` в subject, `email` в claims
- Токены возвращаются snake_case прямо на `/login` (без отдельного `/me`)
- Gateway форвардит `X-User-Email` header в downstream сервисы

## Frontend (React + TypeScript + Vite)
- Ant Design — UI компоненты, `ConfigProvider` для темы
- Framer Motion — анимации переходов между страницами
- Axios через `apiClient` с interceptors для токенов
- enum поля сериализуются как `{code, displayName}` — обрабатывать через `enumUtils.ts`
- State: Zustand (`authStore`, `cartStore`)
- Роутинг: React Router v6, `Outlet` в layouts

### Frontend: стили и анимации
**Анимации (Emil Kowalski):**
- `ease-out` для появления, `ease-in` для исчезновения, spring physics для интерактивных элементов
- Анимации UI < 300ms, кастомный `cubic-bezier` вместо стандартных CSS easing
- Анимировать только `transform` и `opacity` (GPU-ускорение), никогда layout-свойства
- `AnimatePresence` для условного рендеринга, `clip-path` для reveals
- `@media (hover: hover)` для hover-анимаций — защита от touch
- Вопрос перед анимацией: "она нужна?" — сдержанность это фича

**Качество дизайна (impeccable.style + tasteskill.dev):**
- НЕ использовать: Inter как дефолтный шрифт, purple градиенты, cards-в-cards, glassmorphism, bounce easing, серый текст на цветном фоне, emoji как заголовки секций
- Цвета: OKLCH tinted neutrals с тёплым base hue
- Типографика: `clamp()` для маркетинга, фиксированный rem для app/dashboard
- Сетка: 8px baseline grid, generous padding на интерактивных поверхностях
- Иерархия: не каждая кнопка primary — использовать ghost/text/secondary варианты
- Optimistic UI: обновлять немедленно, синхронизировать в фоне
- Empty states должны обучать интерфейсу, не просто "ничего нет"
- Один memorable визуальный элемент на экран

## Тестирование
- Integration tests: Testcontainers (PostgreSQL, Kafka, Redis)
- Gateway: WireMock + Testcontainers Redis
- Код должен быть testable — DI, без static методов

## Что НЕ делать
- Хардкодить credentials — только `application.yml` + env переменные
- `// TODO: implement` заглушки — базовая реализация должна быть полной
- N+1 проблема — явно управлять Lazy/Eager loading
- Пустые catch блоки — никогда
- Возвращать entity из контроллера — только DTO
- Не добавлять соавторство при коммитах
- Не добавлять md файлы и вики .claude в репозиторий


## Wiki — long-term memory

Wiki lives in `.claude/wiki/`. Read `index.md` + `log.md` (last 5 entries) at session start. Update affected pages after significant changes.

# CLAUDE.md — Instructions for Claude Code

## Auto-start

Read `.claude/wiki/log.md` (last 5 entries) and `.claude/wiki/index.md` at session start. Report: "Context loaded. Last session: [date]. Issues: [critical only]"

## Output style
No preamble. Tool result first. No explanations of actions.

## Language
**Communication with user:** Always respond in Russian (Русский язык)
**Documentation:** All code changes, comments, commit messages, wiki updates, and memory files must be in English.

**Exception:** Existing code comments in the project remain in Russian. Only change config files and wiki to English.

## Decision-making approach

**Do not make changes until 95% confident in the solution. Ask questions before that point.**

- Before changing any dependency version — verify the current stable version on PyPI first
- Before writing any non-trivial code — read the relevant files first
- If not 95% sure — ask, do not guess