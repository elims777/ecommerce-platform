/** Способы оплаты */
export enum PaymentMethod {
    CARD = 'CARD',
    SBP = 'SBP',
    CASH_ON_DELIVERY = 'CASH_ON_DELIVERY',
    INVOICE = 'INVOICE',
}

export const PaymentMethodLabels: Record<PaymentMethod, string> = {
    [PaymentMethod.CARD]: 'Банковская карта',
    [PaymentMethod.SBP]: 'Система быстрых платежей (СБП)',
    [PaymentMethod.CASH_ON_DELIVERY]: 'Оплата при получении',
    [PaymentMethod.INVOICE]: 'Выставить счёт',
};

/** Способы доставки */
export enum DeliveryMethod {
    PICKUP = 'PICKUP',
    SUPPLIER_DELIVERY = 'SUPPLIER_DELIVERY',
}

export const DeliveryMethodLabels: Record<DeliveryMethod, string> = {
    [DeliveryMethod.PICKUP]: 'Самовывоз',
    [DeliveryMethod.SUPPLIER_DELIVERY]: 'Доставка поставщиком',
};

/** Адрес доставки */
export interface AddressDto {
    city: string;
    street: string;
    building: string;
    apartment?: string;
    postalCode?: string;
    phone: string;
    recipientName: string;
}

/** Статусы заказа — по Swagger */
export enum OrderStatus {
    CREATED = 'CREATED',
    PENDING_PAYMENT = 'PENDING_PAYMENT',
    PAID = 'PAID',
    PAYMENT_FAILED = 'PAYMENT_FAILED',
    PROCESSING = 'PROCESSING',
    SHIPPED = 'SHIPPED',
    IN_TRANSIT = 'IN_TRANSIT',
    DELIVERED = 'DELIVERED',
    CANCELLED = 'CANCELLED',
    REFUNDED = 'REFUNDED',
    AWAITING_CONFIRMATION = 'AWAITING_CONFIRMATION',
    INVOICE_SENT = 'INVOICE_SENT',
    PARTIALLY_PAID = 'PARTIALLY_PAID',
    COMPLETED = 'COMPLETED',
}

export const OrderStatusLabels: Record<OrderStatus, string> = {
    [OrderStatus.CREATED]: 'Создан',
    [OrderStatus.PENDING_PAYMENT]: 'Ожидает оплаты',
    [OrderStatus.PAID]: 'Оплачен',
    [OrderStatus.PAYMENT_FAILED]: 'Ошибка оплаты',
    [OrderStatus.PROCESSING]: 'В обработке',
    [OrderStatus.SHIPPED]: 'Отгружен',
    [OrderStatus.IN_TRANSIT]: 'В пути',
    [OrderStatus.DELIVERED]: 'Доставлен',
    [OrderStatus.CANCELLED]: 'Отменён',
    [OrderStatus.REFUNDED]: 'Возврат средств',
    [OrderStatus.AWAITING_CONFIRMATION]: 'Ожидает подтверждения',
    [OrderStatus.INVOICE_SENT]: 'Счёт выставлен',
    [OrderStatus.PARTIALLY_PAID]: 'Частично оплачен',
    [OrderStatus.COMPLETED]: 'Завершен',
};

/** Запрос на создание заказа */
export interface CreateOrderRequest {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    deliveryAddress?: AddressDto;
    warehousePointId?: number;
    comment?: string;
    companyName?: string;
    inn?: string;
    customerName?: string;
    customerPhone?: string;
}

/** Позиция заказа */
export interface OrderItemDto {
    productId: number;
    productName: string;
    quantity: number;
    price: number;
    subtotal: number;
}

/** Позиция заказа в запросе на редактирование */
export interface UpdateOrderItemRequest {
    productId: number;
    quantity: number;
}

/** Запрос на редактирование заказа — разрешён только в статусе CREATED */
export interface UpdateOrderRequest {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    deliveryAddress?: AddressDto;
    warehousePointId?: number;
    items: UpdateOrderItemRequest[];
    comment?: string;
}

/** Точка самовывоза */
export interface WarehousePointDto {
    id: number;
    name: string;
    city: string;
    street: string;
    building: string;
    postalCode: string;
    phone: string;
    workingHours: string;
    description: string;
    active: boolean;
}

/** Полный заказ — id это UUID (string) */
export interface OrderDto {
    id: string;
    userId: number;
    orderNumber: string;
    status: OrderStatus;
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    totalAmount: number;
    items: OrderItemDto[];
    deliveryAddress?: AddressDto;
    warehousePoint?: WarehousePointDto;
    trackingNumber?: string;
    customerEmail: string;
    customerName?: string | null;
    customerPhone?: string | null;
    companyName?: string;
    inn?: string;
    comment?: string;
    createdAt: string;
    updatedAt: string;
}

/** Краткая сводка заказа — для списка */
export interface OrderSummaryDto {
    id: string;
    orderNumber: string;
    status: OrderStatus;
    customerType?: unknown;
    itemsCount: number;
    totalAmount: number;
    customerEmail: string;
    customerName?: string | null;
    customerPhone?: string | null;
    companyName?: string | null;
    createdAt: string;
}