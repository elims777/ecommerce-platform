import { create } from 'zustand';
import * as cartApi from '@/api/cart';
import type { CartItemDto } from '@/api/cart';

interface CartState {
    items: CartItemDto[];
    totalItems: number;
    totalAmount: number;
    isLoading: boolean;

    fetchCart: () => Promise<void>;
    addItem: (productId: number, quantity: number) => Promise<void>;
    updateQuantity: (productId: number, quantity: number) => Promise<void>;
    removeItem: (productId: number) => Promise<void>;
    clearCart: () => Promise<void>;
    resetCart: () => void;
}

const calcTotals = (items: CartItemDto[]) => ({
    totalItems: items.reduce((s, i) => s + i.quantity, 0),
    totalAmount: items.reduce((s, i) => s + i.price * i.quantity, 0),
});

export const useCartStore = create<CartState>((set, get) => ({
    items: [],
    totalItems: 0,
    totalAmount: 0,
    isLoading: false,

    fetchCart: async () => {
        set({ isLoading: true });
        try {
            const cart = await cartApi.getCart();
            set({ items: cart.items, totalItems: cart.totalItems, totalAmount: cart.totalAmount, isLoading: false });
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
            set({ items: updatedItems, ...calcTotals(updatedItems) });
        }
        try {
            await cartApi.addToCart({ productId, quantity });
            await get().fetchCart();
        } catch {
            set({ items: prev.items, ...calcTotals(prev.items) });
            throw new Error('Не удалось добавить товар');
        }
    },

    updateQuantity: async (productId, quantity) => {
        if (quantity <= 0) { await get().removeItem(productId); return; }
        const prev = get();
        const updatedItems = prev.items.map(i => i.productId === productId ? { ...i, quantity } : i);
        set({ items: updatedItems, ...calcTotals(updatedItems) });
        try {
            await cartApi.updateCartItem(productId, quantity);
        } catch {
            set({ items: prev.items, ...calcTotals(prev.items) });
            throw new Error('Не удалось обновить количество');
        }
    },

    removeItem: async (productId) => {
        const prev = get();
        const updatedItems = prev.items.filter(i => i.productId !== productId);
        set({ items: updatedItems, ...calcTotals(updatedItems) });
        try {
            await cartApi.removeCartItem(productId);
        } catch {
            set({ items: prev.items, ...calcTotals(prev.items) });
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
