/**
 * Яндекс.Метрика — отложенная загрузка с отсевом ботов.
 *
 * Счётчика нет в index.html: скрипт вставляется из JS после первого
 * взаимодействия пользователя. Боты, которые не кликают и не скроллят,
 * счётчик не запускают и в статистику не попадают.
 */

const COUNTER_ID = import.meta.env.VITE_YM_ID as string | undefined;

const BOT_UA =
    /bot|crawl|spider|slurp|headless|phantom|lighthouse|googlebot|bingbot|ahrefs|semrush|mj12|dotbot|petalbot|python-requests|curl|wget|scrapy|go-http/i;

const INTERACTION_EVENTS = ['pointerdown', 'keydown', 'scroll', 'touchstart'] as const;

/** Резервный запуск, если взаимодействия так и не было (мс). */
const FALLBACK_DELAY = 4000;

declare global {
    interface Window {
        ym?: (id: number, action: string, ...args: unknown[]) => void;
        dataLayer?: unknown[];
    }
}

let started = false;
/** Хиты, накопленные до фактической загрузки скрипта. */
let pendingHit: string | null = null;
/** Цели, достигнутые до фактической загрузки скрипта. */
const pendingGoals: string[] = [];

const isBot = (): boolean => {
    const nav = window.navigator;
    if (nav.webdriver) return true;
    if (BOT_UA.test(nav.userAgent)) return true;
    if (!nav.languages || nav.languages.length === 0) return true;
    return false;
};

const injectCounter = () => {
    const id = Number(COUNTER_ID);

    window.dataLayer = window.dataLayer || [];

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

    if (pendingHit) {
        window.ym(id, 'hit', pendingHit);
        pendingHit = null;
    }

    pendingGoals.splice(0).forEach((goal) => window.ym?.(id, 'reachGoal', goal));
};

/**
 * Ставит счётчик в очередь на загрузку по первому взаимодействию.
 * Повторные вызовы игнорируются.
 */
export const initMetrika = () => {
    if (started) return;
    if (!import.meta.env.PROD) return;
    if (!COUNTER_ID) return;
    if (isBot()) return;

    started = true;

    const start = () => {
        window.clearTimeout(timerId);
        INTERACTION_EVENTS.forEach((event) => window.removeEventListener(event, start));
        injectCounter();
    };

    const timerId = window.setTimeout(start, FALLBACK_DELAY);
    INTERACTION_EVENTS.forEach((event) =>
        window.addEventListener(event, start, { once: true, passive: true }),
    );
};

/** Просмотр страницы при смене роута в SPA. */
export const trackPageView = (url: string) => {
    if (!started) return;
    if (window.ym) {
        window.ym(Number(COUNTER_ID), 'hit', url);
    } else {
        pendingHit = url;
    }
};

/** Достижение цели. До загрузки tag.js цель ждёт в очереди. */
export const reachGoal = (name: string) => {
    if (!started) return;
    if (window.ym) {
        window.ym(Number(COUNTER_ID), 'reachGoal', name);
    } else {
        pendingGoals.push(name);
    }
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
