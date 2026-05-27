import apiClient from './client';
import type {
    AuthTokens,
    LoginRequest,
    RegisterRequest,
    User,
} from '@/types/auth';

/** Логин — POST /v1/auth/login */
export const login = async (request: LoginRequest): Promise<AuthTokens> => {
    const { data } = await apiClient.post<Record<string, unknown>>(
        '/v1/auth/login',
        request,
    );
    const rawUser = data.user as Record<string, unknown>;
    return {
        access_token: data.access_token as string,
        refresh_token: data.refresh_token as string,
        token_type: (data.token_type as string) ?? 'Bearer',
        expires_in: (data.expires_in as number) ?? 3600,
        user: {
            id: rawUser.id as number,
            email: rawUser.email as string,
            firstname: (rawUser.firstname as string) ?? '',
            lastname: (rawUser.lastname as string) ?? '',
            surname: (rawUser.surname as string | null) ?? null,
            phone: (rawUser.phone as string | null) ?? null,
            emailVerified: (rawUser.emailVerified as boolean) ?? false,
            roles: (rawUser.roles as { id: number; name: string }[]) ?? [],
            createdAt: (rawUser.createdAt as string) ?? '',
            updatedAt: (rawUser.updatedAt as string) ?? '',
            clientType: ((data.clientType as string) ?? 'B2C') as 'B2C' | 'B2B',
            companyName: (data.companyName as string | null) ?? null,
            inn: (data.inn as string | null) ?? null,
        },
    };
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

    const accessToken = (data.access_token || data.accessToken) as string;
    const parts = accessToken.split('.');
    let payload: Record<string, unknown> = {};
    try { payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))); } catch { /* ignore */ }

    return {
        access_token: accessToken,
        refresh_token: (data.refresh_token || data.refreshToken) as string,
        token_type: (data.token_type || data.tokenType || 'Bearer') as string,
        expires_in: (data.expires_in || data.expiresIn || 3600) as number,
        user: (data.user as User) ?? {
            id: Number(payload.sub),
            email: payload.email as string,
            firstname: '',
            lastname: '',
            surname: null,
            phone: null,
            emailVerified: true,
            roles: ((payload.roles as string[]) || []).map((name: string, idx: number) => ({ id: idx, name })),
            createdAt: '',
            updatedAt: '',
            clientType: (payload.clientType as 'B2C' | 'B2B') ?? 'B2C',
            companyName: (payload.companyName as string) ?? null,
            inn: (payload.inn as string) ?? null,
        },
    };
};

/** Переключение контекста B2C↔B2B — POST /v1/auth/switch-context */
export const switchContext = async (
    targetType: 'B2C' | 'B2B',
    password?: string,
): Promise<AuthTokens> => {
    const { data } = await apiClient.post<Record<string, unknown>>(
        '/v1/auth/switch-context',
        { targetType, ...(password ? { password } : {}) },
    );
    const accessToken = data.accessToken as string;
    const parts = accessToken.split('.');
    let payload: Record<string, unknown> = {};
    try { payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))); } catch { /* ignore */ }

    return {
        access_token: accessToken,
        refresh_token: data.refreshToken as string,
        token_type: (data.tokenType as string) ?? 'Bearer',
        expires_in: 3600,
        clientType: data.clientType as string,
        companyName: (data.companyName as string | null) ?? null,
        inn: (data.inn as string | null) ?? null,
        user: {
            id: Number(payload.sub),
            email: payload.email as string,
            firstname: '',
            lastname: '',
            surname: null,
            phone: null,
            emailVerified: true,
            roles: ((payload.roles as string[]) || []).map((name: string, idx: number) => ({ id: idx, name })),
            createdAt: '',
            updatedAt: '',
            clientType: (payload.clientType as 'B2C' | 'B2B') ?? 'B2C',
            companyName: (payload.companyName as string) ?? null,
            inn: (payload.inn as string) ?? null,
        },
    };
};