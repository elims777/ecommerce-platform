import { useState } from 'react';
import {
    Table,
    Tag,
    Typography,
    Button,
    Card,
    Spin,
    Empty,
    Descriptions,
    Pagination,
} from 'antd';
import { ShoppingOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getMyOrders, getOrderById } from '@/api/orders';
import {
    OrderStatusLabels,
    PaymentMethodLabels,
    DeliveryMethodLabels,
} from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import type { OrderSummaryDto, OrderDto, OrderItemDto, OrderStatus } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

/** Форматирует цену */
const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

/** Форматирует дату */
const formatDate = (dateStr: string): string =>
    new Date(dateStr).toLocaleDateString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });

/** Цвет тега по статусу */
const getStatusColor = (status: string): string => {
    const colors: Record<string, string> = {
        CREATED: 'blue',
        PENDING_PAYMENT: 'orange',
        PAID: 'cyan',
        PAYMENT_FAILED: 'red',
        PROCESSING: 'processing',
        SHIPPED: 'purple',
        IN_TRANSIT: 'geekblue',
        DELIVERED: 'green',
        CANCELLED: 'default',
        REFUNDED: 'magenta',
        AWAITING_CONFIRMATION: 'gold',
    };
    return colors[status] || 'default';
};

const OrdersPage = () => {
    const navigate = useNavigate();
    const [currentPage, setCurrentPage] = useState(1);
    const [expandedOrderDetails, setExpandedOrderDetails] = useState<
        Record<string, OrderDto>
    >({});
    const [loadingDetails, setLoadingDetails] = useState<Record<string, boolean>>(
        {},
    );
    const pageSize = 10;

    const {
        data: ordersPage,
        isLoading,
        isError,
    } = useQuery({
        queryKey: ['myOrders', currentPage],
        queryFn: () => getMyOrders(currentPage - 1, pageSize),
    });

    const loadOrderDetails = async (orderId: string) => {
        if (expandedOrderDetails[orderId]) return;
        setLoadingDetails((prev) => ({ ...prev, [orderId]: true }));
        try {
            const order = await getOrderById(orderId);
            setExpandedOrderDetails((prev) => ({ ...prev, [orderId]: order }));
        } finally {
            setLoadingDetails((prev) => ({ ...prev, [orderId]: false }));
        }
    };

    const columns: ColumnsType<OrderSummaryDto> = [
        {
            title: '№ заказа',
            dataIndex: 'orderNumber',
            key: 'orderNumber',
            render: (orderNumber: string) => <Text strong>{orderNumber}</Text>,
        },
        {
            title: 'Дата',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 180,
            render: (date: string) => formatDate(date),
        },
        {
            title: 'Статус',
            dataIndex: 'status',
            key: 'status',
            width: 200,
            render: (status: unknown) => {
                const code = extractEnumCode(status);
                return (
                    <Tag color={getStatusColor(code)}>
                        {OrderStatusLabels[code as OrderStatus] || extractEnumDisplayName(status, code)}
                    </Tag>
                );
            },
        },
        {
            title: 'Позиций',
            dataIndex: 'itemsCount',
            key: 'itemsCount',
            width: 100,
        },
        {
            title: 'Сумма',
            dataIndex: 'totalAmount',
            key: 'totalAmount',
            width: 150,
            render: (amount: number) => (
                <Text strong style={{ color: '#1677ff' }}>
                    {formatPrice(amount)}
                </Text>
            ),
        },
    ];

    const expandedRowRender = (record: OrderSummaryDto) => {
        const order = expandedOrderDetails[record.id];
        const loading = loadingDetails[record.id];

        if (loading) {
            return (
                <div style={{ textAlign: 'center', padding: 24 }}>
                    <Spin />
                </div>
            );
        }

        if (!order) return null;

        const itemColumns: ColumnsType<OrderItemDto> = [
            {
                title: 'Товар',
                dataIndex: 'productName',
                key: 'productName',
                render: (name: string, item) => (
                    <a onClick={() => navigate(`/products/${item.productId}`)}>
                        {name}
                    </a>
                ),
            },
            {
                title: 'Цена',
                dataIndex: 'price',
                key: 'price',
                width: 130,
                render: (price: number) => formatPrice(price),
            },
            {
                title: 'Кол-во',
                dataIndex: 'quantity',
                key: 'quantity',
                width: 100,
            },
            {
                title: 'Сумма',
                dataIndex: 'subtotal',
                key: 'subtotal',
                width: 130,
                render: (subtotal: number) => <Text strong>{formatPrice(subtotal)}</Text>,
            },
        ];

        const paymentCode = extractEnumCode(order.paymentMethod);
        const deliveryCode = extractEnumCode(order.deliveryMethod);

        return (
            <div style={{ padding: '0 16px' }}>
                <Table<OrderItemDto>
                    columns={itemColumns}
                    dataSource={order.items}
                    rowKey="productId"
                    pagination={false}
                    size="small"
                    style={{ marginBottom: 16 }}
                />

                <Descriptions size="small" column={2}>
                    <Descriptions.Item label="Способ оплаты">
                        {PaymentMethodLabels[paymentCode as keyof typeof PaymentMethodLabels] ||
                            extractEnumDisplayName(order.paymentMethod)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Способ доставки">
                        {DeliveryMethodLabels[deliveryCode as keyof typeof DeliveryMethodLabels] ||
                            extractEnumDisplayName(order.deliveryMethod)}
                    </Descriptions.Item>
                    {order.deliveryAddress && (
                        <>
                            <Descriptions.Item label="Получатель">
                                {order.deliveryAddress.recipientName}
                            </Descriptions.Item>
                            <Descriptions.Item label="Телефон">
                                {order.deliveryAddress.phone}
                            </Descriptions.Item>
                            <Descriptions.Item label="Адрес" span={2}>
                                {[
                                    order.deliveryAddress.postalCode,
                                    order.deliveryAddress.city,
                                    order.deliveryAddress.street,
                                    `д. ${order.deliveryAddress.building}`,
                                    order.deliveryAddress.apartment
                                        ? `кв. ${order.deliveryAddress.apartment}`
                                        : null,
                                ]
                                    .filter(Boolean)
                                    .join(', ')}
                            </Descriptions.Item>
                        </>
                    )}
                    {order.warehousePoint && (
                        <Descriptions.Item label="Точка самовывоза" span={2}>
                            {order.warehousePoint.name} — {order.warehousePoint.city},{' '}
                            {order.warehousePoint.street}, д. {order.warehousePoint.building}
                        </Descriptions.Item>
                    )}
                    {order.trackingNumber && (
                        <Descriptions.Item label="Трекинг" span={2}>
                            {order.trackingNumber}
                        </Descriptions.Item>
                    )}
                    {order.comment && (
                        <Descriptions.Item label="Комментарий" span={2}>
                            {order.comment}
                        </Descriptions.Item>
                    )}
                </Descriptions>
            </div>
        );
    };

    if (isLoading) {
        return (
            <div style={{ textAlign: 'center', padding: 120 }}>
                <Spin size="large" />
            </div>
        );
    }

    if (isError) {
        return (
            <Empty
                description="Не удалось загрузить заказы"
                style={{ padding: 120 }}
            />
        );
    }

    if (!ordersPage || ordersPage.empty) {
        return (
            <Empty
                image={<ShoppingOutlined style={{ fontSize: 80, color: '#d9d9d9' }} />}
                description="У вас пока нет заказов"
                style={{ padding: 120 }}
            >
                <Button type="primary" onClick={() => navigate('/')}>
                    Перейти в каталог
                </Button>
            </Empty>
        );
    }

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>
                Мои заказы
            </Title>

            <Card>
                <Table<OrderSummaryDto>
                    columns={columns}
                    dataSource={ordersPage.content}
                    rowKey="id"
                    pagination={false}
                    expandable={{
                        expandedRowRender,
                        expandRowByClick: true,
                        onExpand: (expanded, record) => {
                            if (expanded) {
                                loadOrderDetails(record.id);
                            }
                        },
                    }}
                />

                {ordersPage.totalPages > 1 && (
                    <div style={{ textAlign: 'center', marginTop: 24 }}>
                        <Pagination
                            current={currentPage}
                            total={ordersPage.totalElements}
                            pageSize={pageSize}
                            onChange={setCurrentPage}
                            showTotal={(total) => `Всего ${total} заказов`}
                        />
                    </div>
                )}
            </Card>
        </div>
    );
};

export default OrdersPage;