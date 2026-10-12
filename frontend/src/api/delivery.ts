import apiClient from './client';
import type { CarrierDto } from '@/types/delivery';

/** Включённые ТК для оформления заказа — GET /api/v1/delivery/carriers */
export const getCarriers = async (): Promise<CarrierDto[]> => {
    const { data } = await apiClient.get<CarrierDto[]>('/v1/delivery/carriers');
    return data;
};
