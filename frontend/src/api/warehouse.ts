import apiClient from './client';
import type { WarehousePointDto } from '@/types/order';

/** Получить активные точки самовывоза — GET /api/v1/warehouse-points */
export const getWarehousePoints = async (): Promise<WarehousePointDto[]> => {
    const { data } = await apiClient.get<WarehousePointDto[]>('/v1/warehouse-points');
    return data;
};