import { useState } from 'react';
import {
    Table,
    Tag,
    Typography,
    Select,
    Input,
    Button,
    Card,
    Row,
    Col,
    Descriptions,
    Spin,
    App,
} from 'antd';
import {
    SearchOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import apiClient from '@/api/client';
import {
    OrderStatus,
    OrderStatusLabels,
    PaymentMethodLabels,
    DeliveryMethodLabels,
} from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import type { OrderSummaryDto, OrderDto, OrderItemDto } from '@/types/order';
import type { Page } from '@/types/product';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

/** Форматирует цену */
const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
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

/** Получить все заказы (админ) */
const getAdminOrders = async (
    page: number,
    size: number,
    status?: OrderStatus,
): Promise<Page<OrderSummaryDto>> => {
    const params: Record<string, string | number> = {
        page,
        size,
        sort: 'createdAt,desc',
    };
    if (status) {
        params.status = status;
    }
    const { data } = await apiClient.get<Page<OrderSummaryDto>>('/v1/orders', {
        params,
    });
    return data;
};

/** Получить полный заказ */
const getOrderDetails = async (id: string): Promise<OrderDto> => {
    const { data } = await apiClient.get<OrderDto>(`/v1/orders/${id}`);
    return data;
};

const AdminOrdersPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();

    const [currentPage, setCurrentPage] = useState(1);
    const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>();
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedDetails, setExpandedDetails] = useState<Record<string, OrderDto>>({});
    const [loadingDetails, setLoadingDetails] = useState<Record<string, boolean>>({});
    const pageSize = 15;

    const {
        data: ordersPage,
        isLoading,
        refetch,
    } = useQuery({
        queryKey: ['adminOrders', { page: currentPage, status: statusFilter }],
        queryFn: () => getAdminOrders(currentPage - 1, pageSize, statusFilter),
    });

    const loadDetails = async (orderId: string) => {
        if (expandedDetails[orderId]) return;
        setLoadingDetails((prev) => ({ ...prev, [orderId]: true }));
        try {
            const order = await getOrderDetails(orderId);
            setExpandedDetails((prev) => ({ ...prev, [orderId]: order }));
        } catch {
            messageApi.error('Не удалось загрузить детали заказа');
        } finally {
            setLoadingDetails((prev) => ({ ...prev, [orderId]: false }));
        }
    };

    const filteredOrders = ordersPage?.content.filter((o) =>
        searchQuery
            ? o.orderNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
            o.customerEmail.toLowerCase().includes(searchQuery.toLowerCase())
            : true,
    );

    const statusOptions = Object.entries(OrderStatusLabels).map(
        ([key, label]) => ({ value: key, label }),
    );

    const columns: ColumnsType<OrderSummaryDto> = [
        {
            title: '№ заказа',
            dataIndex: 'orderNumber',
            key: 'orderNumber',
            width: 160,
            render: (num: string) => <Text strong>{num}</Text>,
        },
        {
            title: 'Клиент',
            dataIndex: 'customerEmail',
            key: 'customerEmail',
            ellipsis: true,
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
            width: 90,
        },
        {
            title: 'Сумма',
            dataIndex: 'totalAmount',
            key: 'totalAmount',
            width: 140,
            render: (amount: number) => (
                <Text strong style={{ color: '#1677ff' }}>
                    {formatPrice(amount)}
                </Text>
            ),
        },
        {
            title: 'Дата',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 150,
            render: (date: string) => formatDate(date),
        },
    ];

    const expandedRowRender = (record: OrderSummaryDto) => {
        const order = expandedDetails[record.id];
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
                    <a onClick={() => navigate(`/products/${item.productId}`)}>{name}</a>
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
                width: 80,
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
                    <Descriptions.Item label="Оплата">
                        {PaymentMethodLabels[paymentCode as keyof typeof PaymentMethodLabels] ||
                            extractEnumDisplayName(order.paymentMethod)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Доставка">
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
                        <Descriptions.Item label="Самовывоз" span={2}>
                            {order.warehousePoint.name} — {order.warehousePoint.city},{' '}
                            {order.warehousePoint.street}
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

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>
                Заказы
            </Title>

            <Card style={{ marginBottom: 16, borderRadius: 12 }}>
                <Row gutter={16} align="middle">
                    <Col xs={24} sm={8}>
                        <Input
                            placeholder="Поиск по номеру или email..."
                            prefix={<SearchOutlined />}
                            allowClear
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </Col>
                    <Col xs={24} sm={8}>
                        <Select
                            placeholder="Все статусы"
                            allowClear
                            style={{ width: '100%' }}
                            options={statusOptions}
                            value={statusFilter}
                            onChange={(val) => {
                                setStatusFilter(val);
                                setCurrentPage(1);
                            }}
                        />
                    </Col>
                    <Col xs={24} sm={8} style={{ textAlign: 'right' }}>
                        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
                            Обновить
                        </Button>
                    </Col>
                </Row>
            </Card>

            <Card style={{ borderRadius: 12 }}>
                <Table<OrderSummaryDto>
                    columns={columns}
                    dataSource={filteredOrders}
                    rowKey="id"
                    loading={isLoading}
                    expandable={{
                        expandedRowRender,
                        expandRowByClick: true,
                        onExpand: (expanded, record) => {
                            if (expanded) {
                                loadDetails(record.id);
                            }
                        },
                    }}
                    pagination={{
                        current: currentPage,
                        total: ordersPage?.totalElements || 0,
                        pageSize,
                        onChange: (page) => setCurrentPage(page),
                        showTotal: (total) => `Всего ${total} заказов`,
                        showSizeChanger: false,
                    }}
                    size="middle"
                    scroll={{ x: 900 }}
                />
            </Card>
        </div>
    );
};

export default AdminOrdersPage;