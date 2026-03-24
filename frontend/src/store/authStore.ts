import { create } from 'zustand';
import type { User } from '@/types/auth';
import * as authApi from '@/api/auth';
import type { LoginRequest, RegisterRequest } from '@/types/auth';

interface AuthState {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;

    /** Логин — сохраняет токены и данные пользователя */
    login: (request: LoginRequest) => Promise<void>;

    /** Регистрация — сразу логинит после успешной регистрации */
    register: (request: RegisterRequest) => Promise<void>;

    /** Выход — очищает токены и состояние */
    logout: () => Promise<void>;

    /** Восстановление сессии при загрузке приложения */
    restoreSession: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
    user: null,
    isAuthenticated: false,
    isLoading: true,

    login: async (request) => {
        const response = await authApi.login(request);
        localStorage.setItem('accessToken', response.accessToken);
        localStorage.setItem('refreshToken', response.refreshToken);
        set({ user: response.user, isAuthenticated: true });
    },

    register: async (request) => {
        const response = await authApi.register(request);
        localStorage.setItem('accessToken', response.accessToken);
        localStorage.setItem('refreshToken', response.refreshToken);
        set({ user: response.user, isAuthenticated: true });
    },

    logout: async () => {
        try {
            await authApi.logout();
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

        try {
            const user = await authApi.getCurrentUser();
            set({ user, isAuthenticated: true, isLoading: false });
        } catch {
            // Токен невалидный или истёк — refresh сработает через interceptor
            // Если и refresh не помог — interceptor очистит токены
            localStorage.removeItem('accessToken');
            localStorage.removeItem('refreshToken');
            set({ user: null, isAuthenticated: false, isLoading: false });
        }
    },
}));