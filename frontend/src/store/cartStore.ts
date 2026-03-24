import { create } from 'zustand';

/** Товар в корзине — минимальный набор данных для отображения */
export interface CartItem {
    productId: number;
    name: string;
    price: number;
    quantity: number;
    imageUrl: string | null;
    unitOfMeasure: string | null;
    maxStock: number;
}

interface CartState {
    items: CartItem[];

    /** Добавить товар (если уже есть — увеличивает количество) */
    addItem: (item: Omit<CartItem, 'quantity'>, quantity?: number) => void;

    /** Обновить количество конкретного товара */
    updateQuantity: (productId: number, quantity: number) => void;

    /** Удалить товар из корзины */
    removeItem: (productId: number) => void;

    /** Очистить корзину */
    clearCart: () => void;

    /** Общее количество позиций */
    getTotalItems: () => number;

    /** Общая сумма */
    getTotalPrice: () => number;
}

/** Загрузка корзины из localStorage */
const loadCart = (): CartItem[] => {
    try {
        const saved = localStorage.getItem('cart');
        return saved ? JSON.parse(saved) : [];
    } catch {
        return [];
    }
};

/** Сохранение корзины в localStorage */
const saveCart = (items: CartItem[]) => {
    localStorage.setItem('cart', JSON.stringify(items));
};

export const useCartStore = create<CartState>((set, get) => ({
    items: loadCart(),

    addItem: (item, quantity = 1) => {
        set((state) => {
            const existingIndex = state.items.findIndex(
                (i) => i.productId === item.productId,
            );

            let newItems: CartItem[];

            if (existingIndex >= 0) {
                // Товар уже в корзине — увеличиваем количество (не больше maxStock)
                newItems = state.items.map((i, idx) =>
                    idx === existingIndex
                        ? {
                            ...i,
                            quantity: Math.min(i.quantity + quantity, i.maxStock),
                        }
                        : i,
                );
            } else {
                // Новый товар
                newItems = [...state.items, { ...item, quantity }];
            }

            saveCart(newItems);
            return { items: newItems };
        });
    },

    updateQuantity: (productId, quantity) => {
        set((state) => {
            const newItems =
                quantity <= 0
                    ? state.items.filter((i) => i.productId !== productId)
                    : state.items.map((i) =>
                        i.productId === productId
                            ? { ...i, quantity: Math.min(quantity, i.maxStock) }
                            : i,
                    );

            saveCart(newItems);
            return { items: newItems };
        });
    },

    removeItem: (productId) => {
        set((state) => {
            const newItems = state.items.filter((i) => i.productId !== productId);
            saveCart(newItems);
            return { items: newItems };
        });
    },

    clearCart: () => {
        localStorage.removeItem('cart');
        set({ items: [] });
    },

    getTotalItems: () =>
        get().items.reduce((sum, item) => sum + item.quantity, 0),

    getTotalPrice: () =>
        get().items.reduce((sum, item) => sum + item.price * item.quantity, 0),
}));