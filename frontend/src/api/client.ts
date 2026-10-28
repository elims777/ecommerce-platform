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

let refreshPromise: Promise<string> | null = null;

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
            !originalRequest.url?.includes('/auth/login') &&
            !originalRequest.url?.includes('/auth/oauth2/exchange')
        ) {
            originalRequest._retry = true;

            // Параллельный 401 пришёл уже после refresh соседнего запроса — токен свежий, просто повторяем
            const currentToken = localStorage.getItem('accessToken');
            if (currentToken && originalRequest.headers.Authorization !== `Bearer ${currentToken}`) {
                originalRequest.headers.Authorization = `Bearer ${currentToken}`;
                return apiClient(originalRequest);
            }

            const refreshToken = localStorage.getItem('refreshToken');
            if (!refreshToken) {
                throw error;
            }

            try {
                // single-flight: параллельные 401 ждут один общий refresh
                refreshPromise ??= axios
                    .post(
                        `${API_BASE_URL}/v1/auth/refresh`,
                        { refreshToken },
                        { headers: { 'Accept': 'application/json' } },
                    )
                    .then(({ data }) => {
                        const access = data.access_token || data.accessToken;
                        const refresh = data.refresh_token || data.refreshToken;
                        if (!access) {
                            throw new Error('Refresh response without access token');
                        }
                        localStorage.setItem('accessToken', access);
                        if (refresh) {
                            localStorage.setItem('refreshToken', refresh);
                        }
                        return access as string;
                    })
                    .finally(() => { refreshPromise = null; });
                const newAccessToken = await refreshPromise;

                originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
                return apiClient(originalRequest);
            } catch {
                // Refresh не удался — просто reject, authStore разберётся
                throw error;
            }
        }

        throw error;
    },
);

export default apiClient;