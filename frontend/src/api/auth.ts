import apiClient from './client';
import type {
    AuthResponse,
    LoginRequest,
    RegisterRequest,
    User,
} from '@/types/auth';

/** Логин по email + пароль */
export const login = async (request: LoginRequest): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>(
        '/v1/auth/login',
        request,
    );
    return data;
};

/** Регистрация нового пользователя */
export const register = async (
    request: RegisterRequest,
): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>(
        '/v1/auth/register',
        request,
    );
    return data;
};

/** Получение текущего пользователя по токену */
export const getCurrentUser = async (): Promise<User> => {
    const { data } = await apiClient.get<User>('/v1/users/me');
    return data;
};

/** Выход — инвалидация refresh-токена на сервере */
export const logout = async (): Promise<void> => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (refreshToken) {
        await apiClient.post('/v1/auth/logout', { refreshToken });
    }
};