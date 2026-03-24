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
    App,
} from 'antd';
import {
    SearchOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import apiClient from '@/api/client';
import {
    OrderStatus,
    OrderStatusLabels,
    PaymentMethodLabels,
    DeliveryMethodLabels,
} from '@/types/order';
import type { OrderDto, OrderItemDto } from '@/types/order';
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
const getStatusColor = (status: OrderStatus): string => {
    const colors: Record<string, string> = {
        NEW: 'blue',
        AWAITING_CONFIRMATION: 'orange',
        CONFIRMED: 'cyan',
        IN_PROGRESS: 'processing',
        SHIPPED: 'purple',
        DELIVERED: 'green',
        CANCELLED: 'red',
    };
    return colors[status] || 'default';
};

/** Допустимые переходы статусов */
const statusTransitions: Record<string, OrderStatus[]> = {
    [OrderStatus.NEW]: [OrderStatus.CONFIRMED, OrderStatus.CANCELLED],
    [OrderStatus.AWAITING_CONFIRMATION]: [OrderStatus.CONFIRMED, OrderStatus.CANCELLED],
    [OrderStatus.CONFIRMED]: [OrderStatus.IN_PROGRESS, OrderStatus.CANCELLED],
    [OrderStatus.IN_PROGRESS]: [OrderStatus.SHIPPED, OrderStatus.CANCELLED],
    [OrderStatus.SHIPPED]: [OrderStatus.DELIVERED],
    [OrderStatus.DELIVERED]: [],
    [OrderStatus.CANCELLED]: [],
};

/** Получить все заказы (админ) */
const getAdminOrders = async (
    page: number,
    size: number,
    status?: OrderStatus,
): Promise<Page<OrderDto>> => {
    const params: Record<string, string | number> = {
        page,
        size,
        sort: 'createdAt,desc',
    };
    if (status) {
        params.status = status;
    }
    const { data } = await apiClient.get<Page<OrderDto>>('/v1/orders', { params });
    return data;
};

const AdminOrdersPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    const [currentPage, setCurrentPage] = useState(1);
    const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>();
    const [searchQuery, setSearchQuery] = useState('');
    const pageSize = 15;

    // Загрузка заказов
    const {
        data: ordersPage,
        isLoading,
        refetch,
    } = useQuery({
        queryKey: ['adminOrders', { page: currentPage, status: statusFilter }],
        queryFn: () => getAdminOrders(currentPage - 1, pageSize, statusFilter),
    });

    // Мутация: смена статуса заказа
    const changeStatusMutation = useMutation({
        mutationFn: async ({
                               orderId,
                               newStatus,
                           }: {
            orderId: number;
            newStatus: OrderStatus;
        }) => {
            await apiClient.patch(`/v1/orders/${orderId}/status`, null, {
                params: { status: newStatus },
            });
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminOrders'] });
            messageApi.success('Статус заказа обновлён');
        },
        onError: () => {
            messageApi.error('Ошибка при обновлении статуса');
        },
    });

    // Фильтрация по поиску на клиенте
    const filteredOrders = ordersPage?.content.filter((o) =>
        searchQuery
            ? o.orderNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
            o.customerEmail.toLowerCase().includes(searchQuery.toLowerCase())
            : true,
    );

    // Опции для фильтра статусов
    const statusOptions = Object.entries(OrderStatusLabels).map(
        ([key, label]) => ({
            value: key,
            label,
        }),
    );

    // Колонки таблицы
    const columns: ColumnsType<OrderDto> = [
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
            render: (status: OrderStatus, record) => {
                const allowedTransitions = statusTransitions[status] || [];
                if (allowedTransitions.length === 0) {
                    return (
                        <Tag color={getStatusColor(status)}>
                            {OrderStatusLabels[status]}
                        </Tag>
                    );
                }
                return (
                    <Select
                        value={status}
                        size="small"
                        style={{ width: '100%' }}
                        onChange={(newStatus) =>
                            changeStatusMutation.mutate({
                                orderId: record.id,
                                newStatus,
                            })
                        }
                        loading={changeStatusMutation.isPending}
                    >
                        <Select.Option value={status}>
                            <Tag color={getStatusColor(status)}>
                                {OrderStatusLabels[status]}
                            </Tag>
                        </Select.Option>
                        {allowedTransitions.map((s) => (
                            <Select.Option key={s} value={s}>
                                <Tag color={getStatusColor(s)}>{OrderStatusLabels[s]}</Tag>
                            </Select.Option>
                        ))}
                    </Select>
                );
            },
        },
        {
            title: 'Оплата',
            dataIndex: 'paymentMethod',
            key: 'paymentMethod',
            width: 160,
            render: (method: string) =>
                PaymentMethodLabels[method as keyof typeof PaymentMethodLabels] ||
                method,
        },
        {
            title: 'Доставка',
            dataIndex: 'deliveryMethod',
            key: 'deliveryMethod',
            width: 150,
            render: (method: string) =>
                DeliveryMethodLabels[method as keyof typeof DeliveryMethodLabels] ||
                method,
        },
        {
            title: 'Сумма',
            dataIndex: 'totalAmount',
            key: 'totalAmount',
            width: 140,
            sorter: (a, b) => a.totalAmount - b.totalAmount,
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

    // Раскрываемые строки — детали заказа
    const expandedRowRender = (order: OrderDto) => {
        const itemColumns: ColumnsType<OrderItemDto> = [
            {
                title: 'Товар',
                dataIndex: 'productName',
                key: 'productName',
                render: (name: string, record) => (
                    <a onClick={() => navigate(`/products/${record.productId}`)}>
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
                width: 80,
            },
            {
                title: 'Сумма',
                dataIndex: 'totalPrice',
                key: 'totalPrice',
                width: 130,
                render: (total: number) => <Text strong>{formatPrice(total)}</Text>,
            },
        ];

        return (
            <div style={{ padding: '0 16px' }}>
                <Table<OrderItemDto>
                    columns={itemColumns}
                    dataSource={order.items}
                    rowKey="id"
                    pagination={false}
                    size="small"
                    style={{ marginBottom: 16 }}
                />
                <Descriptions size="small" column={2}>
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

            {/* Фильтры */}
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

            {/* Таблица */}
            <Card style={{ borderRadius: 12 }}>
                <Table<OrderDto>
                    columns={columns}
                    dataSource={filteredOrders}
                    rowKey="id"
                    loading={isLoading}
                    expandable={{
                        expandedRowRender,
                        expandRowByClick: true,
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
                    scroll={{ x: 1000 }}
                />
            </Card>
        </div>
    );
};

export default AdminOrdersPage;