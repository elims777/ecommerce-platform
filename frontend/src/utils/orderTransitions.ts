import { OrderStatus } from '@/types/order';

export const ALLOWED_TRANSITIONS: Record<string, OrderStatus[]> = {
  CREATED: [OrderStatus.PROCESSING, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED],
  PROCESSING: [OrderStatus.INVOICE_SENT, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED],
  INVOICE_SENT: [OrderStatus.PENDING_PAYMENT, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.CANCELLED],
  PENDING_PAYMENT: [OrderStatus.PAID, OrderStatus.PARTIALLY_PAID, OrderStatus.PAYMENT_FAILED, OrderStatus.CANCELLED],
  PAYMENT_FAILED: [OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED],
  AWAITING_CONFIRMATION: [OrderStatus.SHIPPED, OrderStatus.CANCELLED],
  PAID: [OrderStatus.SHIPPED, OrderStatus.REFUNDED, OrderStatus.COMPLETED],
  PARTIALLY_PAID: [OrderStatus.SHIPPED, OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED],
  SHIPPED: [OrderStatus.IN_TRANSIT, OrderStatus.DELIVERED],
  IN_TRANSIT: [OrderStatus.DELIVERED],
  DELIVERED: [OrderStatus.PAID, OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED],
  CANCELLED: [OrderStatus.REFUNDED, OrderStatus.COMPLETED],
  REFUNDED: [OrderStatus.COMPLETED],
  COMPLETED: [],
};

export function getAllowedTransitions(currentStatus: string): OrderStatus[] {
  return ALLOWED_TRANSITIONS[currentStatus] ?? [];
}
