import apiClient from './client';
import type { Product, Page } from '@/types/product';

interface GetProductsParams {
    page?: number;
    size?: number;
    sort?: string;
    categoryId?: number;
    isActive?: boolean;
}

/** Получить товары с пагинацией */
export const getProducts = async (params: GetProductsParams = {}): Promise<Page<Product>> => {
    const { page = 0, size = 20, sort = 'createdAt,desc', categoryId } = params;

    const url = categoryId
        ? `/v1/products/category/${categoryId}`
        : '/v1/products';

    const { data } = await apiClient.get<Page<Product>>(url, {
        params: { page, size, sort },
    });
    return data;
};

/** Получить товар по ID */
export const getProductById = async (id: number): Promise<Product> => {
    const { data } = await apiClient.get<Product>(`/v1/products/${id}`);
    return data;
};

/** Получить товар по slug */
export const getProductBySlug = async (slug: string): Promise<Product> => {
    const { data } = await apiClient.get<Product>(`/v1/products/slug/${slug}`);
    return data;
};

/** Поиск товаров по названию */
export const searchProducts = async (query: string, page = 0, size = 20): Promise<Page<Product>> => {
    const { data } = await apiClient.get<Page<Product>>('/v1/products/search', {
        params: { query, page, size },
    });
    return data;
};

/** Получить количество товаров в наличии (публичный эндпоинт) */
export const getAvailableProductsCount = async (): Promise<number> => {
    const { data } = await apiClient.get<{ count: number }>('/v1/products/count-available');
    return data.count;
};

/** Получить все товары включая неактивные (для админки) */
export const getAdminProducts = async (params: GetProductsParams = {}): Promise<Page<Product>> => {
    const { page = 0, size = 20, sort = 'name,asc', categoryId, isActive } = params;
    const queryParams: Record<string, unknown> = { page, size, sort };
    if (categoryId !== undefined) queryParams.categoryId = categoryId;
    if (isActive !== undefined) queryParams.isActive = isActive;
    const { data } = await apiClient.get<Page<Product>>('/v1/products/admin', {
        params: queryParams,
    });
    return data;
};