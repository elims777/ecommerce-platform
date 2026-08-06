import apiClient from './client';
import type { Page } from '@/types/product';
import type { News, NewsAdmin, NewsRequest } from '@/types/news';

/** Опубликованные новости с пагинацией */
export const getNews = async (page = 0, size = 10): Promise<Page<News>> => {
    const { data } = await apiClient.get<Page<News>>('/v1/news', { params: { page, size } });
    return data;
};

/** Последние новости для блока на главной */
export const getLatestNews = async (size = 5): Promise<News[]> => {
    const { data } = await apiClient.get<News[]>('/v1/news/latest', { params: { size } });
    return data;
};

export const getNewsBySlug = async (slug: string): Promise<News> => {
    const { data } = await apiClient.get<News>(`/v1/news/${slug}`);
    return data;
};

// === Админка ===

export const getAdminNews = async (page = 0, size = 20): Promise<Page<NewsAdmin>> => {
    const { data } = await apiClient.get<Page<NewsAdmin>>('/v1/admin/news', { params: { page, size } });
    return data;
};

export const getAdminNewsById = async (id: number): Promise<NewsAdmin> => {
    const { data } = await apiClient.get<NewsAdmin>(`/v1/admin/news/${id}`);
    return data;
};

export const createNews = async (request: NewsRequest): Promise<NewsAdmin> => {
    const { data } = await apiClient.post<NewsAdmin>('/v1/admin/news', request);
    return data;
};

export const updateNews = async (id: number, request: NewsRequest): Promise<NewsAdmin> => {
    const { data } = await apiClient.put<NewsAdmin>(`/v1/admin/news/${id}`, request);
    return data;
};

export const deleteNews = async (id: number): Promise<void> => {
    await apiClient.delete(`/v1/admin/news/${id}`);
};

/** Загрузка картинки в S3, возвращает публичный URL */
export const uploadNewsImage = async (file: File): Promise<string> => {
    const formData = new FormData();
    formData.append('file', file);

    const { data } = await apiClient.post<{ url: string }>('/v1/admin/news/images', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    });
    return data.url;
};
