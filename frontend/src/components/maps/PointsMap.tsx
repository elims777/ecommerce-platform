import { useMemo } from 'react';
import type { CSSProperties } from 'react';
import { Grid } from 'antd';
import { useYmaps } from '@/lib/ymaps';

/** Точка на карте */
export interface MapPoint {
    id: number | string;
    latitude: number;
    longitude: number;
}

interface PointsMapProps {
    points: MapPoint[];
    selectedId?: number | string | null;
    /** Клик по метке */
    onSelect?: (id: number | string) => void;
    /** Если задан — метки перетаскиваются, кластеризации нет; получает новые координаты */
    onDragEnd?: (latitude: number, longitude: number) => void;
    height?: number;
}

type LngLat = [number, number];

const pinStyle = (selected: boolean, clickable: boolean): CSSProperties => ({
    width: selected ? 22 : 14,
    height: selected ? 22 : 14,
    borderRadius: '50%',
    background: selected ? 'var(--brand-green, #1a7f37)' : '#e5484d',
    border: '2px solid #fff',
    boxShadow: '0 1px 4px rgba(0,0,0,.4)',
    transform: 'translate(-50%, -50%)',
    cursor: clickable ? 'pointer' : 'default',
});

const clusterStyle: CSSProperties = {
    ...pinStyle(false, true),
    width: 30,
    height: 30,
    background: '#1677ff',
    color: '#fff',
    fontSize: 12,
    lineHeight: '26px',
    textAlign: 'center',
};

/**
 * Карта Yandex Maps 3 с метками (кластеризация, если модуль доступен). Не рендерит ничего, пока карта
 * не загрузилась, нет ключа, загрузка не удалась или нет точек — рядом должен быть список/поля.
 */
const PointsMap = ({ points, selectedId, onSelect, onDragEnd, height = 300 }: PointsMapProps) => {
    const kit = useYmaps();
    // На узких экранах карта не перехватывает скролл страницы: без drag/scrollZoom, остаётся pinchZoom (двумя пальцами)
    const narrow = !Grid.useBreakpoint().sm;

    const location = useMemo(() => {
        if (points.length === 0) return null;
        const selected = points.find((p) => p.id === selectedId);
        const single = selected ?? (points.length === 1 ? points[0] : null);
        if (single) return { center: [single.longitude, single.latitude] as LngLat, zoom: 15 };
        const lngs = points.map((p) => p.longitude);
        const lats = points.map((p) => p.latitude);
        const bounds: [LngLat, LngLat] = [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]];
        if (bounds[0][0] === bounds[1][0] && bounds[0][1] === bounds[1][1]) return { center: bounds[0], zoom: 15 };
        return { bounds };
    }, [points, selectedId]);

    const features = useMemo(() => points.map((p) => ({
        type: 'Feature' as const,
        id: String(p.id),
        geometry: { type: 'Point' as const, coordinates: [p.longitude, p.latitude] as LngLat },
    })), [points]);

    const byId = useMemo(() => new Map(points.map((p) => [String(p.id), p])), [points]);

    const method = useMemo(() => kit?.clusterByGrid?.({ gridSize: 64 }), [kit]);

    if (!kit || !location) return null;
    const { YMap, YMapDefaultSchemeLayer, YMapDefaultFeaturesLayer, YMapMarker, YMapClusterer } = kit;

    const renderMarker = (p: MapPoint) => (
        <YMapMarker
            key={p.id}
            coordinates={[p.longitude, p.latitude]}
            draggable={!!onDragEnd}
            onDragEnd={onDragEnd && ((c: LngLat) => onDragEnd(c[1], c[0]))}
            onClick={onSelect && (() => onSelect(p.id))}
        >
            <div style={pinStyle(p.id === selectedId, !!onSelect)} />
        </YMapMarker>
    );

    return (
        <div style={{ width: '100%', height, borderRadius: 8, overflow: 'hidden' }}>
            <YMap location={location} behaviors={narrow && !onDragEnd ? ['pinchZoom'] : undefined}>
                <YMapDefaultSchemeLayer />
                <YMapDefaultFeaturesLayer />
                {YMapClusterer && method && !onDragEnd ? (
                    <YMapClusterer
                        method={method}
                        features={features}
                        marker={(f: { id: string }) => renderMarker(byId.get(f.id)!)}
                        cluster={(coordinates: LngLat, items: unknown[]) => (
                            <YMapMarker key={`${String(coordinates)}-${items.length}`} coordinates={coordinates}>
                                <div style={clusterStyle}>{items.length}</div>
                            </YMapMarker>
                        )}
                    />
                ) : points.map(renderMarker)}
            </YMap>
        </div>
    );
};

export default PointsMap;
