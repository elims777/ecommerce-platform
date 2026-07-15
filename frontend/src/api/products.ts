import apiClient from './client';
import type { Product, Page, Facet } from '@/types/product';

interface GetProductsParams {
    page?: number;
    size?: number;
    sort?: string;
    categoryId?: number;
    isActive?: boolean;
    attr?: string[];
}

/** Получить товары с пагинацией */
export const getProducts = async (params: GetProductsParams = {}): Promise<Page<Product>> => {
    // sort не подставляем по умолчанию: без него бэкенд сортирует категорию
    // по displayOrder (порядок из админки), общий список — по createdAt desc
    const { page = 0, size = 20, sort, categoryId, attr } = params;

    const url = categoryId
        ? `/v1/products/category/${categoryId}`
        : '/v1/products';

    const query: Record<string, unknown> = sort ? { page, size, sort } : { page, size };
    // attr-фильтры применимы только к категорийному листингу
    if (categoryId && attr && attr.length > 0) query.attr = attr;

    const { data } = await apiClient.get<Page<Product>>(url, {
        params: query,
        // axios сериализует массив attr как повторяющиеся ключи attr=...&attr=... (не attr[0]=...)
        paramsSerializer: { indexes: null },
    });
    return data;
};

/** Получить фасеты каталога для категории */
export const getFacets = async (categoryId: number): Promise<Facet[]> => {
    const { data } = await apiClient.get<Facet[]>('/v1/products/facets', {
        params: { categoryId },
    });
    return data;
};

/** Получить рекомендуемые товары (Хиты продаж) */
export const getFeaturedProducts = async (): Promise<Page<Product>> => {
    const { data } = await apiClient.get<Page<Product>>('/v1/products/featured', {
        params: { page: 0, size: 10 },
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