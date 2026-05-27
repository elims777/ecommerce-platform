import apiClient from './client';
import type { Product, ProductImage, ProductAttribute } from '@/types/product';

/** Запрос на создание/обновление товара */
export interface ProductRequest {
    name: string;
    description?: string;
    shortDescription?: string;
    price?: number;
    stockQuantity?: number;
    categoryId?: number | null;
    isActive?: boolean;
    isFeatured?: boolean;
    externalId?: string;
    sku?: string;
    externalCode?: string;
    unitOfMeasure?: string;
    vatRate?: number;
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

/** Изменить остаток на складе */
export const changeStock = async (id: number, quantity: number): Promise<Product> => {
    const { data } = await apiClient.put<Product>(`/v1/products/${id}/stock`, null, {
        params: { quantity },
    });
    return data;
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

/** Массовая активация/деактивация товаров */
export const batchUpdateActive = async (productIds: number[], isActive: boolean): Promise<void> => {
    await apiClient.put('/v1/products/batch/active', productIds, {
        params: { isActive },
    });
};

/** Массовое удаление товаров */
export const bulkDeleteProducts = async (ids: number[]): Promise<void> => {
    await apiClient.delete('/v1/products/batch', { data: ids });
};