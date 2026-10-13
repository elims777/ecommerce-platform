import apiClient from './client';
import type { AddShipmentRequest, CarrierCode, CarrierDto, CarrierSyncReport, CarrierUpdateRequest, ShipmentDto, ShipmentFilters, WarehousePointRequest } from '@/types/delivery';
import type { OrderDto, WarehousePointDto } from '@/types/order';
import type { Page } from '@/types/product';

export const getAdminCarriers = async (): Promise<CarrierDto[]> => {
    const { data } = await apiClient.get<CarrierDto[]>('/v1/admin/carriers');
    return data;
};

export const updateCarrier = async (code: CarrierCode, request: CarrierUpdateRequest): Promise<CarrierDto> => {
    const { data } = await apiClient.patch<CarrierDto>(`/v1/admin/carriers/${code}`, request);
    return data;
};

export const getAdminWarehousePoints = async (): Promise<WarehousePointDto[]> => {
    const { data } = await apiClient.get<WarehousePointDto[]>('/v1/admin/warehouse-points');
    return data;
};

export const createWarehousePoint = async (request: WarehousePointRequest): Promise<WarehousePointDto> => {
    const { data } = await apiClient.post<WarehousePointDto>('/v1/admin/warehouse-points', request);
    return data;
};

export const updateWarehousePoint = async (id: number, request: WarehousePointRequest): Promise<WarehousePointDto> => {
    const { data } = await apiClient.put<WarehousePointDto>(`/v1/admin/warehouse-points/${id}`, request);
    return data;
};

/** Таймаут синхронизации терминалов: СДЭК грузит справочник дольше дефолтных 15 с клиента */
const SYNC_TERMINALS_TIMEOUT_MS = 2 * 60 * 1000;

/** Ручная синхронизация терминалов ТК — POST /api/v1/admin/carriers/{code}/sync-terminals */
export const syncCarrierTerminals = async (code: CarrierCode): Promise<CarrierSyncReport> => {
    const { data } = await apiClient.post<CarrierSyncReport>(
        `/v1/admin/carriers/${code}/sync-terminals`, undefined, { timeout: SYNC_TERMINALS_TIMEOUT_MS });
    return data;
};

/** Список отправлений с фильтрами — GET /api/v1/admin/shipments */
export const getAdminShipments = async (params: ShipmentFilters): Promise<Page<ShipmentDto>> => {
    const { data } = await apiClient.get<Page<ShipmentDto>>('/v1/admin/shipments', { params });
    return data;
};

/** Принудительный опрос статуса в ТК */
export const refreshShipment = async (id: number): Promise<ShipmentDto> => {
    const { data } = await apiClient.post<ShipmentDto>(`/v1/admin/shipments/${id}/refresh`);
    return data;
};

/** Привязка отправления из журнала ТК к заказу по номеру */
export const linkShipment = async (id: number, orderNumber: string): Promise<ShipmentDto> => {
    const { data } = await apiClient.post<ShipmentDto>(`/v1/admin/shipments/${id}/link`, { orderNumber });
    return data;
};

/** Внести накладную в заказ. 409 SHIPMENT_MODE_SWITCH_REQUIRED — нужно повторить с shipmentMode=MULTIPLE */
export const addOrderShipment = async (orderId: string, request: AddShipmentRequest): Promise<ShipmentDto> => {
    const { data } = await apiClient.post<ShipmentDto>(`/v1/admin/orders/${orderId}/shipments`, request);
    return data;
};

/** Отвязать накладную от заказа — она остаётся в журнале отправлений */
export const unlinkOrderShipment = async (orderId: string, shipmentId: number): Promise<void> => {
    await apiClient.post(`/v1/admin/orders/${orderId}/shipments/${shipmentId}/unlink`);
};

/** Запустить импорт журнала ТК — 202 запущен, 409 уже идёт */
export const importShipments = async (): Promise<void> => {
    await apiClient.post('/v1/admin/shipments/import');
};

/** «Все отправления внесены» */
export const completeOrderShipments = async (orderId: string): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>(`/v1/admin/orders/${orderId}/shipments/complete`);
    return data;
};
