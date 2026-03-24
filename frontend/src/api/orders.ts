import apiClient from './client';
import type { CreateOrderRequest, OrderDto } from '@/types/order';
import type { Page } from '@/types/product';

/** Создать заказ (товары берутся из серверной корзины) */
export const createOrder = async (
    request: CreateOrderRequest,
): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>('/v1/orders', request);
    return data;
};

/** Получить заказы текущего пользователя с пагинацией */
export const getMyOrders = async (
    page = 0,
    size = 10,
): Promise<Page<OrderDto>> => {
    const { data } = await apiClient.get<Page<OrderDto>>('/v1/orders/my', {
        params: { page, size, sort: 'createdAt,desc' },
    });
    return data;
};

/** Получить заказ по ID */
export const getOrderById = async (id: number): Promise<OrderDto> => {
    const { data } = await apiClient.get<OrderDto>(`/v1/orders/${id}`);
    return data;
};