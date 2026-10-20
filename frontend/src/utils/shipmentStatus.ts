import type { ShipmentStatusCode } from '@/types/delivery';

/** Подпись, цвет тега antd и шаг прогресса (1–4) по коду статуса; у CANCELLED/PROBLEM/NOT_FOUND прогресса нет. Порядок — как в фильтре. */
export const SHIPMENT_STATUS: Record<ShipmentStatusCode, { label: string; color?: string; step: number | null }> = {
    CREATED: { label: 'Заказ создан', color: 'gold', step: 1 },
    IN_TRANSIT: { label: 'В пути', color: 'blue', step: 2 },
    ARRIVED_AT_TERMINAL: { label: 'Прибыл на терминал', color: 'green', step: 3 },
    DELIVERED: { label: 'Доставлен', step: 4 },
    CANCELLED: { label: 'Отменён', color: 'default', step: null },
    PROBLEM: { label: 'Проблема', color: 'orange', step: null },
    NOT_FOUND: { label: 'Не найдена', color: 'red', step: null },
};
