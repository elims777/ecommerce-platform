import { useState } from 'react';
import {
  Row, Col, Card, Typography, Table, Tag, Statistic, Spin, Segmented,
} from 'antd';
import {
  ShoppingCartOutlined, ShoppingOutlined, UserOutlined, DollarOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as ReTooltip,
  ResponsiveContainer, PieChart, Pie, Cell, Legend,
} from 'recharts';
import apiClient from '@/api/client';
import { getProducts } from '@/api/products';
import { getAdminOrders } from '@/api/adminOrders';
import { OrderStatusLabels } from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import { getDateRange, groupRevenueByDay } from '@/utils/dateRange';
import type { Period } from '@/utils/dateRange';
import type { OrderSummaryDto, OrderStatus } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

const REVENUE_STATUSES = ['PAID', 'COMPLETED', 'PARTIALLY_PAID'];

const PIE_COLORS = [
  '#1677ff', '#52c41a', '#faad14', '#722ed1',
  '#13c2c2', '#eb2f96', '#fa8c16', '#a0d911',
];

const formatPrice = (price: number): string =>
  new Intl.NumberFormat('ru-RU', {
    style: 'currency', currency: 'RUB',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  }).format(price);

const formatDate = (dateStr: string): string =>
  new Date(dateStr).toLocaleDateString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });

const getStatusColor = (status: string): string => ({
  CREATED: 'blue', PENDING_PAYMENT: 'orange', PAID: 'cyan',
  PAYMENT_FAILED: 'red', PROCESSING: 'processing', SHIPPED: 'purple',
  IN_TRANSIT: 'geekblue', DELIVERED: 'green', CANCELLED: 'default',
  REFUNDED: 'magenta', AWAITING_CONFIRMATION: 'gold',
} as Record<string, string>)[status] ?? 'default';

const PERIOD_OPTIONS = [
  { label: 'День', value: 'day' },
  { label: 'Неделя', value: 'week' },
  { label: 'Месяц', value: 'month' },
  { label: 'Год', value: 'year' },
];

interface UserRow { id: number; email: string; }

