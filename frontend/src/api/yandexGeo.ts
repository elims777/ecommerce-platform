import { YANDEX_GEOCODER_KEY, YANDEX_SUGGEST_KEY } from '@/lib/ymaps';

/** Подсказка Геосаджеста */
export interface AddressSuggestion {
    title: string;
    subtitle: string;
    /** Полный адрес для запроса в Геокодер */
    query: string;
}

/** Результат геокодирования */
export interface GeocodedAddress {
    city: string;
    street: string;
    building: string;
    postalCode: string;
    latitude: number;
    longitude: number;
}

interface SuggestResponse {
    results?: { title: { text: string }; subtitle?: { text: string }; address?: { formatted_address?: string } }[];
}

interface GeocoderResponse {
    response: {
        GeoObjectCollection: {
            featureMember: {
                GeoObject: {
                    Point: { pos: string };
                    metaDataProperty: {
                        GeocoderMetaData: {
                            Address: { postal_code?: string; Components: { kind: string; name: string }[] };
                        };
                    };
                };
            }[];
        };
    };
}

/**
 * Подсказки адреса (Yandex Geosuggest). Без ключа или при любой ошибке возвращает пустой список —
 * пользователь продолжает вводить адрес вручную.
 */
export const suggestAddress = async (text: string): Promise<AddressSuggestion[]> => {
    if (!YANDEX_SUGGEST_KEY) return [];
    try {
        const url = `https://suggest-maps.yandex.ru/v1/suggest?apikey=${YANDEX_SUGGEST_KEY}&print_address=1&text=${encodeURIComponent(text)}`;
        const res = await fetch(url);
        if (!res.ok) return [];
        const data = (await res.json()) as SuggestResponse;
        return (data.results ?? []).map((r) => ({
            title: r.title.text,
            subtitle: r.subtitle?.text ?? '',
            query: r.address?.formatted_address ?? [r.title.text, r.subtitle?.text].filter(Boolean).join(', '),
        }));
    } catch {
        return [];
    }
};

/**
 * Геокодирует адрес (Yandex Geocoder, один запрос). Без ключа, при ошибке или если адрес не найден — null.
 */
export const geocodeAddress = async (query: string): Promise<GeocodedAddress | null> => {
    if (!YANDEX_GEOCODER_KEY) return null;
    try {
        const url = `https://geocode-maps.yandex.ru/1.x/?apikey=${YANDEX_GEOCODER_KEY}&format=json&results=1&geocode=${encodeURIComponent(query)}`;
        const res = await fetch(url);
        if (!res.ok) return null;
        const data = (await res.json()) as GeocoderResponse;
        const geo = data.response.GeoObjectCollection.featureMember[0]?.GeoObject;
        if (!geo) return null;
        const address = geo.metaDataProperty.GeocoderMetaData.Address;
        const part = (...kinds: string[]) =>
            kinds.map((k) => address.Components.find((c) => c.kind === k)?.name).find(Boolean) ?? '';
        const [longitude, latitude] = geo.Point.pos.split(' ').map(Number);
        return {
            city: part('locality', 'province'),
            street: part('street'),
            building: part('house'),
            postalCode: address.postal_code ?? '',
            latitude,
            longitude,
        };
    } catch {
        return null;
    }
};
