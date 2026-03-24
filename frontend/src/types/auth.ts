/** Роли пользователей — маппятся на JWT claims из auth-service */
export enum UserRole {
    CUSTOMER = 'CUSTOMER',
    ADMIN = 'ADMIN',
}

/** Данные пользователя из JWT / user-service */
export interface User {
    id: number;
    email: string;
    firstName: string;
    lastName: string;
    role: UserRole;
}

/** Запрос на логин */
export interface LoginRequest {
    email: string;
    password: string;
}

/** Запрос на регистрацию */
export interface RegisterRequest {
    email: string;
    password: string;
    firstName: string;
    lastName: string;
}

/** Ответ от auth-service при логине/регистрации */
export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    user: User;
}

/** Запрос на обновление токенов */
export interface RefreshTokenRequest {
    refreshToken: string;
}