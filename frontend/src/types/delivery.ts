/** Коды транспортных компаний — совпадают с enum CarrierCode на бэкенде */
export type CarrierCode = 'CDEK' | 'DELLIN' | 'PEC' | 'BAIKAL' | 'VOZOVOZ';

export interface CarrierDto {
    code: CarrierCode;
    name: string;
    enabled: boolean;
    addressDeliveryEnabled: boolean;
    terminalDeliveryEnabled: boolean;
    sortOrder: number;
    apiKeyConfigured: boolean;
    terminalsSyncedAt: string | null;
    activeTerminalsCount: number;
    lastSyncReport: CarrierSyncReport | null;
    /** Шаблон ссылки отслеживания с плейсхолдером {number} */
    trackingUrlTemplate: string | null;
    /** false — ТК не отдаёт статусы по API, статус меняет менеджер вручную */
    statusTrackingSupported: boolean;
}

export type ShipmentStatusCode = 'IN_TRANSIT' | 'ARRIVED_AT_TERMINAL' | 'DELIVERED' | 'CANCELLED' | 'PROBLEM' | 'NOT_FOUND';
export type ShipmentDirection = 'OUTGOING' | 'INCOMING' | 'UNKNOWN';
export type ShipmentSource = 'MANUAL' | 'IMPORT';
export type ShipmentMode = 'SINGLE' | 'MULTIPLE';

/** Отправление (накладная ТК) */
export interface ShipmentDto {
    id: number;
    orderId: string | null;
    orderNumber: string | null;
    carrierCode: CarrierCode;
    carrierName: string;
    waybillNumber: string;
    direction: ShipmentDirection;
    sender: string | null;
    receiver: string | null;
    routeFrom: string | null;
    routeTo: string | null;
    shippedAt: string | null;
    estimatedDeliveryAt: string | null;
    /** Сырой текст статуса от ТК */
    carrierStatus: string | null;
    carrierStatusCode: ShipmentStatusCode | null;
    carrierStatusUpdatedAt: string | null;
    polledAt: string | null;
    /** «Нет движения» — опрос остановлен */
    pollingStopped: boolean;
    notFound: boolean;
    source: ShipmentSource;
    trackingUrl: string | null;
    createdAt: string;
}

export interface AddShipmentRequest {
    carrierCode: CarrierCode;
    waybillNumber: string;
    shipmentMode?: ShipmentMode;
}

export interface ShipmentFilters {
    carrier?: CarrierCode;
    direction?: ShipmentDirection;
    statusCode?: ShipmentStatusCode;
    city?: string;
    from?: string;
    to?: string;
    problem?: boolean;
    notFound?: boolean;
    stale?: boolean;
    awaitingMore?: boolean;
    page?: number;
    size?: number;
}

/** Тип точки ТК. Для СДЭК — PVZ и POSTAMAT, для остальных — TERMINAL */
export type PointType = 'TERMINAL' | 'PVZ' | 'POSTAMAT';

export interface CarrierTerminalDto {
    id: number;
    carrierCode: CarrierCode;
    pointType: PointType;
    city: string;
    name: string;
    address: string;
    latitude: number;
    longitude: number;
    workingHours: string | null;
    phone: string | null;
}

export type CarrierSyncStatus = 'OK' | 'WARNING' | 'ERROR' | 'NOT_CONFIGURED';

export interface CarrierSyncReport {
    carrierCode: CarrierCode;
    carrierName: string;
    status: CarrierSyncStatus;
    received: number;
    added: number;
    updated: number;
    deactivated: number;
    skipped: number;
    skippedItems: { externalId: string | null; reason: string }[];
    moreSkipped: number;
    message: string | null;
    durationMs: number;
    finishedAt: string;
}

export interface CarrierUpdateRequest {
    enabled: boolean;
    addressDeliveryEnabled: boolean;
    terminalDeliveryEnabled: boolean;
    sortOrder: number;
    trackingUrlTemplate?: string | null;
}

export interface WarehousePointRequest {
    name: string;
    city: string;
    street: string;
    building: string;
    postalCode?: string | null;
    phone: string;
    workingHours?: string;
    description?: string;
    latitude?: number | null;
    longitude?: number | null;
    active: boolean;
}

/** Показывается под выбором ТК при оформлении и редактировании заказа; в письмах не выводится (решение 2026-10-08). Текст согласован, не менять */
export const DELIVERY_DISCLAIMER =
    'Стоимость доставки не входит в сумму заказа и рассчитывается индивидуально после оформления. ' +
    'Межтерминальную перевозку и доставку до адреса оплачивает покупатель по тарифам выбранной ' +
    'транспортной компании — как правило, при получении груза. Отгрузка осуществляется с ближайшего ' +
    'к вам склада или производства. Менеджер свяжется с вами для согласования условий доставки; ' +
    'для крупных заказов возможна бесплатная доставка.';
