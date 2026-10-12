/** Коды транспортных компаний — совпадают с enum CarrierCode на бэкенде */
export type CarrierCode = 'CDEK' | 'DELLIN' | 'PEC' | 'BAIKAL' | 'VOZOVOZ';

export interface CarrierDto {
    code: CarrierCode;
    name: string;
    enabled: boolean;
    addressDeliveryEnabled: boolean;
    sortOrder: number;
}

export interface CarrierUpdateRequest {
    enabled: boolean;
    addressDeliveryEnabled: boolean;
    sortOrder: number;
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
