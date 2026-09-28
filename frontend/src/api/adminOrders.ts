import apiClient from '@/api/client';
import type { OrderSummaryDto, OrderStatus } from '@/types/order';
import type { Page } from '@/types/product';

export type { Page };

export interface AdminOrdersParams {
  page?: number;
  size?: number;
  status?: OrderStatus;
  userId?: number;
  dateFrom?: string;
  dateTo?: string;
}

export const getAdminOrders = async (params: AdminOrdersParams): Promise<Page<OrderSummaryDto>> => {
  const { data } = await apiClient.get<Page<OrderSummaryDto>>('/v1/admin/orders', { params });
  return data;
};

export const getAdminOrder = async (orderId: string): Promise<import('@/types/order').OrderDto> => {
  const { data } = await apiClient.get(`/v1/admin/orders/${orderId}`);
  return data;
};

export const changeOrderStatus = async (orderId: string, status: string): Promise<void> => {
  await apiClient.patch(`/v1/admin/orders/${orderId}/status`, { status });
};

/** Сводка по заказам для админки */
export interface OrderStats {
  total: number;
  inProgress: number;
  completed: number;
  cancelled: number;
  newLast30Days: number;
}

export const getOrderStats = async (): Promise<OrderStats> => {
  const { data } = await apiClient.get<OrderStats>('/v1/admin/orders/stats');
  return data;
};
