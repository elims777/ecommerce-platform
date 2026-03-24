/** Способы оплаты — маппится на PaymentMethod enum */
export enum PaymentMethod {
    CARD = 'CARD',
    SBP = 'SBP',
    CASH_ON_DELIVERY = 'CASH_ON_DELIVERY',
}

export const PaymentMethodLabels: Record<PaymentMethod, string> = {
    [PaymentMethod.CARD]: 'Банковская карта',
    [PaymentMethod.SBP]: 'Система быстрых платежей',
    [PaymentMethod.CASH_ON_DELIVERY]: 'Оплата при получении',
};

/** Способы доставки — маппится на DeliveryMethod enum */
export enum DeliveryMethod {
    PICKUP = 'PICKUP',
    SUPPLIER_DELIVERY = 'SUPPLIER_DELIVERY',
}

export const DeliveryMethodLabels: Record<DeliveryMethod, string> = {
    [DeliveryMethod.PICKUP]: 'Самовывоз',
    [DeliveryMethod.SUPPLIER_DELIVERY]: 'Доставка поставщиком',
};

/** Адрес доставки — маппится на AddressDto */
export interface AddressDto {
    city: string;
    street: string;
    building: string;
    apartment?: string;
    postalCode?: string;
    phone: string;
    recipientName: string;
}

/** Запрос на создание заказа — маппится на CreateOrderRequest */
export interface CreateOrderRequest {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    deliveryAddress?: AddressDto;
    warehousePointId?: number;
    comment?: string;
}

/** Статус заказа */
export enum OrderStatus {
    NEW = 'NEW',
    AWAITING_CONFIRMATION = 'AWAITING_CONFIRMATION',
    CONFIRMED = 'CONFIRMED',
    IN_PROGRESS = 'IN_PROGRESS',
    SHIPPED = 'SHIPPED',
    DELIVERED = 'DELIVERED',
    CANCELLED = 'CANCELLED',
}

export const OrderStatusLabels: Record<OrderStatus, string> = {
    [OrderStatus.NEW]: 'Новый',
    [OrderStatus.AWAITING_CONFIRMATION]: 'Ожидает подтверждения',
    [OrderStatus.CONFIRMED]: 'Подтверждён',
    [OrderStatus.IN_PROGRESS]: 'В обработке',
    [OrderStatus.SHIPPED]: 'Отправлен',
    [OrderStatus.DELIVERED]: 'Доставлен',
    [OrderStatus.CANCELLED]: 'Отменён',
};

/** Позиция заказа */
export interface OrderItemDto {
    id: number;
    productId: number;
    productName: string;
    quantity: number;
    price: number;
    totalPrice: number;
}

/** Заказ — ответ от order-service */
export interface OrderDto {
    id: number;
    orderNumber: string;
    userId: number;
    customerEmail: string;
    status: OrderStatus;
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    totalAmount: number;
    items: OrderItemDto[];
    deliveryAddress?: AddressDto;
    comment?: string;
    createdAt: string;
    updatedAt: string;
}