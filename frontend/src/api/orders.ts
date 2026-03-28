import apiClient from './client';
import type { CreateOrderRequest, OrderDto, OrderSummaryDto } from '@/types/order';
import type { Page } from '@/types/product';

/** Создать заказ из серверной корзины — POST /api/v1/orders */
export const createOrder = async (
    request: CreateOrderRequest,
): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>('/v1/orders', request);
    return data;
};

/** Получить заказы текущего пользователя — GET /api/v1/orders */
export const getMyOrders = async (
    page = 0,
    size = 10,
): Promise<Page<OrderSummaryDto>> => {
    const { data } = await apiClient.get<Page<OrderSummaryDto>>('/v1/orders', {
        params: { page, size, sort: 'createdAt,desc' },
    });
    return data;
};

/** Получить полный заказ по UUID — GET /api/v1/orders/{orderId} */
export const getOrderById = async (id: string): Promise<OrderDto> => {
    const { data } = await apiClient.get<OrderDto>(`/v1/orders/${id}`);
    return data;
};

/** Отменить заказ — POST /api/v1/orders/{orderId}/cancel */
export const cancelOrder = async (id: string): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>(`/v1/orders/${id}/cancel`);
    return data;
};

/** Повторить заказ — POST /api/v1/orders/{orderId}/repeat */
export const repeatOrder = async (id: string): Promise<OrderDto> => {
    const { data } = await apiClient.post<OrderDto>(`/v1/orders/${id}/repeat`);
    return data;
};