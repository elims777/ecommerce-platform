/** Роль пользователя */
export interface UserRole {
    id: number;
    name: string;
}

export type ClientType = 'B2C' | 'B2B';

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
    clientType: ClientType;
    companyName?: string | null;
    inn?: string | null;
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
    newsletterConsent?: boolean;
}

/** Ответ от auth-service при логине — маппится на AuthResponse */
export interface AuthTokens {
    access_token: string;
    refresh_token: string;
    token_type: string;
    expires_in: number;
    user: User;
    clientType?: string;
    companyName?: string | null;
    inn?: string | null;
}

/** Проверка роли */
export const hasRole = (user: User, roleName: string): boolean =>
    user.roles.some((r) => r.name === roleName);

/** Проверка роли ADMIN */
export const isAdmin = (user: User): boolean => hasRole(user, 'ROLE_ADMIN');