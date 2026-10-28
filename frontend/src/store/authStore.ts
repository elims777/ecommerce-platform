import { create } from 'zustand';
import type { User, ClientType, LoginRequest } from '@/types/auth';
import * as authApi from '@/api/auth';
import { getLegalEntity } from '@/api/legalEntity';
import { queryClient } from '@/lib/queryClient';
import { useCartStore } from '@/store/cartStore';

interface AuthState {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;

    login: (request: LoginRequest) => Promise<void>;
    loginLegal: (login: string, password: string) => Promise<void>;
    logout: () => Promise<void>;
    restoreSession: () => Promise<void>;
    switchContext: (targetType: ClientType) => Promise<void>;
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
        const tokens = await authApi.login(request);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        localStorage.removeItem('clientType');
        localStorage.removeItem('userId');
        queryClient.clear();
        useCartStore.getState().resetCart();
        set({ user: tokens.user, isAuthenticated: true });
    },

    loginLegal: async (login, password) => {
        const tokens = await authApi.loginLegal(login, password);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        localStorage.setItem('clientType', 'B2B');
        localStorage.setItem('userId', String(tokens.user.id));
        queryClient.clear();
        useCartStore.getState().resetCart();
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
            localStorage.removeItem('clientType');
            localStorage.removeItem('userId');
            queryClient.clear();
            useCartStore.getState().resetCart();
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
                localStorage.removeItem('clientType');
                localStorage.removeItem('userId');
                set({ user: null, isAuthenticated: false, isLoading: false });
                return;
            }

            try {
                const tokens = await authApi.refreshTokens(refreshToken);
                localStorage.setItem('accessToken', tokens.access_token);
                localStorage.setItem('refreshToken', tokens.refresh_token);
                // tokens.user — заглушка из JWT (пустые ФИО/телефон), поэтому user не ставим:
                // реальные данные загрузит код ниже уже с новым токеном
            } catch {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                localStorage.removeItem('clientType');
                localStorage.removeItem('userId');
                set({ user: null, isAuthenticated: false, isLoading: false });
                return;
            }
        }

        // Токен валидный — загружаем данные пользователя из БД
        const savedClientType = localStorage.getItem('clientType');
        const savedUserId = localStorage.getItem('userId');

        if (savedClientType === 'B2B' && savedUserId) {
            try {
                const entity = await getLegalEntity(Number(savedUserId));
                set({
                    user: {
                        id: entity.id,
                        email: entity.email,
                        firstname: '',
                        lastname: '',
                        surname: null,
                        phone: entity.phone,
                        emailVerified: true,
                        roles: [{ id: 0, name: 'ROLE_USER' }],
                        createdAt: entity.createdAt,
                        updatedAt: '',
                        clientType: 'B2B',
                        companyName: entity.fullName,
                        inn: entity.inn,
                    },
                    isAuthenticated: true,
                    isLoading: false,
                });
            } catch {
                localStorage.removeItem('accessToken');
                localStorage.removeItem('refreshToken');
                localStorage.removeItem('clientType');
                localStorage.removeItem('userId');
                set({ user: null, isAuthenticated: false, isLoading: false });
            }
            return;
        }

        try {
            const user = await authApi.getCurrentUser();
            set({ user, isAuthenticated: true, isLoading: false });
        } catch {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            localStorage.removeItem('clientType');
            localStorage.removeItem('userId');
            set({ user: null, isAuthenticated: false, isLoading: false });
        }
    },

    switchContext: async (targetType) => {
        const tokens = await authApi.switchContext(targetType);
        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        localStorage.setItem('clientType', 'B2B');
        localStorage.setItem('userId', String(tokens.user.id));
        useCartStore.getState().resetCart();
        set({ user: tokens.user });
        // Кэш чистим без рефетча (старые ключи); страницы перемонтируются по key Outlet в ClientLayout
        queryClient.removeQueries();
    },
}));