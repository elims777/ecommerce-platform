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

/** Логин юрлица — POST /v1/auth/login/legal, затем GET /api/v1/legal-entities/{id} */
export const loginLegal = async (login: string, password: string): Promise<AuthTokens> => {
    const { data } = await apiClient.post<Record<string, unknown>>('/v1/auth/login/legal', { login, password });
    const accessToken = (data.accessToken || data.access_token) as string;
    const refreshToken = (data.refreshToken || data.refresh_token) as string;

    // sub содержит только цифры — ASCII, atob не ломает
    const subStr = accessToken.split('.')[1];
    const rawPayload = JSON.parse(atob(subStr.replace(/-/g, '+').replace(/_/g, '/')));
    const legalId = Number(rawPayload.sub);

    // Сохраняем токен заранее, чтобы GET-запрос прошёл с авторизацией
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('refreshToken', refreshToken);

    const { data: entity } = await apiClient.get<Record<string, unknown>>(`/v1/legal-entities/${legalId}`);

    return {
        access_token: accessToken,
        refresh_token: refreshToken,
        token_type: ((data.tokenType || data.token_type) as string) ?? 'Bearer',
        expires_in: ((data.expiresIn || data.expires_in) as number) ?? 3600,
        user: {
            id: legalId,
            email: (entity.email as string) ?? '',
            firstname: '',
            lastname: '',
            surname: null,
            phone: null,
            emailVerified: true,
            roles: [{ id: 0, name: 'ROLE_USER' }],
            createdAt: (entity.createdAt as string) ?? '',
            updatedAt: '',
            clientType: 'B2B',
            companyName: (entity.fullName as string) ?? null,
            inn: (entity.inn as string) ?? null,
        },
    };
};

/** Регистрация физлица — POST /v1/register */
export const register = async (request: RegisterRequest): Promise<void> => {
    await apiClient.post('/v1/register', request);
};

export interface RegisterLegalRequest {
    inn: string;
    ogrn: string;
    fullName: string;
    director: string;
    phone: string;
    email: string;
    password: string;
    legalCity: string;
    legalStreet: string;
    legalBuilding: string;
    legalPostalCode?: string;
}

/** Регистрация юрлица — POST /v1/register/legal */
export const registerLegal = async (request: RegisterLegalRequest): Promise<void> => {
    await apiClient.post('/v1/register/legal', request);
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

/** Запрос ссылки для сброса пароля — POST /v1/auth/forgot-password */
export const forgotPassword = async (email: string): Promise<void> => {
    await apiClient.post('/v1/auth/forgot-password', { email });
};

/** Проверка валидности токена сброса пароля — GET /v1/auth/reset-password/validate */
export const validateResetToken = async (token: string): Promise<boolean> => {
    const { data } = await apiClient.get<{ valid: boolean }>('/v1/auth/reset-password/validate', { params: { token } });
    return Boolean(data.valid);
};

/** Сброс пароля по токену — POST /v1/auth/reset-password */
export const resetPassword = async (token: string, newPassword: string): Promise<void> => {
    await apiClient.post('/v1/auth/reset-password', { token, newPassword });
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