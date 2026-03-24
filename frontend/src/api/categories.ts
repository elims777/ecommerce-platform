import apiClient from './client';
import type { CategoryTree } from '@/types/product';

/** Получить дерево категорий (кэшируется на бэкенде) */
export const getCategoryTree = async (): Promise<CategoryTree[]> => {
    const { data } = await apiClient.get<CategoryTree[]>('/v1/categories/tree');
    return data;
};