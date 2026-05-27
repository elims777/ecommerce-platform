import { create } from 'zustand';
import type { User, ClientType } from '@/types/auth';
import * as authApi from '@/api/auth';
import type { LoginRequest } from '@/types/auth';

interface AuthState {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;

    login: (request: LoginRequest) => Promise<void>;
    logout: () => Promise<void>;
    restoreSession: () => Promise<void>;
    switchContext: (targetType: ClientType, password?: string) => Promise<void>;
}

/**
 * Декодирует payload JWT без верификации подписи.
 * Используется ТОЛЬКО для извлечения данных на клиенте.
 */
const decodeJwtPayload = (token: string): Record<string, unknown> | null => {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(payload);
    } catch {
        return null;
    }
};

/**
 * Проверяет, не истёк ли JWT токен.
 */
const isTokenExpired = (token: string): boolean => {
    const payload = decodeJwtPayload(token);
    if (!payload || typeof payload.exp !== 'number') return true;
    return payload.exp * 1000 < Date.now();
};

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    isAuthenticated: false,
    isLoading: true,

    login: async (request) => {
        // Login response содержит и токены, и user — не нужен отдельный /me
        const tokens = await authApi.login(request);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        set({ user: tokens.user, isAuthenticated: true });
    },

    logout: async () => {
        try {
            await authApi.logout();
        } catch {
            // Если logout на сервере упал — всё равно чистим локально
        } finally {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            set({ user: null, isAuthenticated: false });
        }
    },

    restoreSession: async () => {
        const accessToken = localStorage.getItem('accessToken');
        if (!accessToken) {
            set({ isLoading: false });
            return;
        }

        // Проверяем токен локально — без запроса на сервер
        if (isTokenExpired(accessToken)) {
            // Пробуем обновить через refresh token
            const refreshToken = localStorage.getItem('refreshToken');
            if (!refreshToken || isTokenExpired(refreshToken)) {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                set({ user: null, isAuthenticated: false, isLoading: false });
                return;
            }

            try {
                const tokens = await authApi.refreshTokens(refreshToken);
                localStorage.setItem('accessToken', tokens.access_token);
                localStorage.setItem('refreshToken', tokens.refresh_token);
                set({ user: tokens.user, isAuthenticated: true, isLoading: false });
            } catch {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                set({ user: null, isAuthenticated: false, isLoading: false });
            }
            return;
        }

        // Токен валидный — извлекаем данные пользователя из JWT
        try {
            const user = await authApi.getCurrentUser();
            set({ user, isAuthenticated: true, isLoading: false });
        } catch {
            // /me упал — но токен не истёк, пробуем использовать данные из JWT
            const payload = decodeJwtPayload(accessToken);
            if (payload) {
                set({
                    user: {
                        id: Number(payload.sub),
                        email: payload.email as string,
                        firstname: '',
                        lastname: '',
                        surname: null,
                        phone: null,
                        emailVerified: true,
                        roles: ((payload.roles as string[]) || []).map((name, idx) => ({ id: idx, name })),
                        createdAt: '',
                        updatedAt: '',
                        clientType: (payload.clientType as ClientType) ?? 'B2C',
                        companyName: (payload.companyName as string) ?? null,
                        inn: (payload.inn as string) ?? null,
                    },
                    isAuthenticated: true,
                    isLoading: false,
                });
            } else {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                set({ user: null, isAuthenticated: false, isLoading: false });
            }
        }
    },

    switchContext: async (targetType, password) => {
        const tokens = await authApi.switchContext(targetType, password);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        set({ user: tokens.user });
    },
}));