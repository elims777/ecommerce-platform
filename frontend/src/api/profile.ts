import apiClient from './client';
import type { User } from '@/types/auth';

/** Данные для обновления профиля */
export interface UpdateProfileRequest {
    firstname: string;
    lastname: string;
    surname?: string;
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
    const { data } = await apiClient.put<User>(
        `/v1/users/${userId}`,
        request,
    );
    return data;
};