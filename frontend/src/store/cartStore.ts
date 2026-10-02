import { create } from 'zustand';
import * as cartApi from '@/api/cart';
import type { CartItemDto } from '@/api/cart';
import { pushEcommerce } from '@/lib/metrika';

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

/** id в ecommerce — id родительского товара, как offer id в товарном фиде Директа. */
const trackCart = (kind: 'add' | 'remove', item: CartItemDto, quantity: number) =>
    pushEcommerce({
        [kind]: {
            products: [{
                id: String(item.parentProductId ?? item.productId),
                name: item.productName,
                price: item.price,
                quantity,
            }],
        },
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
            const added = get().items.find(i => i.productId === productId);
            if (added) trackCart('add', added, quantity);
        } catch (err) {
            set({ items: prev.items, ...calcTotals(prev.items) });
            throw err;
        }
    },

    updateQuantity: async (productId, quantity) => {
        if (quantity <= 0) { await get().removeItem(productId); return; }
        const prev = get();
        const updatedItems = prev.items.map(i => i.productId === productId ? { ...i, quantity } : i);
        set({ items: updatedItems, ...calcTotals(updatedItems) });
        try {
            await cartApi.updateCartItem(productId, quantity);
            const current = prev.items.find(i => i.productId === productId);
            if (current && quantity !== current.quantity) {
                trackCart(quantity > current.quantity ? 'add' : 'remove', current, Math.abs(quantity - current.quantity));
            }
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
            const removed = prev.items.find(i => i.productId === productId);
            if (removed) trackCart('remove', removed, removed.quantity);
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
