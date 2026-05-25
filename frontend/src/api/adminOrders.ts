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
  const { data } = await apiClient.get<Page<OrderSummaryDto>>('/v1/orders/admin', { params });
  return data;
};

export const changeOrderStatus = async (orderId: string, status: string): Promise<void> => {
  await apiClient.patch(`/v1/orders/${orderId}/status`, { status });
};
