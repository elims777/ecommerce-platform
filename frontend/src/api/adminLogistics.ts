import apiClient from './client';
import type { CarrierCode, CarrierDto, CarrierUpdateRequest, WarehousePointRequest } from '@/types/delivery';
import type { WarehousePointDto } from '@/types/order';

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
