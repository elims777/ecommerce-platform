import apiClient from './client';

/** Элемент корзины — CartItemDto из Swagger */
export interface CartItemDto {
    productId: number;
    productName: string;
    quantity: number;
    price: number;
    subtotal: number;
    parentProductId?: number | null;
}

/** Корзина — CartDto из Swagger */
export interface CartDto {
    userId: number;
    items: CartItemDto[];
    totalItems: number;
    totalAmount: number;
}

/** Запрос на добавление — AddToCartRequest */
export interface AddToCartRequest {
    productId: number;
    quantity: number;
}

/** Запрос на обновление количества — UpdateCartItemRequest */
export interface UpdateCartItemRequest {
    quantity: number;
}

/** Получить корзину — GET /api/v1/cart */
export const getCart = async (): Promise<CartDto> => {
    const { data } = await apiClient.get<CartDto>('/v1/cart');
    return data;
};

/** Добавить товар — POST /api/v1/cart/items */
export const addToCart = async (request: AddToCartRequest): Promise<CartDto> => {
    const { data } = await apiClient.post<CartDto>('/v1/cart/items', request);
    return data;
};

/** Изменить количество — PUT /api/v1/cart/items/{productId} */
export const updateCartItem = async (
    productId: number,
    quantity: number,
): Promise<CartDto> => {
    const { data } = await apiClient.put<CartDto>(
        `/v1/cart/items/${productId}`,
        { quantity },
    );
    return data;
};

/** Удалить товар — DELETE /api/v1/cart/items/{productId} */
export const removeCartItem = async (productId: number): Promise<CartDto> => {
    const { data } = await apiClient.delete<CartDto>(
        `/v1/cart/items/${productId}`,
    );
    return data;
};

/** Очистить корзину — DELETE /api/v1/cart */
export const clearCart = async (): Promise<void> => {
    await apiClient.delete('/v1/cart');
};
