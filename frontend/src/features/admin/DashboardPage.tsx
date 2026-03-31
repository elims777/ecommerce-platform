import {
    Row,
    Col,
    Card,
    Typography,
    Table,
    Tag,
    Statistic,
    Spin,
} from 'antd';
import {
    ShoppingCartOutlined,
    ShoppingOutlined,
    UserOutlined,
    DollarOutlined,
    SyncOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';
import apiClient from '@/api/client';
import { getProducts } from '@/api/products';
import { OrderStatusLabels } from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import type { OrderSummaryDto, OrderStatus } from '@/types/order';
import type { Page } from '@/types/product';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

interface UserRow {
    id: number;
    email: string;
    firstname: string;
    lastname: string;
}

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

const PIE_COLORS = [
    '#1677ff', '#52c41a', '#faad14', '#722ed1',
    '#13c2c2', '#eb2f96', '#fa8c16', '#a0d911',
];

const DashboardPage = () => {
    const navigate = useNavigate();

    // Загрузка последних заказов
    const { data: ordersPage, isLoading: ordersLoading } = useQuery({
        queryKey: ['dashboardOrders'],
        queryFn: async () => {
            const { data } = await apiClient.get<Page<OrderSummaryDto>>('/v1/orders', {
                params: { page: 0, size: 5, sort: 'createdAt,desc' },
            });
            return data;
        },
    });

    // Загрузка товаров (первая страница для подсчёта)
    const { data: productsPage, isLoading: productsLoading } = useQuery({
        queryKey: ['dashboardProducts'],
        queryFn: () => getProducts({ page: 0, size: 1 }),
    });

    // Загрузка пользователей
    const { data: users = [], isLoading: usersLoading } = useQuery({
        queryKey: ['dashboardUsers'],
        queryFn: async () => {
            const { data } = await apiClient.get<UserRow[]>('/v1/users/all');
            return data;
        },
    });

    const isLoading = ordersLoading || productsLoading || usersLoading;

    // Подсчёт статистики
    const totalOrders = ordersPage?.totalElements || 0;
    const totalProducts = productsPage?.totalElements || 0;
    const totalUsers = users.length;
    const totalRevenue = ordersPage?.content.reduce((sum, o) => sum + o.totalAmount, 0) || 0;

    // Подсчёт заказов по статусам для диаграммы
    const statusCounts: Record<string, number> = {};
    ordersPage?.content.forEach((order) => {
        const code = extractEnumCode(order.status);
        const label = OrderStatusLabels[code as OrderStatus] || extractEnumDisplayName(order.status, code);
        statusCounts[label] = (statusCounts[label] || 0) + 1;
    });
    const statusChartData = Object.entries(statusCounts).map(([name, value]) => ({
        name,
        value,
    }));

    // Карточки статистики
    const statsData = [
        {
            title: 'Заказы',
            value: totalOrders,
            icon: <ShoppingCartOutlined />,
            color: '#1677ff',
        },
        {
            title: 'Товары',
            value: totalProducts,
            icon: <ShoppingOutlined />,
            color: '#52c41a',
        },
        {
            title: 'Клиенты',
            value: totalUsers,
            icon: <UserOutlined />,
            color: '#722ed1',
        },
        {
            title: 'Сумма заказов (последние)',
            value: totalRevenue,
            prefix: '₽',
            icon: <DollarOutlined />,
            color: '#faad14',
        },
    ];

    // Колонки таблицы последних заказов
    const orderColumns: ColumnsType<OrderSummaryDto> = [
        {
            title: '№ заказа',
            dataIndex: 'orderNumber',
            key: 'orderNumber',
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
            title: 'Сумма',
            dataIndex: 'totalAmount',
            key: 'totalAmount',
            render: (amount: number) => (
                <Text strong>{formatPrice(amount)}</Text>
            ),
        },
        {
            title: 'Дата',
            dataIndex: 'createdAt',
            key: 'createdAt',
            render: (date: string) => formatDate(date),
        },
    ];

    if (isLoading) {
        return (
            <div style={{ textAlign: 'center', padding: 120 }}>
                <Spin size="large" />
            </div>
        );
    }

    return (
        <div>
            <div
                style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: 24,
                }}
            >
                <Title level={2} style={{ margin: 0 }}>
                    Дашборд
                </Title>
                <div>
                    <SyncOutlined style={{ color: '#52c41a', marginRight: 8 }} />
                    <Text type="secondary">Данные в реальном времени</Text>
                </div>
            </div>

            {/* Карточки статистики */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                {statsData.map((stat) => (
                    <Col xs={24} sm={12} lg={6} key={stat.title}>
                        <Card hoverable style={{ borderRadius: 12 }}>
                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'flex-start',
                                }}
                            >
                                <div>
                                    <Text type="secondary" style={{ fontSize: 14 }}>
                                        {stat.title}
                                    </Text>
                                    <div style={{ marginTop: 8 }}>
                                        <Statistic
                                            value={stat.value}
                                            prefix={stat.prefix}
                                            valueStyle={{ fontSize: 28, fontWeight: 700 }}
                                        />
                                    </div>
                                </div>
                                <div
                                    style={{
                                        width: 48,
                                        height: 48,
                                        borderRadius: 12,
                                        background: `${stat.color}15`,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: 22,
                                        color: stat.color,
                                    }}
                                >
                                    {stat.icon}
                                </div>
                            </div>
                        </Card>
                    </Col>
                ))}
            </Row>

            {/* Заказы и диаграмма */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col xs={24} lg={14}>
                    <Card
                        title="Последние заказы"
                        extra={
                            <a onClick={() => navigate('/admin/orders')}>Все заказы</a>
                        }
                        style={{ borderRadius: 12 }}
                    >
                        {ordersPage && ordersPage.content.length > 0 ? (
                            <Table<OrderSummaryDto>
                                columns={orderColumns}
                                dataSource={ordersPage.content}
                                rowKey="id"
                                pagination={false}
                                size="small"
                            />
                        ) : (
                            <Text type="secondary">Заказов пока нет</Text>
                        )}
                    </Card>
                </Col>
                <Col xs={24} lg={10}>
                    <Card title="Заказы по статусам" style={{ borderRadius: 12 }}>
                        {statusChartData.length > 0 ? (
                            <ResponsiveContainer width="100%" height={320}>
                                <PieChart>
                                    <Pie
                                        data={statusChartData}
                                        cx="50%"
                                        cy="50%"
                                        innerRadius={60}
                                        outerRadius={110}
                                        paddingAngle={3}
                                        dataKey="value"
                                        label={((props: Record<string, number | string>) =>
                                                `${props.name} ${(Number(props.percent) * 100).toFixed(0)}%`
                                        ) as never}
                                        labelLine={false}
                                    >
                                        {statusChartData.map((_, index) => (
                                            <Cell
                                                key={`cell-${index}`}
                                                fill={PIE_COLORS[index % PIE_COLORS.length]}
                                            />
                                        ))}
                                    </Pie>
                                    <Tooltip
                                        formatter={((value: number) => [`${value} заказов`, 'Количество']) as never}
                                    />
                                    <Legend />
                                </PieChart>
                            </ResponsiveContainer>
                        ) : (
                            <div style={{ textAlign: 'center', padding: 80 }}>
                                <Text type="secondary">Нет данных для диаграммы</Text>
                            </div>
                        )}
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default DashboardPage;