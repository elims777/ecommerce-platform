import apiClient from './client';
import type {
    AuthTokens,
    LoginRequest,
    RegisterRequest,
    User,
} from '@/types/auth';

/** Логин — POST /v1/auth/login */
export const login = async (request: LoginRequest): Promise<AuthTokens> => {
    const { data } = await apiClient.post<AuthTokens>(
        '/v1/auth/login',
        request,
    );
    return data;
};

/** Регистрация — POST /v1/register */
export const register = async (request: RegisterRequest): Promise<void> => {
    await apiClient.post('/v1/register', request);
};

/** Получение текущего пользователя — GET /v1/auth/me */
export const getCurrentUser = async (): Promise<User> => {
    const { data } = await apiClient.get<User>('/v1/auth/me');
    return data;
};

/** Выход — POST /v1/auth/logout */
export const logout = async (): Promise<void> => {
    await apiClient.post('/v1/auth/logout');
};

/**
 * Обновление токенов — POST /v1/auth/refresh?refreshToken=xxx
 * Swagger показывает AuthResponse (camelCase), но login возвращает snake_case.
 * Обрабатываем оба варианта.
 */
export const refreshTokens = async (refreshToken: string): Promise<AuthTokens> => {
    const { data } = await apiClient.post<Record<string, unknown>>(
        '/v1/auth/refresh',
        null,
        { params: { refreshToken } },
    );

    // Нормализуем ответ — может быть и snake_case, и camelCase
    return {
        access_token: (data.access_token || data.accessToken) as string,
        refresh_token: (data.refresh_token || data.refreshToken) as string,
        token_type: (data.token_type || data.tokenType || 'Bearer') as string,
        expires_in: (data.expires_in || data.expiresIn || 3600) as number,
        user: data.user as User,
    };
};