import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const apiClient = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000,
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    },
});

// Request interceptor — подставляет JWT токен в каждый запрос
apiClient.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
        const accessToken = localStorage.getItem('accessToken');
        if (accessToken && config.headers) {
            config.headers.Authorization = `Bearer ${accessToken}`;
        }
        return config;
    },
    (error) => Promise.reject(error),
);

// Response interceptor — обрабатывает 401
// НЕ делает автоматический redirect и НЕ чистит токены агрессивно.
// Это ответственность authStore.
apiClient.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
        const originalRequest = error.config as InternalAxiosRequestConfig & {
            _retry?: boolean;
        };

        if (
            error.response?.status === 401 &&
            !originalRequest._retry &&
            !originalRequest.url?.includes('/auth/refresh') &&
            !originalRequest.url?.includes('/auth/login')
        ) {
            originalRequest._retry = true;

            const refreshToken = localStorage.getItem('refreshToken');
            if (!refreshToken) {
                return Promise.reject(error);
            }

            try {
                const { data } = await axios.post(
                    `${API_BASE_URL}/v1/auth/refresh`,
                    null,
                    {
                        params: { refreshToken },
                        headers: { 'Accept': 'application/json' },
                    },
                );

                const newAccessToken = data.access_token || data.accessToken;
                const newRefreshToken = data.refresh_token || data.refreshToken;

                if (newAccessToken) {
                    localStorage.setItem('accessToken', newAccessToken);
                }
                if (newRefreshToken) {
                    localStorage.setItem('refreshToken', newRefreshToken);
                }

                originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
                return apiClient(originalRequest);
            } catch {
                // Refresh не удался — просто reject, authStore разберётся
                return Promise.reject(error);
            }
        }

        return Promise.reject(error);
    },
);

export default apiClient;