import apiClient from './client';
import type { Product } from '@/types/product';

export const getFavouriteIds = async (): Promise<number[]> => {
    const { data } = await apiClient.get<number[]>('/v1/favourites/ids');
    return data;
};

export const getFavouriteProducts = async (): Promise<Product[]> => {
    const { data } = await apiClient.get<Product[]>('/v1/favourites/products');
    return data;
};

export const addFavourite = async (productId: number): Promise<void> => {
    await apiClient.post(`/v1/favourites/${productId}`);
};

export const removeFavourite = async (productId: number): Promise<void> => {
    await apiClient.delete(`/v1/favourites/${productId}`);
};
