/** Роль пользователя */
export interface UserRole {
    id: number;
    name: string;
}

/** Данные пользователя — маппится на UserDto из user-service */
export interface User {
    id: number;
    email: string;
    firstname: string;
    lastname: string;
    surname: string | null;
    phone: string | null;
    emailVerified: boolean;
    roles: UserRole[];
    createdAt: string;
    updatedAt: string;
}

/** Запрос на логин — маппится на SimpleAuthRequest */
export interface LoginRequest {
    email: string;
    password: string;
}

/** Запрос на регистрацию — маппится на RegistrationRequest */
export interface RegisterRequest {
    email: string;
    password: string;
    firstname: string;
    lastname: string;
    surname?: string;
    phone: string;
}

/** Ответ от auth-service при логине — маппится на AuthResponse */
export interface AuthTokens {
    access_token: string;
    refresh_token: string;
    token_type: string;
    expires_in: number;
    user: User;
}

/** Проверка роли */
export const hasRole = (user: User, roleName: string): boolean =>
    user.roles.some((r) => r.name === roleName);

/** Проверка роли ADMIN */
export const isAdmin = (user: User): boolean => hasRole(user, 'ROLE_ADMIN');