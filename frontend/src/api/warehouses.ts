import apiClient from './client';
import type { ProductStock, Warehouse } from '@/types/product';

/** Тело создания/правки склада */
export interface WarehouseRequest {
    code: string;
    name: string;
    city: string;
    pickupAvailable: boolean;
    active: boolean;
    sortOrder: number;
}

/** Активные склады по sortOrder (публичный) */
export const getWarehouses = async (): Promise<Warehouse[]> => {
    const { data } = await apiClient.get<Warehouse[]>('/v1/products/warehouses');
    return data;
};

/** Остатки (>0, активные склады) по товарам: ключ — productId */
export const getProductStocks = async (ids: number[]): Promise<Record<number, ProductStock[]>> => {
    const { data } = await apiClient.get<Record<number, ProductStock[]>>('/v1/products/stocks', {
        params: { ids: ids.join(',') },
    });
    return data;
};

export const getAdminWarehouses = async (): Promise<Warehouse[]> => {
    const { data } = await apiClient.get<Warehouse[]>('/v1/products/admin/warehouses');
    return data;
};

export const createWarehouse = async (request: WarehouseRequest): Promise<Warehouse> => {
    const { data } = await apiClient.post<Warehouse>('/v1/products/admin/warehouses', request);
    return data;
};

export const updateWarehouse = async (id: number, request: WarehouseRequest): Promise<Warehouse> => {
    const { data } = await apiClient.put<Warehouse>(`/v1/products/admin/warehouses/${id}`, request);
    return data;
};
