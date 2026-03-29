import { create } from 'zustand';
import * as cartApi from '@/api/cart';
import type { CartItemDto } from '@/api/cart';

interface CartState {
    items: CartItemDto[];
    totalItems: number;
    totalAmount: number;
    isLoading: boolean;

    /** Загрузить корзину с сервера */
    fetchCart: () => Promise<void>;

    /** Добавить товар в корзину */
    addItem: (productId: number, quantity: number) => Promise<void>;

    /** Обновить количество товара */
    updateQuantity: (productId: number, quantity: number) => Promise<void>;

    /** Удалить товар из корзины */
    removeItem: (productId: number) => Promise<void>;

    /** Очистить корзину */
    clearCart: () => Promise<void>;

    /** Сбросить состояние (при logout) */
    resetCart: () => void;
}

export const useCartStore = create<CartState>((set) => ({
    items: [],
    totalItems: 0,
    totalAmount: 0,
    isLoading: false,

    fetchCart: async () => {
        set({ isLoading: true });
        try {
            const cart = await cartApi.getCart();
            set({
                items: cart.items,
                totalItems: cart.totalItems,
                totalAmount: cart.totalAmount,
                isLoading: false,
            });
        } catch {
            set({ isLoading: false });
        }
    },

    addItem: async (productId, quantity) => {
        const cart = await cartApi.addToCart({ productId, quantity });
        set({
            items: cart.items,
            totalItems: cart.totalItems,
            totalAmount: cart.totalAmount,
        });
    },

    updateQuantity: async (productId, quantity) => {
        if (quantity <= 0) {
            const cart = await cartApi.removeCartItem(productId);
            set({
                items: cart.items,
                totalItems: cart.totalItems,
                totalAmount: cart.totalAmount,
            });
        } else {
            const cart = await cartApi.updateCartItem(productId, quantity);
            set({
                items: cart.items,
                totalItems: cart.totalItems,
                totalAmount: cart.totalAmount,
            });
        }
    },

    removeItem: async (productId) => {
        const cart = await cartApi.removeCartItem(productId);
        set({
            items: cart.items,
            totalItems: cart.totalItems,
            totalAmount: cart.totalAmount,
        });
    },

    clearCart: async () => {
        await cartApi.clearCart();
        set({ items: [], totalItems: 0, totalAmount: 0 });
    },

    resetCart: () => {
        set({ items: [], totalItems: 0, totalAmount: 0, isLoading: false });
    },
}));