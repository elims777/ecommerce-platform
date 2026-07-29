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
    }
}

let started = false;
/** Хиты, накопленные до фактической загрузки скрипта. */
let pendingHit: string | null = null;

const isBot = (): boolean => {
    const nav = window.navigator;
    if (nav.webdriver) return true;
    if (BOT_UA.test(nav.userAgent)) return true;
    if (!nav.languages || nav.languages.length === 0) return true;
    return false;
};

const injectCounter = () => {
    const id = Number(COUNTER_ID);

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
    });

    if (pendingHit) {
        window.ym(id, 'hit', pendingHit);
        pendingHit = null;
    }
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
