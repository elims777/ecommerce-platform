const STORAGE_KEY = 'pendingAddToCart';
const TTL_MS = 10 * 60 * 1000;

interface PendingItem {
    productId: number;
    quantity: number;
}

interface PendingAddToCart {
    items: PendingItem[];
    savedAt: number;
}

const isValidPendingItem = (v: unknown): v is PendingItem =>
    typeof v === 'object' && v !== null
    && typeof (v as PendingItem).productId === 'number'
    && typeof (v as PendingItem).quantity === 'number'
    && (v as PendingItem).quantity > 0;

export const savePendingAddToCart = (items: PendingItem[]): void => {
    if (!items.length) return;
    try {
        const payload: PendingAddToCart = { items, savedAt: Date.now() };
        localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
    } catch (e) {
        console.warn('[pendingCart] не удалось сохранить в localStorage', e);
    }
};

export const clearPendingAddToCart = (): void => {
    try {
        localStorage.removeItem(STORAGE_KEY);
    } catch {
        // ignore
    }
};

export interface ConsumeResult {
    added: number;
    failed: number;
}

export const consumePendingAddToCart = async (
    addItem: (productId: number, quantity: number) => Promise<void>,
): Promise<ConsumeResult> => {
    let raw: string | null;
    try {
        raw = localStorage.getItem(STORAGE_KEY);
    } catch {
        return { added: 0, failed: 0 };
    }
    if (!raw) return { added: 0, failed: 0 };

    clearPendingAddToCart();

    let parsed: unknown;
    try {
        parsed = JSON.parse(raw);
    } catch {
        return { added: 0, failed: 0 };
    }

    const payload = parsed as PendingAddToCart;
    if (typeof payload?.savedAt === 'number' && Date.now() - payload.savedAt > TTL_MS) {
        return { added: 0, failed: 0 };
    }

    const items = payload?.items;
    if (!Array.isArray(items)) return { added: 0, failed: 0 };

    const validItems = items.filter(isValidPendingItem);
    if (!validItems.length) return { added: 0, failed: 0 };

    const results = await Promise.allSettled(
        validItems.map(i => addItem(i.productId, i.quantity)),
    );
    const added = results.filter(r => r.status === 'fulfilled').length;
    const failed = results.length - added;

    if (failed > 0) {
        console.warn('[pendingCart] часть товаров не добавилась после логина', results);
    }

    return { added, failed };
};
