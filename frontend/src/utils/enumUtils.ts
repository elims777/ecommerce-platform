/**
 * Бэкенд может возвращать enum-значения в двух форматах:
 * - Строка: "CREATED"
 * - Объект: { code: "CREATED", displayName: "Создан" }
 *
 * Эта утилита нормализует оба варианта в строку.
 */
export const extractEnumCode = (value: unknown): string => {
    if (typeof value === 'string') return value;
    if (value && typeof value === 'object' && 'code' in value) {
        return (value as { code: string }).code;
    }
    return String(value);
};

export const extractEnumDisplayName = (value: unknown, fallback?: string): string => {
    if (value && typeof value === 'object' && 'displayName' in value) {
        return (value as { displayName: string }).displayName;
    }
    return fallback || extractEnumCode(value);
};