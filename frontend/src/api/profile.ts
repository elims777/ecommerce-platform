import apiClient from './client';

/** Профиль пользователя — маппится на UserDto */
export interface UserProfile {
    id: number;
    email: string;
    firstname: string;
    lastname: string;
    surname: string | null;
    emailVerified: boolean;
    createdAt: string;
    updatedAt: string;
}

/** Данные для обновления профиля */
export interface UpdateProfileRequest {
    firstname: string;
    lastname: string;
    surname?: string;
}

/** Получить профиль текущего пользователя */
export const getProfile = async (): Promise<UserProfile> => {
    const { data } = await apiClient.get<UserProfile>('/v1/users/me');
    return data;
};

/** Обновить профиль */
export const updateProfile = async (
    userId: number,
    request: UpdateProfileRequest,
): Promise<UserProfile> => {
    const { data } = await apiClient.put<UserProfile>(
        `/v1/users/${userId}`,
        request,
    );
    return data;
};

/** Сменить пароль */
export const changePassword = async (
    userId: number,
    newPassword: string,
): Promise<void> => {
    await apiClient.put(`/v1/users/${userId}`, {
        password: newPassword,
    });
};