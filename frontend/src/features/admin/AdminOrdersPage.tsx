import { useState } from 'react';
import {
  Table, Tag, Typography, Select, Input, Button,
  Card, Row, Col, App, DatePicker, Space,
} from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getAdminOrders, changeOrderStatus } from '@/api/adminOrders';
import { OrderStatus, OrderStatusLabels } from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import { getAllowedTransitions } from '@/utils/orderTransitions';
import type { OrderSummaryDto } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

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

const AdminOrdersPage = () => {
  const navigate = useNavigate();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();

  const [currentPage, setCurrentPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>();
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState<[string, string] | null>(null);
  const pageSize = 15;

  const queryParams = {
    page: currentPage - 1,
    size: pageSize,
    status: statusFilter,
    dateFrom: dateRange?.[0],
    dateTo: dateRange?.[1],
  };

  const { data: ordersPage, isLoading, refetch } = useQuery({
    queryKey: ['adminOrders', queryParams],
    queryFn: () => getAdminOrders(queryParams),
  });

  const statusMutation = useMutation({
    mutationFn: ({ orderId, status }: { orderId: string; status: string }) =>
      changeOrderStatus(orderId, status),
    onSuccess: () => {
      messageApi.success('Статус обновлён');
      queryClient.invalidateQueries({ queryKey: ['adminOrders'] });
    },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  const filteredOrders = ordersPage?.content.filter((o) =>
    searchQuery
      ? o.orderNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
        o.customerEmail.toLowerCase().includes(searchQuery.toLowerCase())
      : true,
  );

  const statusOptions = Object.entries(OrderStatusLabels).map(([key, label]) => ({
    value: key, label,
  }));

  const columns: ColumnsType<OrderSummaryDto> = [
    {
      title: '№ заказа',
      dataIndex: 'orderNumber',
      key: 'orderNumber',
      width: 160,
      render: (num: string, record) => (
        <a onClick={() => navigate(`/admin/orders/${record.id}`)}>{num}</a>
      ),
    },
    {
      title: 'Клиент',
      dataIndex: 'customerEmail',
      key: 'customerEmail',
      ellipsis: true,
    },
    {
      title: 'Тип',
      dataIndex: 'customerType',
      key: 'customerType',
      width: 70,
      render: (ct: unknown) => {
        const code = extractEnumCode(ct);
        return (
          <Tag color={code === 'B2B' ? 'purple' : 'blue'} style={{ fontSize: 11 }}>
            {code || '—'}
          </Tag>
        );
      },
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      width: 220,
      render: (status: unknown, record) => {
        const code = extractEnumCode(status);
        const transitions = getAllowedTransitions(code);
        if (transitions.length === 0) {
          return (
            <Tag color={getStatusColor(code)}>
              {OrderStatusLabels[code as OrderStatus] ?? extractEnumDisplayName(status, code)}
            </Tag>
          );
        }
        const options = [
          { value: code, label: OrderStatusLabels[code as OrderStatus] ?? code, disabled: true },
          ...transitions.map((s) => ({ value: s, label: OrderStatusLabels[s] ?? s })),
        ];
        return (
          <Select
            size="small"
            style={{ width: 200 }}
            value={code}
            options={options}
            onChange={(newStatus) => statusMutation.mutate({ orderId: record.id, status: newStatus })}
            onClick={(e) => e.stopPropagation()}
          />
        );
      },
    },
    {
      title: 'Сумма',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      width: 140,
      render: (amount: number) => (
        <Text strong style={{ color: '#1677ff' }}>{formatPrice(amount)}</Text>
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

  return (
    <div>
      <Title level={2} style={{ marginBottom: 24 }}>Заказы</Title>

      <Card style={{ marginBottom: 16, borderRadius: 12 }}>
        <Row gutter={[16, 8]} align="middle">
          <Col xs={24} sm={8}>
            <Input
              placeholder="Поиск по номеру или email..."
              prefix={<SearchOutlined />}
              allowClear
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </Col>
          <Col xs={24} sm={6}>
            <Select
              placeholder="Все статусы"
              allowClear
              style={{ width: '100%' }}
              options={statusOptions}
              value={statusFilter}
              onChange={(val) => { setStatusFilter(val); setCurrentPage(1); }}
            />
          </Col>
          <Col xs={24} sm={7}>
            <Space>
              <RangePicker
                onChange={(dates) => {
                  if (dates && dates[0] && dates[1]) {
                    setDateRange([
                      dates[0].startOf('day').toISOString(),
                      dates[1].endOf('day').toISOString(),
                    ]);
                    setCurrentPage(1);
                  } else {
                    setDateRange(null);
                  }
                }}
              />
            </Space>
          </Col>
          <Col xs={24} sm={3} style={{ textAlign: 'right' }}>
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
          onRow={(record) => ({
            style: { cursor: 'pointer' },
            onClick: () => navigate(`/admin/orders/${record.id}`),
          })}
          pagination={{
            current: currentPage,
            total: ordersPage?.totalElements ?? 0,
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
