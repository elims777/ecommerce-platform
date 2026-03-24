import apiClient from './client';
import type { Product, Page } from '@/types/product';

interface GetProductsParams {
    page?: number;
    size?: number;
    sort?: string;
    categoryId?: number;
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
export const searchProducts = async (query: string): Promise<Product[]> => {
    const { data } = await apiClient.get<Product[]>('/v1/products/search', {
        params: { query },
    });
    return data;
};