import { useMemo, useState } from 'react';
import { App, AutoComplete, Form } from 'antd';
import { useQuery } from '@tanstack/react-query';
import PointsMap from '@/components/maps/PointsMap';
import { geocodeAddress, suggestAddress } from '@/api/yandexGeo';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { YANDEX_GEOCODER_KEY, YANDEX_SUGGEST_KEY } from '@/lib/ymaps';

const SUGGEST_DEBOUNCE_MS = 400;
const SUGGEST_MIN_LENGTH = 3;

interface AddressSuggestProps {
    /** Имена полей формы, которые заполняются после выбора подсказки */
    fields: { city: string; street: string; building: string; postalCode: string };
}

/**
 * Поиск адреса через Yandex Geosuggest: после выбора подсказки один вызов Геокодера заполняет
 * город/улицу/дом/индекс в форме и показывает точку на карте. Без ключа или при ошибке Яндекса
 * не рендерит ничего — остаются обычные ручные поля (они всегда редактируемы).
 */
const AddressSuggest = ({ fields }: AddressSuggestProps) => {
    const form = Form.useFormInstance();
    const { message: messageApi } = App.useApp();
    const [text, setText] = useState('');
    const [point, setPoint] = useState<{ id: string; latitude: number; longitude: number } | null>(null);
    const debounced = useDebouncedValue(text.trim(), SUGGEST_DEBOUNCE_MS);

    const { data: suggestions = [] } = useQuery({
        queryKey: ['addressSuggest', debounced],
        queryFn: () => suggestAddress(debounced),
        enabled: debounced.length >= SUGGEST_MIN_LENGTH,
        staleTime: 5 * 60 * 1000,
    });

    const mapPoints = useMemo(() => (point ? [point] : []), [point]);

    if (!YANDEX_SUGGEST_KEY || !YANDEX_GEOCODER_KEY) return null;

    const handleSelect = async (query: string) => {
        const geo = await geocodeAddress(query);
        if (!geo) {
            messageApi.warning('Не удалось определить адрес, заполните поля вручную');
            return;
        }
        form.setFieldsValue({
            [fields.city]: geo.city ?? '',
            [fields.street]: geo.street ?? '',
            [fields.building]: geo.building ?? '',
            [fields.postalCode]: geo.postalCode ?? '',
        });
        setPoint({ id: query, latitude: geo.latitude, longitude: geo.longitude });
    };

    return (
        <Form.Item label="Найти адрес">
            <AutoComplete
                value={text}
                placeholder="Например: Новосибирск, Ленина 1"
                options={suggestions.map((s) => ({
                    value: s.query,
                    label: <>{s.title} <span style={{ color: 'var(--ink-3)' }}>{s.subtitle}</span></>,
                }))}
                onChange={setText}
                onSelect={handleSelect}
            />
            {point && (
                <div style={{ marginTop: 8 }}>
                    <PointsMap points={mapPoints} height={200} />
                </div>
            )}
        </Form.Item>
    );
};

export default AddressSuggest;
