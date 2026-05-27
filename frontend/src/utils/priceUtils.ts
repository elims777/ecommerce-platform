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
