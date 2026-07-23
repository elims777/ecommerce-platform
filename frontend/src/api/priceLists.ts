import apiClient from './client';

export type PriceListStatus = 'PENDING' | 'READY' | 'FAILED' | 'EXPIRED';

export interface PriceListResponse {
    id: number;
    status: PriceListStatus;
    categoryNames: string[];
    rowCount: number | null;
    createdAt: string;
    completedAt: string | null;
}

/** Заказать формирование прайс-листа по выбранным категориям */
export const createPriceList = async (categoryIds: number[]): Promise<PriceListResponse> => {
    const { data } = await apiClient.post<PriceListResponse>('/v1/price-lists', { categoryIds });
    return data;
};

/** Получить список прайс-листов текущего пользователя */
export const getMyPriceLists = async (): Promise<PriceListResponse[]> => {
    const { data } = await apiClient.get<PriceListResponse[]>('/v1/price-lists');
    return data;
};

/** Скачать готовый прайс-лист (xlsx) */
export const downloadPriceList = async (id: number): Promise<Blob> => {
    const { data } = await apiClient.get<Blob>(`/v1/price-lists/${id}/download`, {
        responseType: 'blob',
    });
    return data;
};
