import {
    Row,
    Col,
    Card,
    Typography,
    Table,
    Tag,
    Statistic,
    Space,
} from 'antd';
import {
    ShoppingCartOutlined,
    ShoppingOutlined,
    UserOutlined,
    DollarOutlined,
    ArrowUpOutlined,
    ArrowDownOutlined,
    SyncOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { OrderStatusLabels } from '@/types/order';
import type { OrderStatus } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts';

const { Title, Text } = Typography;

// ============================================================
// MOCK DATA — заменить на реальные API когда появится stats-service
// TODO: GET /api/v1/stats/dashboard
// ============================================================

interface StatCard {
    title: string;
    value: number;
    prefix?: string;
    suffix?: string;
    change: number; // процент изменения за период
    icon: React.ReactNode;
    color: string;
}

const statsData: StatCard[] = [
    {
        title: 'Выручка',
        value: 1245800,
        prefix: '₽',
        change: 12.5,
        icon: <DollarOutlined />,
        color: '#1677ff',
    },
    {
        title: 'Заказы',
        value: 156,
        change: 8.2,
        icon: <ShoppingCartOutlined />,
        color: '#52c41a',
    },
    {
        title: 'Товары',
        value: 432,
        change: -2.1,
        icon: <ShoppingOutlined />,
        color: '#faad14',
    },
    {
        title: 'Клиенты',
        value: 89,
        change: 15.3,
        icon: <UserOutlined />,
        color: '#722ed1',
    },
];

interface RecentOrder {
    id: number;
    orderNumber: string;
    customerEmail: string;
    status: OrderStatus;
    totalAmount: number;
    createdAt: string;
}

const recentOrders: RecentOrder[] = [
    {
        id: 1,
        orderNumber: 'ORD-2026-0156',
        customerEmail: 'ivanov@company.ru',
        status: 'NEW' as OrderStatus,
        totalAmount: 45600,
        createdAt: '2026-03-24T10:30:00',
    },
    {
        id: 2,
        orderNumber: 'ORD-2026-0155',
        customerEmail: 'petrov@mail.ru',
        status: 'CONFIRMED' as OrderStatus,
        totalAmount: 123400,
        createdAt: '2026-03-24T09:15:00',
    },
    {
        id: 3,
        orderNumber: 'ORD-2026-0154',
        customerEmail: 'sidorov@corp.ru',
        status: 'SHIPPED' as OrderStatus,
        totalAmount: 78900,
        createdAt: '2026-03-23T16:45:00',
    },
    {
        id: 4,
        orderNumber: 'ORD-2026-0153',
        customerEmail: 'kozlov@factory.ru',
        status: 'DELIVERED' as OrderStatus,
        totalAmount: 234500,
        createdAt: '2026-03-23T14:20:00',
    },
    {
        id: 5,
        orderNumber: 'ORD-2026-0152',
        customerEmail: 'novikov@trade.ru',
        status: 'IN_PROGRESS' as OrderStatus,
        totalAmount: 56700,
        createdAt: '2026-03-23T11:00:00',
    },
];

interface TopProduct {
    id: number;
    name: string;
    category: string;
    sold: number;
    revenue: number;
}

const topProducts: TopProduct[] = [
    { id: 1, name: 'Респиратор РУССИЗ 122 FFP 2', category: 'СИЗ', sold: 1240, revenue: 99200 },
    { id: 2, name: 'Нитриловые перчатки mediOk', category: 'Медицинские товары', sold: 8500, revenue: 50150 },
    { id: 3, name: 'Огнетушитель ОП-4(з) АВСЕ', category: 'Противопожарное', sold: 320, revenue: 297600 },
    { id: 4, name: 'Алмадез-хлор', category: 'Дерматологические СИЗ', sold: 2100, revenue: 1050000 },
    { id: 5, name: 'Самоспасатель UFMS «Шанс»', category: 'ГО и ЧС', sold: 180, revenue: 644400 },
];

interface CategoryStat {
    name: string;
    value: number;
}

const categoryStats: CategoryStat[] = [
    { name: 'СИЗ', value: 340 },
    { name: 'Противопожарное', value: 215 },
    { name: 'Медицинские товары', value: 180 },
    { name: 'Спецодежда', value: 156 },
    { name: 'Дерматологические СИЗ', value: 120 },
    { name: 'ГО и ЧС', value: 95 },
    { name: 'Бытовая химия', value: 78 },
    { name: 'Прочее', value: 62 },
];

const PIE_COLORS = [
    '#1677ff', '#52c41a', '#faad14', '#722ed1',
    '#13c2c2', '#eb2f96', '#fa8c16', '#a0d911',
];
// ============================================================

/** Форматирует число с разделителями тысяч */
const formatNumber = (value: number): string =>
    new Intl.NumberFormat('ru-RU').format(value);

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

const DashboardPage = () => {
    const navigate = useNavigate();

    // Колонки таблицы последних заказов
    const orderColumns: ColumnsType<RecentOrder> = [
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
        },
        {
            title: 'Статус',
            dataIndex: 'status',
            key: 'status',
            render: (status: OrderStatus) => (
                <Tag color={getStatusColor(status)}>
                    {OrderStatusLabels[status] || status}
                </Tag>
            ),
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

    // Колонки таблицы топ-товаров
    const productColumns: ColumnsType<TopProduct> = [
        {
            title: 'Товар',
            dataIndex: 'name',
            key: 'name',
            render: (name: string) => (
                <Text ellipsis={{ tooltip: name }}>{name}</Text>
            ),
        },
        {
            title: 'Категория',
            dataIndex: 'category',
            key: 'category',
            render: (cat: string) => <Tag>{cat}</Tag>,
        },
        {
            title: 'Продано',
            dataIndex: 'sold',
            key: 'sold',
            width: 100,
            render: (sold: number) => formatNumber(sold),
        },
        {
            title: 'Выручка',
            dataIndex: 'revenue',
            key: 'revenue',
            width: 140,
            render: (rev: number) => (
                <Text strong>{formatPrice(rev)}</Text>
            ),
        },
    ];

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
                <Space>
                    <SyncOutlined style={{ color: '#52c41a' }} />
                    <Text type="secondary">Данные за март 2026</Text>
                </Space>
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
                                            suffix={stat.suffix}
                                            valueStyle={{ fontSize: 28, fontWeight: 700 }}
                                        />
                                    </div>
                                    <div style={{ marginTop: 8 }}>
                                        {stat.change >= 0 ? (
                                            <Text style={{ color: '#52c41a', fontSize: 13 }}>
                                                <ArrowUpOutlined /> +{stat.change}%
                                            </Text>
                                        ) : (
                                            <Text style={{ color: '#ff4d4f', fontSize: 13 }}>
                                                <ArrowDownOutlined /> {stat.change}%
                                            </Text>
                                        )}
                                        <Text
                                            type="secondary"
                                            style={{ fontSize: 12, marginLeft: 8 }}
                                        >
                                            vs прошлый месяц
                                        </Text>
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

            {/* Таблицы и диаграмма */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
                <Col xs={24} lg={14}>
                    <Card
                        title="Последние заказы"
                        extra={<a onClick={() => navigate('/admin/orders')}>Все заказы</a>}
                        style={{ borderRadius: 12 }}
                    >
                        <Table<RecentOrder>
                            columns={orderColumns}
                            dataSource={recentOrders}
                            rowKey="id"
                            pagination={false}
                            size="small"
                        />
                    </Card>
                </Col>
                <Col xs={24} lg={10}>
                    <Card title="Заказы по категориям" style={{ borderRadius: 12 }}>
                        <ResponsiveContainer width="100%" height={320}>
                            <PieChart>
                                <Pie
                                    data={categoryStats}
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
                                    {categoryStats.map((_, index) => (
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
                    </Card>
                </Col>
            </Row>

            {/* Топ товаров */}
            <Row gutter={[16, 16]}>
                <Col xs={24}>
                    <Card
                        title="Топ товаров"
                        extra={<a onClick={() => navigate('/admin/products')}>Все товары</a>}
                        style={{ borderRadius: 12 }}
                    >
                        <Table<TopProduct>
                            columns={productColumns}
                            dataSource={topProducts}
                            rowKey="id"
                            pagination={false}
                            size="small"
                        />
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default DashboardPage;