import { create } from 'zustand';
import * as cartApi from '@/api/cart';
import type { CartItemDto } from '@/api/cart';

const GUEST_CART_KEY = 'guestCart';

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
    mergeGuestCart: () => Promise<void>;
}

const isAuthed = () => !!localStorage.getItem('accessToken');

const calcTotals = (items: CartItemDto[]) => ({
    totalItems: items.reduce((s, i) => s + i.quantity, 0),
    totalAmount: items.reduce((s, i) => s + i.price * i.quantity, 0),
});

const loadGuestCart = (): CartItemDto[] => {
    try {
        return JSON.parse(localStorage.getItem(GUEST_CART_KEY) || '[]');
    } catch {
        return [];
    }
};

const saveGuestCart = (items: CartItemDto[]) =>
    localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));

const makeGuestItem = (productId: number, quantity: number): CartItemDto => ({
    productId,
    productName: '',
    quantity,
    price: 0,
    subtotal: 0,
});

export const useCartStore = create<CartState>((set, get) => ({
    items: [],
    totalItems: 0,
    totalAmount: 0,
    isLoading: false,

    fetchCart: async () => {
        if (!isAuthed()) {
            const items = loadGuestCart();
            set({ items, ...calcTotals(items) });
            return;
        }
        set({ isLoading: true });
        try {
            const cart = await cartApi.getCart();
            set({ items: cart.items, totalItems: cart.totalItems, totalAmount: cart.totalAmount, isLoading: false });
        } catch {
            set({ isLoading: false });
        }
    },

    addItem: async (productId, quantity) => {
        if (!isAuthed()) {
            const items = loadGuestCart();
            const idx = items.findIndex(i => i.productId === productId);
            if (idx >= 0) {
                items[idx] = { ...items[idx], quantity: items[idx].quantity + quantity };
            } else {
                items.push(makeGuestItem(productId, quantity));
            }
            saveGuestCart(items);
            set({ items, ...calcTotals(items) });
            return;
        }

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

        if (!isAuthed()) {
            const items = loadGuestCart().map(i =>
                i.productId === productId ? { ...i, quantity } : i
            );
            saveGuestCart(items);
            set({ items, ...calcTotals(items) });
            return;
        }

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
        if (!isAuthed()) {
            const items = loadGuestCart().filter(i => i.productId !== productId);
            saveGuestCart(items);
            set({ items, ...calcTotals(items) });
            return;
        }

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
        if (!isAuthed()) {
            localStorage.removeItem(GUEST_CART_KEY);
            set({ items: [], totalItems: 0, totalAmount: 0 });
            return;
        }
        await cartApi.clearCart();
        set({ items: [], totalItems: 0, totalAmount: 0 });
    },

    resetCart: () => {
        set({ items: [], totalItems: 0, totalAmount: 0, isLoading: false });
    },

    mergeGuestCart: async () => {
        const guestItems = loadGuestCart();
        if (guestItems.length === 0) return;
        await Promise.allSettled(
            guestItems.map(i => cartApi.addToCart({ productId: i.productId, quantity: i.quantity }))
        );
        localStorage.removeItem(GUEST_CART_KEY);
        await get().fetchCart();
    },
}));
