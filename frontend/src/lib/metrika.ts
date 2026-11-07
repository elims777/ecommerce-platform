/**
 * Яндекс.Метрика — счётчик грузится сразу при старте приложения.
 *
 * Роботов отсекают два механизма: дешёвый префильтр по User-Agent/webdriver здесь
 * и настройка счётчика «фильтровать роботов по поведению» на стороне Метрики.
 * Отложенная загрузка по взаимодействию не используется: она теряла реальные
 * визиты с мгновенным отказом, из-за чего данные расходились с Директом.
 */

const COUNTER_ID = import.meta.env.VITE_YM_ID as string | undefined;

const BOT_UA =
    /bot|crawl|spider|slurp|headless|phantom|lighthouse|googlebot|bingbot|ahrefs|semrush|mj12|dotbot|petalbot|python-requests|curl|wget|scrapy|go-http/i;

declare global {
    interface Window {
        ym?: (id: number, action: string, ...args: unknown[]) => void;
        dataLayer?: unknown[];
    }
}

let started = false;

const isBot = (): boolean => {
    const nav = window.navigator;
    if (nav.webdriver) return true;
    if (BOT_UA.test(nav.userAgent)) return true;
    return !nav.languages || nav.languages.length === 0;
};

const injectCounter = () => {
    const id = Number(COUNTER_ID);

    window.dataLayer = window.dataLayer || [];

    // Очередь команд: tag.js разберёт её при загрузке, поэтому hit/reachGoal
    // можно отправлять сразу после init, не дожидаясь скрипта.
    window.ym =
        window.ym ||
        function (...args: unknown[]) {
            (window.ym as unknown as { a: unknown[][] }).a =
                (window.ym as unknown as { a?: unknown[][] }).a || [];
            (window.ym as unknown as { a: unknown[][] }).a.push(args);
        };

    const script = document.createElement('script');
    script.async = true;
    script.src = 'https://mc.yandex.ru/metrika/tag.js';
    document.head.appendChild(script);

    window.ym(id, 'init', {
        clickmap: true,
        trackLinks: true,
        accurateTrackBounce: true,
        webvisor: true,
        ecommerce: 'dataLayer',
    });
};

/** Инициализирует счётчик. Повторные вызовы игнорируются. */
export const initMetrika = () => {
    if (started) return;
    if (!import.meta.env.PROD) return;
    if (!COUNTER_ID) return;
    if (isBot()) return;

    started = true;
    injectCounter();
};

/** Просмотр страницы при смене роута в SPA. */
export const trackPageView = (url: string) => {
    if (!started) return;
    window.ym?.(Number(COUNTER_ID), 'hit', url);
};

/** Достижение цели. */
export const reachGoal = (name: string) => {
    if (!started) return;
    window.ym?.(Number(COUNTER_ID), 'reachGoal', name);
};

/** Метрика режет контейнер ecommerce на 8192 символах; берём с запасом. */
const MAX_ECOMMERCE_LENGTH = 8000;

type EcommerceAction = { products?: unknown[]; actionField?: { id?: unknown } };

const push = (ecommerce: Record<string, unknown>) => {
    window.dataLayer = window.dataLayer || [];
    window.dataLayer.push({ ecommerce: { currencyCode: 'RUB', ...ecommerce } });
};

const fits = (ecommerce: Record<string, unknown>) =>
    JSON.stringify({ ecommerce: { currencyCode: 'RUB', ...ecommerce } }).length <= MAX_ECOMMERCE_LENGTH;

/**
 * Событие электронной коммерции (detail / add / remove / purchase) в формате Яндекса.
 * Если контейнер не влезает в лимит, products делятся на части; у purchase id части
 * получает суффикс `-N` (подномера заказа по правилам Яндекса).
 */
export const pushEcommerce = (ecommerce: Record<string, unknown>) => {
    if (!started) return;

    const [kind, action] = Object.entries(ecommerce)[0] as [string, EcommerceAction | undefined];
    const products = action?.products;
    if (fits(ecommerce) || !products || products.length < 2) {
        push(ecommerce);
        return;
    }

    // Жадно набираем части: каждая вмещает максимум товаров (минимум один).
    const withProducts = (id: unknown, chunk: unknown[]) => ({
        [kind]: {
            ...action,
            ...(kind === 'purchase' ? { actionField: { ...action?.actionField, id } } : {}),
            products: chunk,
        },
    });
    const chunks: unknown[][] = [];
    let chunk: unknown[] = [];
    for (const product of products) {
        if (chunk.length > 0 && !fits(withProducts(`${action?.actionField?.id}-${chunks.length + 1}`, [...chunk, product]))) {
            chunks.push(chunk);
            chunk = [];
        }
        chunk.push(product);
    }
    chunks.push(chunk);

    chunks.forEach((part, index) =>
        push(withProducts(`${action?.actionField?.id}-${index + 1}`, part)),
    );
};