const DashboardPage = () => {
  const navigate = useNavigate();
  const [period, setPeriod] = useState<Period>('month');
  const dateRange = getDateRange(period);

  const { data: ordersPage, isLoading: ordersLoading } = useQuery({
    queryKey: ['dashboardOrders', period],
    queryFn: () => getAdminOrders({
      page: 0, size: 200,
      dateFrom: dateRange.dateFrom,
      dateTo: dateRange.dateTo,
    }),
  });

  const { data: recentOrders, isLoading: recentLoading } = useQuery({
    queryKey: ['dashboardRecentOrders'],
    queryFn: () => getAdminOrders({ page: 0, size: 5 }),
  });

  const { data: productsPage, isLoading: productsLoading } = useQuery({
    queryKey: ['dashboardProducts'],
    queryFn: () => getProducts({ page: 0, size: 1 }),
  });

  const { data: users = [], isLoading: usersLoading } = useQuery({
    queryKey: ['dashboardUsers'],
    queryFn: async () => {
      const { data } = await apiClient.get<UserRow[]>('/v1/users/all');
      return data;
    },
  });

  const isLoading = ordersLoading || productsLoading || usersLoading || recentLoading;

  const orders = ordersPage?.content ?? [];
  const totalOrders = ordersPage?.totalElements ?? 0;
  const totalProducts = productsPage?.totalElements ?? 0;
  const totalUsers = users.length;
  const totalRevenue = orders.reduce((sum, o) => {
    const code = extractEnumCode(o.status);
    return REVENUE_STATUSES.includes(code) ? sum + o.totalAmount : sum;
  }, 0);

  const revenueByDay = groupRevenueByDay(
    orders.map(o => ({
      createdAt: o.createdAt,
      totalAmount: o.totalAmount,
      status: o.status as string,
    })),
    REVENUE_STATUSES,
  );

  const statusCounts: Record<string, number> = {};
  orders.forEach(order => {
    const code = extractEnumCode(order.status);
    const label = OrderStatusLabels[code as OrderStatus] ?? extractEnumDisplayName(order.status, code);
    statusCounts[label] = (statusCounts[label] ?? 0) + 1;
  });
  const statusChartData = Object.entries(statusCounts).map(([name, value]) => ({ name, value }));

  const statsData = [
    { title: 'Заказов за период', value: totalOrders, icon: <ShoppingCartOutlined />, color: '#1677ff' },
    { title: 'Выручка за период', value: totalRevenue, formatter: formatPrice, icon: <DollarOutlined />, color: '#52c41a' },
    { title: 'Товаров в каталоге', value: totalProducts, icon: <ShoppingOutlined />, color: '#722ed1' },
    { title: 'Клиентов', value: totalUsers, icon: <UserOutlined />, color: '#faad14' },
  ];

  const orderColumns: ColumnsType<OrderSummaryDto> = [
    {
      title: '№',
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
            {OrderStatusLabels[code as OrderStatus] ?? extractEnumDisplayName(status, code)}
          </Tag>
        );
      },
    },
    {
      title: 'Сумма',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (amount: number) => <Text strong>{formatPrice(amount)}</Text>,
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={2} style={{ margin: 0 }}>Дашборд</Title>
        <Segmented
          options={PERIOD_OPTIONS}
          value={period}
          onChange={(val) => setPeriod(val as Period)}
        />
      </div>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        {statsData.map((stat) => (
          <Col xs={24} sm={12} lg={6} key={stat.title}>
            <Card hoverable style={{ borderRadius: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 14 }}>{stat.title}</Text>
                  <div style={{ marginTop: 8 }}>
                    {stat.formatter ? (
                      <div style={{ fontSize: 28, fontWeight: 700 }}>{stat.formatter(stat.value)}</div>
                    ) : (
                      <Statistic value={stat.value} valueStyle={{ fontSize: 28, fontWeight: 700 }} />
                    )}
                  </div>
                </div>
                <div style={{
                  width: 48, height: 48, borderRadius: 12,
                  background: `${stat.color}15`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 22, color: stat.color,
                }}>
                  {stat.icon}
                </div>
              </div>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} lg={16}>
          <Card title="Выручка по дням" style={{ borderRadius: 12 }}>
            {revenueByDay.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <LineChart data={revenueByDay} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="date" tick={{ fontSize: 12 }} />
                  <YAxis tickFormatter={(v) => `${(v / 1000).toFixed(0)}к`} tick={{ fontSize: 12 }} />
                  <ReTooltip formatter={(value: number) => [formatPrice(value), 'Выручка']} />
                  <Line
                    type="monotone" dataKey="revenue"
                    stroke="#1677ff" strokeWidth={2}
                    dot={{ r: 3 }} activeDot={{ r: 5 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div style={{ textAlign: 'center', padding: 80 }}>
                <Text type="secondary">Нет данных за выбранный период</Text>
              </div>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Заказы по статусам" style={{ borderRadius: 12 }}>
            {statusChartData.length > 0 ? (
              <ResponsiveContainer width="100%" height={260}>
                <PieChart>
                  <Pie
                    data={statusChartData} cx="50%" cy="50%"
                    innerRadius={50} outerRadius={90}
                    paddingAngle={3} dataKey="value"
                  >
                    {statusChartData.map((_, index) => (
                      <Cell key={`cell-${index}`} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                    ))}
                  </Pie>
                  <ReTooltip formatter={(value: number) => [`${value} заказов`, 'Количество']} />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div style={{ textAlign: 'center', padding: 60 }}>
                <Text type="secondary">Нет данных</Text>
              </div>
            )}
          </Card>
        </Col>
      </Row>

      <Card
        title="Последние заказы"
        extra={<a onClick={() => navigate('/admin/orders')}>Все заказы</a>}
        style={{ borderRadius: 12 }}
      >
        {recentOrders && recentOrders.content.length > 0 ? (
          <Table<OrderSummaryDto>
            columns={orderColumns}
            dataSource={recentOrders.content}
            rowKey="id"
            pagination={false}
            size="small"
          />
        ) : (
          <Text type="secondary">Заказов пока нет</Text>
        )}
      </Card>
    </div>
  );
};

export default DashboardPage;
