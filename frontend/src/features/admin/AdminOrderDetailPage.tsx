import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Descriptions, Tag, Table, Typography,
  Button, Spin, App, Select,
} from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { changeOrderStatus } from '@/api/adminOrders';
import { OrderStatus, OrderStatusLabels, PaymentMethodLabels, DeliveryMethodLabels } from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import { getAllowedTransitions } from '@/utils/orderTransitions';
import type { OrderDto, OrderItemDto } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(price);

const formatDate = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const getStatusColor = (s: string): string => ({
  CREATED: 'blue', PENDING_PAYMENT: 'orange', PAID: 'cyan',
  PAYMENT_FAILED: 'red', PROCESSING: 'processing', SHIPPED: 'purple',
  IN_TRANSIT: 'geekblue', DELIVERED: 'green', CANCELLED: 'default',
  REFUNDED: 'magenta', AWAITING_CONFIRMATION: 'gold',
} as Record<string, string>)[s] ?? 'default';

const AdminOrderDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();

  const { data: order, isLoading } = useQuery({
    queryKey: ['adminOrderDetail', id],
    queryFn: async () => {
      const { data } = await apiClient.get<OrderDto>(`/v1/orders/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const statusMutation = useMutation({
    mutationFn: (newStatus: string) => changeOrderStatus(id!, newStatus),
    onSuccess: () => {
      messageApi.success('Статус обновлён');
      queryClient.invalidateQueries({ queryKey: ['adminOrderDetail', id] });
      queryClient.invalidateQueries({ queryKey: ['adminOrders'] });
    },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  if (isLoading || !order) {
    return <div style={{ textAlign: 'center', padding: 120 }}><Spin size="large" /></div>;
  }

  const statusCode = extractEnumCode(order.status);
  const transitions = getAllowedTransitions(statusCode);
  const statusOptions = [
    { value: statusCode, label: OrderStatusLabels[statusCode as OrderStatus] ?? statusCode, disabled: true },
    ...transitions.map((s) => ({ value: s, label: OrderStatusLabels[s] ?? s })),
  ];

  const paymentCode = extractEnumCode(order.paymentMethod);
  const deliveryCode = extractEnumCode(order.deliveryMethod);

  const itemColumns: ColumnsType<OrderItemDto> = [
    { title: 'Товар', dataIndex: 'productName', key: 'productName' },
    { title: 'Кол-во', dataIndex: 'quantity', key: 'quantity', width: 80 },
    { title: 'Цена', dataIndex: 'price', key: 'price', width: 130, render: (p: number) => formatPrice(p) },
    {
      title: 'Сумма', key: 'subtotal', width: 130,
      render: (_: unknown, record: OrderItemDto) => (
        <Text strong>{formatPrice(record.price * record.quantity)}</Text>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/orders')}>
          Назад
        </Button>
        <Title level={3} style={{ margin: 0 }}>Заказ {order.orderNumber}</Title>
      </div>

      <Card style={{ borderRadius: 12, marginBottom: 16 }}>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="Статус">
            {transitions.length > 0 ? (
              <Select
                size="small"
                style={{ width: 200 }}
                value={statusCode}
                options={statusOptions}
                onChange={(s) => statusMutation.mutate(s)}
                loading={statusMutation.isPending}
              />
            ) : (
              <Tag color={getStatusColor(statusCode)}>
                {OrderStatusLabels[statusCode as OrderStatus] ?? statusCode}
              </Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label="Дата создания">{formatDate(order.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Клиент">{order.customerEmail}</Descriptions.Item>
          <Descriptions.Item label="Оплата">
            {PaymentMethodLabels[paymentCode as keyof typeof PaymentMethodLabels] ?? extractEnumDisplayName(order.paymentMethod, paymentCode)}
          </Descriptions.Item>
          <Descriptions.Item label="Доставка">
            {DeliveryMethodLabels[deliveryCode as keyof typeof DeliveryMethodLabels] ?? extractEnumDisplayName(order.deliveryMethod, deliveryCode)}
          </Descriptions.Item>
          {order.trackingNumber && (
            <Descriptions.Item label="Трекинг" span={2}>{order.trackingNumber}</Descriptions.Item>
          )}
          {order.comment && (
            <Descriptions.Item label="Комментарий" span={2}>{order.comment}</Descriptions.Item>
          )}
        </Descriptions>
      </Card>

      {order.deliveryAddress && (
        <Card title="Адрес доставки" style={{ borderRadius: 12, marginBottom: 16 }}>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="Получатель">{order.deliveryAddress.recipientName}</Descriptions.Item>
            <Descriptions.Item label="Телефон">{order.deliveryAddress.phone}</Descriptions.Item>
            <Descriptions.Item label="Адрес" span={2}>
              {[
                order.deliveryAddress.postalCode,
                order.deliveryAddress.city,
                order.deliveryAddress.street,
                `д. ${order.deliveryAddress.building}`,
                order.deliveryAddress.apartment ? `кв. ${order.deliveryAddress.apartment}` : null,
              ].filter(Boolean).join(', ')}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      <Card
        title={`Позиции заказа (${order.items?.length ?? 0})`}
        style={{ borderRadius: 12 }}
        extra={<Text strong>Итого: {formatPrice(order.totalAmount)}</Text>}
      >
        <Table<OrderItemDto>
          columns={itemColumns}
          dataSource={order.items ?? []}
          rowKey="productId"
          pagination={false}
          size="small"
        />
      </Card>
    </div>
  );
};

export default AdminOrderDetailPage;
