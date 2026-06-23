import { create } from 'zustand';
import { getFavouriteIds, addFavourite, removeFavourite } from '@/api/favourites';
import { queryClient } from '@/lib/queryClient';

interface FavouritesState {
    ids: Set<number>;
    loading: boolean;
    fetchIds: () => Promise<void>;
    toggle: (productId: number) => Promise<void>;
    isFavourite: (productId: number) => boolean;
    clear: () => void;
}

export const useFavouritesStore = create<FavouritesState>((set, get) => ({
    ids: new Set(),
    loading: false,

    fetchIds: async () => {
        set({ loading: true });
        try {
            const ids = await getFavouriteIds();
            set({ ids: new Set(ids) });
        } catch {
            // не авторизован — оставляем пустым
        } finally {
            set({ loading: false });
        }
    },

    toggle: async (productId) => {
        const { ids } = get();
        if (ids.has(productId)) {
            set({ ids: new Set([...ids].filter((id) => id !== productId)) });
            await removeFavourite(productId);
        } else {
            set({ ids: new Set([...ids, productId]) });
            await addFavourite(productId);
        }
        queryClient.invalidateQueries({ queryKey: ['favourites', 'products'] });
    },

    isFavourite: (productId) => get().ids.has(productId),

    clear: () => set({ ids: new Set() }),
}));
