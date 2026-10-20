import type { ShipmentDto } from '@/types/delivery';

/** Подпись даты доставки: «Доставлен ДД.ММ.ГГ» / «Доставка ≈ ДД.ММ.ГГ»; для отменённых и без даты — null. */
export const deliveryDateLabel = (s: ShipmentDto): string | null => {
    if (s.carrierStatusCode === 'CANCELLED') return null;
    const delivered = s.carrierStatusCode === 'DELIVERED';
    const iso = delivered ? s.deliveredAt : s.estimatedDeliveryAt;
    if (!iso) return null;
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return null;
    const day = date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit', timeZone: 'Europe/Moscow' });
    return `${delivered ? 'Доставлен' : 'Доставка ≈'} ${day}`;
};
