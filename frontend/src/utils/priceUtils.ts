import { useAuthStore } from '@/store/authStore';

interface HasPrice {
    price: number;
    wholesalePrice?: number | null;
}

export const useDisplayPrice = (product: HasPrice): number => {
    const clientType = useAuthStore((s) => s.user?.clientType ?? 'B2C');
    return clientType === 'B2B'
        ? product.price
        : (product.wholesalePrice ?? product.price);
};

export const PRICE_PLACEHOLDER = 'Уточнить стоимость';

export const isPriceAvailable = (price: number | null | undefined): boolean =>
    typeof price === 'number' && price > 0;

const formatRub = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

export const formatPriceOrPlaceholder = (price: number | null | undefined): string =>
    isPriceAvailable(price) ? formatRub(price as number) : PRICE_PLACEHOLDER;
