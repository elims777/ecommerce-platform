import { useMemo, useState } from 'react';
import { Input, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import PointsMap from '@/components/maps/PointsMap';

const { Text } = Typography;

/** Максимум строк в списке: при тысячах точек (СДЭК в Москве) рисуем только первые, остальные находятся поиском */
const MAX_LIST_ROWS = 100;

/** Точка для выбора: пункт самовывоза или терминал ТК */
export interface PickerPoint {
    id: number;
    title: string;
    address: string;
    latitude?: number | null;
    longitude?: number | null;
}

interface PointsPickerProps {
    points: PickerPoint[];
    /** Подключается к Form.Item автоматически */
    value?: number;
    onChange?: (id: number) => void;
    /** Вместо «Ничего не найдено», когда точек нет вовсе (например, «Сначала выберите город») */
    emptyHint?: string;
}

/**
 * Карта с метками + список с поиском по адресу. Выбор кликом по метке или по строке.
 * Работает как контрол Form.Item (value/onChange). Если карта недоступна, остаётся список.
 */
const PointsPicker = ({ points, value, onChange, emptyHint }: PointsPickerProps) => {
    const [search, setSearch] = useState('');
    // Новый набор точек — поиск по старому набору не актуален (подстройка состояния при рендере)
    const [prevPoints, setPrevPoints] = useState(points);
    if (points !== prevPoints) {
        setPrevPoints(points);
        setSearch('');
    }

    const filtered = useMemo(() => {
        const q = search.trim().toLowerCase();
        return q ? points.filter((p) => `${p.title} ${p.address}`.toLowerCase().includes(q)) : points;
    }, [points, search]);

    const mapPoints = useMemo(() => points
        .filter((p) => p.latitude != null && p.longitude != null)
        .map((p) => ({ id: p.id, latitude: p.latitude!, longitude: p.longitude! })), [points]);

    return (
        <div>
            <PointsMap
                points={mapPoints}
                selectedId={value}
                onSelect={(id) => onChange?.(Number(id))}
            />
            <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder="Поиск по адресу"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                style={{ margin: '8px 0' }}
            />
            <div style={{ maxHeight: 260, overflowY: 'auto', border: '1px solid var(--line-2)', borderRadius: 6 }}>
                {filtered.slice(0, MAX_LIST_ROWS).map((p) => (
                    <div
                        key={p.id}
                        role="button"
                        tabIndex={0}
                        onClick={() => onChange?.(p.id)}
                        onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onChange?.(p.id); }}
                        style={{
                            padding: '8px 12px',
                            cursor: 'pointer',
                            background: p.id === value ? 'var(--brand-green-soft, #e6f4ea)' : undefined,
                            borderBottom: '1px solid var(--line-2)',
                        }}
                    >
                        <Text strong>{p.title}</Text>
                        <br />
                        <Text type="secondary">{p.address}</Text>
                    </div>
                ))}
                {filtered.length === 0 && <Text type="secondary" style={{ display: 'block', padding: 12 }}>{points.length === 0 && emptyHint ? emptyHint : 'Ничего не найдено'}</Text>}
            </div>
            {filtered.length > MAX_LIST_ROWS && (
                <Text type="secondary" style={{ fontSize: 12 }}>Показаны первые {MAX_LIST_ROWS} из {filtered.length}, уточните поиск</Text>
            )}
        </div>
    );
};

export default PointsPicker;
