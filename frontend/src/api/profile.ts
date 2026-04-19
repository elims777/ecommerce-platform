import apiClient from './client';
import type { User } from '@/types/auth';

/** Данные для обновления профиля */
export interface UpdateProfileRequest {
    firstname: string;
    lastname: string;
    surname?: string;
    phone?: string;
}

/** Получить профиль текущего пользователя (через auth-service → user-service) */
export const getProfile = async (): Promise<User> => {
    const { data } = await apiClient.get<User>('/v1/auth/me');
    return data;
};

/** Обновить профиль (напрямую в user-service) */
export const updateProfile = async (
    userId: number,
    request: UpdateProfileRequest,
): Promise<User> => {
    // Сначала получаем текущие данные, чтобы не затереть email и другие поля
    const current = await getProfile();
    const { data } = await apiClient.put<User>(
        `/v1/users/${userId}`,
        { ...current, ...request },
    );
    return data;
};