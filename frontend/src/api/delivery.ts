import apiClient from './client';
import type { CarrierCode, CarrierDto, CarrierTerminalDto, PointType } from '@/types/delivery';

/** Включённые ТК для оформления заказа — GET /api/v1/delivery/carriers */
export const getCarriers = async (): Promise<CarrierDto[]> => {
    const { data } = await apiClient.get<CarrierDto[]>('/v1/delivery/carriers');
    return data;
};

/** Города, где у ТК есть активные точки — GET /api/v1/delivery/cities */
export const getCities = async (carrier: CarrierCode, type: PointType, q: string): Promise<string[]> => {
    const { data } = await apiClient.get<string[]>('/v1/delivery/cities', { params: { carrier, type, q } });
    return data;
};

/** Точки ТК в городе — GET /api/v1/delivery/terminals */
export const getTerminals = async (carrier: CarrierCode, city: string, type: PointType): Promise<CarrierTerminalDto[]> => {
    const { data } = await apiClient.get<CarrierTerminalDto[]>('/v1/delivery/terminals', { params: { carrier, city, type } });
    return data;
};
