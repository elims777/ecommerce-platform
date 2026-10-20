import React, { useEffect, useState } from 'react';
import type { ComponentType, ReactNode } from 'react';
import ReactDOM from 'react-dom';

/** Ключ Yandex Maps JS API 3. Пусто — карты отключены. */
export const YANDEX_MAPS_KEY: string = import.meta.env.VITE_YANDEX_MAPS_KEY ?? '';
/** Ключ HTTP Геокодера. Пусто — геокодер отключён. */
export const YANDEX_GEOCODER_KEY: string = import.meta.env.VITE_YANDEX_GEOCODER_KEY ?? '';
/** Ключ Геосаджеста. Пусто — подсказки адреса отключены. */
export const YANDEX_SUGGEST_KEY: string = import.meta.env.VITE_YANDEX_SUGGEST_KEY ?? '';

/** Компонент, полученный из ymaps3 через reactify; props ymaps3 не типизируем — только то, что используем. */
type YComponent = ComponentType<Record<string, unknown> & { children?: ReactNode }>;

/** Компоненты карты, готовые к использованию в JSX. YMapClusterer есть, только если модуль кластеризации загрузился. */
export interface YmapsKit {
    YMap: YComponent;
    YMapDefaultSchemeLayer: YComponent;
    YMapDefaultFeaturesLayer: YComponent;
    YMapMarker: YComponent;
    YMapClusterer?: YComponent;
    clusterByGrid?: (options: { gridSize: number }) => unknown;
}

interface ReactifyModule { module: (m: unknown) => Record<string, unknown> }
interface Ymaps3Global {
    ready: Promise<void>;
    import: (name: string) => Promise<unknown>;
}

const CLUSTERER_MODULE = '@yandex/ymaps3-clusterer@0.0.1';

let kitPromise: Promise<YmapsKit | null> | null = null;

const loadScript = (src: string) => new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('ymaps3 script failed'));
    document.head.appendChild(script);
});

const loadKit = async (): Promise<YmapsKit | null> => {
    if (!YANDEX_MAPS_KEY) return null;
    try {
        await loadScript(`https://api-maps.yandex.ru/v3/?apikey=${YANDEX_MAPS_KEY}&lang=ru_RU`);
        const ymaps3 = (window as unknown as { ymaps3: Ymaps3Global }).ymaps3;
        const [reactifyPkg] = await Promise.all([
            ymaps3.import('@yandex/ymaps3-reactify') as Promise<{ reactify: { bindTo: (r: unknown, d: unknown) => ReactifyModule } }>,
            ymaps3.ready,
        ]);
        const reactify = reactifyPkg.reactify.bindTo(React, ReactDOM);
        const kit = reactify.module(ymaps3) as unknown as YmapsKit;
        try {
            const clusterer = reactify.module(await ymaps3.import(CLUSTERER_MODULE)) as unknown as YmapsKit;
            kit.YMapClusterer = clusterer.YMapClusterer;
            kit.clusterByGrid = clusterer.clusterByGrid;
        } catch {
            // Без кластеризации метки рисуются по одной — карта остаётся рабочей
        }
        return kit;
    } catch {
        // Нет сети/ключ неверный — карта просто не показывается, список работает
        return null;
    }
};

/**
 * Лениво загружает Yandex Maps JS API 3 при первом использовании (в общий бандл не попадает).
 * Возвращает null, пока грузится, а также если нет ключа или загрузка не удалась.
 */
export const useYmaps = (): YmapsKit | null => {
    const [kit, setKit] = useState<YmapsKit | null>(null);
    useEffect(() => {
        let alive = true;
        kitPromise ??= loadKit();
        kitPromise.then((k) => {
            if (!k && YANDEX_MAPS_KEY) kitPromise = null; // загрузка упала — следующий монтаж попробует снова
            if (alive) setKit(k);
        });
        return () => { alive = false; };
    }, []);
    return kit;
};
