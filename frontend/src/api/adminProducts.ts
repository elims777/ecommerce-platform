import apiClient from './client';
import type { Product, ProductImage, ProductAttribute, ProductStock, Page } from '@/types/product';

/** Запрос на создание/обновление товара */
export interface ProductRequest {
    name: string;
    description?: string;
    shortDescription?: string;
    price?: number;
    wholesalePrice?: number | null;
    categoryId?: number | null;
    isActive?: boolean;
    isFeatured?: boolean;
    /** Точечная акция на товаре: перебивает акцию его категории */
    isSale?: boolean;
    /** Процент: положительный — наценка, отрицательный — скидка (до −90) */
    saleMarkupPercent?: number | null;
    externalId?: string;
    sku?: string;
    externalCode?: string;
    unitOfMeasure?: string;
    vatRate?: number;
    material?: string;
}

/** Создать товар */
export const createProduct = async (request: ProductRequest): Promise<Product> => {
    const { data } = await apiClient.post<Product>('/v1/products', request);
    return data;
};

/** Обновить товар */
export const updateProduct = async (id: number, request: ProductRequest): Promise<Product> => {
    const { data } = await apiClient.put<Product>(`/v1/products/${id}`, request);
    return data;
};

/** Удалить товар */
export const deleteProduct = async (id: number): Promise<void> => {
    await apiClient.delete(`/v1/products/${id}`);
};

// === Остатки по складам и склейка 1С ↔ Факел ===

/** Собственные остатки записи по складам (включая нулевые) */
export const getOwnStocks = async (id: number): Promise<ProductStock[]> => {
    const { data } = await apiClient.get<ProductStock[]>(`/v1/products/admin/${id}/stocks`);
    return data;
};

/** Задать собственный остаток на складе */
export const setProductStock = async (id: number, warehouseId: number, quantity: number): Promise<Product> => {
    const { data } = await apiClient.put<Product>(`/v1/products/${id}/stocks/${warehouseId}`, null, {
        params: { quantity },
    });
    return data;
};

/** Товары 1С, склеенные с данным */
export const getMergedProducts = async (id: number): Promise<Product[]> => {
    const { data } = await apiClient.get<Product[]>(`/v1/products/admin/${id}/merged`);
    return data;
};

/** Кандидаты на склейку (несклеенные товары 1С) */
export const getMergeCandidates = async (query: string): Promise<Product[]> => {
    const { data } = await apiClient.get<Page<Product>>('/v1/products/admin/merge-candidates', {
        params: { query, size: 20 },
    });
    return data.content;
};

export const mergeProduct = async (id: number, oneCId: number): Promise<void> => {
    await apiClient.post(`/v1/products/admin/${id}/merged/${oneCId}`);
};

export const unmergeProduct = async (oneCId: number): Promise<void> => {
    await apiClient.delete(`/v1/products/admin/merged/${oneCId}`);
};

/** Изменить категорию товара */
export const changeProductCategory = async (id: number, categoryId: number | null): Promise<Product> => {
    const { data } = await apiClient.put<Product>(`/v1/products/${id}/category`, null, {
        params: { categoryId },
    });
    return data;
};

// === Изображения ===

/** Загрузить изображение */
export const uploadImage = async (productId: number, file: File): Promise<ProductImage> => {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await apiClient.post<ProductImage>(
        `/v1/products/${productId}/images`,
        formData,
        { headers: { 'Content-Type': 'multipart/form-data' } },
    );
    return data;
};

/** Удалить изображение */
export const deleteImage = async (productId: number, imageId: number): Promise<void> => {
    await apiClient.delete(`/v1/products/${productId}/images/${imageId}`);
};

/** Установить главное изображение */
export const setPrimaryImage = async (productId: number, imageId: number): Promise<ProductImage> => {
    const { data } = await apiClient.put<ProductImage>(
        `/v1/products/${productId}/images/${imageId}/primary`,
    );
    return data;
};

// === Характеристики ===

/** Запрос на характеристику */
export interface ProductAttributeRequest {
    attributeName: string;
    attributeValue: string;
}

/** Добавить характеристику */
export const addAttribute = async (
    productId: number,
    request: ProductAttributeRequest,
): Promise<ProductAttribute> => {
    const { data } = await apiClient.post<ProductAttribute>(
        `/v1/products/${productId}/attributes`,
        request,
    );
    return data;
};

/** Обновить характеристику */
export const updateAttribute = async (
    productId: number,
    attributeId: number,
    request: ProductAttributeRequest,
): Promise<ProductAttribute> => {
    const { data } = await apiClient.put<ProductAttribute>(
        `/v1/products/${productId}/attributes/${attributeId}`,
        request,
    );
    return data;
};

/** Удалить характеристику */
export const deleteAttribute = async (
    productId: number,
    attributeId: number,
): Promise<void> => {
    await apiClient.delete(`/v1/products/${productId}/attributes/${attributeId}`);
};

/** Назначить/снять родительский товар */
export const setParentProduct = async (id: number, parentProductId: number | null): Promise<Product> => {
    const { data } = await apiClient.patch<Product>(`/v1/products/${id}/set-parent`, null, {
        params: parentProductId != null ? { parentProductId } : {},
    });
    return data;
};

/** Кандидаты в родители (включая неактивные; товары categoryId — первыми) */
export const searchParentCandidates = async (
    query: string,
    categoryId: number | null | undefined,
    excludeIds: number[],
): Promise<Product[]> => {
    const { data } = await apiClient.get<Page<Product>>('/v1/products/admin/parent-candidates', {
        params: { query, categoryId: categoryId ?? undefined, excludeIds: excludeIds.join(','), size: 20 },
    });
    return data.content;
};

/** Массовая активация/деактивация товаров */
export const batchUpdateActive = async (productIds: number[], isActive: boolean): Promise<void> => {
    await apiClient.put('/v1/products/batch/active', productIds, {
        params: { isActive },
    });
};

/** Массовая установка/снятие акции на товарах (собственная метка, перебивает акцию категории) */
export const batchUpdateSale = async (
    productIds: number[],
    isSale: boolean,
    saleMarkupPercent: number | null,
): Promise<void> => {
    await apiClient.put('/v1/products/batch/sale', productIds, {
        params: isSale ? { isSale, saleMarkupPercent } : { isSale },
    });
};

/** Массовое удаление товаров */
export const bulkDeleteProducts = async (ids: number[]): Promise<void> => {
    await apiClient.delete('/v1/products/batch', { data: ids });
};

/** Обновить порядок отображения товара в категории */
export const updateDisplayOrder = async (id: number, displayOrder: number): Promise<Product> => {
    const { data } = await apiClient.patch<Product>(`/v1/products/${id}/display-order`, { displayOrder });
    return data;
};

/** Обновить displayOrder для нескольких товаров одним запросом */
export const reorderProducts = async (orders: Record<number, number>): Promise<void> => {
    await apiClient.patch('/v1/products/reorder', orders);
};

/** Сводка по каталогу для админки */
export interface ProductStats {
    /** Товары без учёта дочерних вариантов */
    uniqueProducts: number;
    /** Все записи каталога, включая варианты */
    total: number;
    fromOneC: number;
    fromFtk: number;
    active: number;
    inactive: number;
}

/** Получить сводку по каталогу (кэшируется на бэкенде, сбрасывается импортом) */
export const getProductStats = async (): Promise<ProductStats> => {
    const { data } = await apiClient.get<ProductStats>('/v1/products/stats');
    return data;
};
