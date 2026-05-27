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

export const useCartStore = create<CartState>((set, get) => ({
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
        const prev = get();
        const existing = prev.items.find(i => i.productId === productId);
        if (existing) {
            const updatedItems = prev.items.map(i =>
                i.productId === productId ? { ...i, quantity: i.quantity + quantity } : i
            );
            set({
                items: updatedItems,
                totalItems: updatedItems.reduce((s, i) => s + i.quantity, 0),
                totalAmount: updatedItems.reduce((s, i) => s + i.price * i.quantity, 0),
            });
        }
        try {
            await cartApi.addToCart({ productId, quantity });
            await get().fetchCart();
        } catch {
            set({ items: prev.items, totalItems: prev.totalItems, totalAmount: prev.totalAmount });
            throw new Error('Не удалось добавить товар');
        }
    },

    updateQuantity: async (productId, quantity) => {
        if (quantity <= 0) { await get().removeItem(productId); return; }
        const prev = get();
        const updatedItems = prev.items.map(i =>
            i.productId === productId ? { ...i, quantity } : i
        );
        set({
            items: updatedItems,
            totalItems: updatedItems.reduce((s, i) => s + i.quantity, 0),
            totalAmount: updatedItems.reduce((s, i) => s + i.price * i.quantity, 0),
        });
        try {
            await cartApi.updateCartItem(productId, quantity);
        } catch {
            set({ items: prev.items, totalItems: prev.totalItems, totalAmount: prev.totalAmount });
            throw new Error('Не удалось обновить количество');
        }
    },

    removeItem: async (productId) => {
        const prev = get();
        const updatedItems = prev.items.filter(i => i.productId !== productId);
        set({
            items: updatedItems,
            totalItems: updatedItems.reduce((s, i) => s + i.quantity, 0),
            totalAmount: updatedItems.reduce((s, i) => s + i.price * i.quantity, 0),
        });
        try {
            await cartApi.removeCartItem(productId);
        } catch {
            set({ items: prev.items, totalItems: prev.totalItems, totalAmount: prev.totalAmount });
            throw new Error('Не удалось удалить товар');
        }
    },

    clearCart: async () => {
        await cartApi.clearCart();
        set({ items: [], totalItems: 0, totalAmount: 0 });
    },

    resetCart: () => {
        set({ items: [], totalItems: 0, totalAmount: 0, isLoading: false });
    },
}));