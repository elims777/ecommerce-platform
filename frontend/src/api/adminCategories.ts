import apiClient from './client';
import type { CategoryTree, Category } from '@/types/product';

/** Запрос на создание/обновление категории */
export interface CategoryRequest {
    name: string;
    description?: string;
    parentId?: number | null;
    externalId?: string;
    displayOrder?: number;
}

/** Получить дерево категорий */
export const getCategoryTree = async (): Promise<CategoryTree[]> => {
    const { data } = await apiClient.get<CategoryTree[]>('/v1/categories/tree');
    return data;
};

/** Получить категорию по ID */
export const getCategoryById = async (id: number): Promise<Category> => {
    const { data } = await apiClient.get<Category>(`/v1/categories/${id}`);
    return data;
};

/** Создать категорию */
export const createCategory = async (request: CategoryRequest): Promise<Category> => {
    const { data } = await apiClient.post<Category>('/v1/categories', request);
    return data;
};

/** Обновить категорию */
export const updateCategory = async (id: number, request: CategoryRequest): Promise<Category> => {
    const { data } = await apiClient.put<Category>(`/v1/categories/${id}`, request);
    return data;
};

/** Удалить категорию */
export const deleteCategory = async (id: number): Promise<void> => {
    await apiClient.delete(`/v1/categories/${id}`);
};

/** Активировать категорию */
export const activateCategory = async (id: number): Promise<Category> => {
    const { data } = await apiClient.put<Category>(`/v1/categories/${id}/activate`);
    return data;
};

/** Деактивировать категорию */
export const deactivateCategory = async (id: number): Promise<Category> => {
    const { data } = await apiClient.put<Category>(`/v1/categories/${id}/deactivate`);
    return data;
};

/** Установить родительскую категорию */
export const setCategoryParent = async (id: number, parentId: number): Promise<Category> => {
    const { data } = await apiClient.put<Category>(`/v1/categories/${id}/parent/${parentId}`);
    return data;
};

/** Обновить displayOrder для нескольких категорий одним запросом */
export const reorderCategories = async (orders: Record<number, number>): Promise<void> => {
    await apiClient.patch('/v1/categories/reorder', orders);
};