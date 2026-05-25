import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Descriptions, Tag, Table, Typography, Button,
  Spin, App, Form, Input, Modal, Select, Popconfirm,
} from 'antd';
import {
  ArrowLeftOutlined, EditOutlined, CheckOutlined, DisconnectOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getUserById, changeUserRole, changeUserStatus, updateUserAdmin,
  getUserLegalEntities, verifyLegalEntity, detachLegalEntity,
} from '@/api/adminUsers';
import type { UpdateUserAdminRequest, LegalEntityDto } from '@/api/adminUsers';
import { getAdminOrders } from '@/api/adminOrders';
import { OrderStatusLabels } from '@/types/order';
import { extractEnumCode } from '@/utils/enumUtils';
import { useAuthStore } from '@/store/authStore';
import type { OrderSummaryDto, OrderStatus } from '@/types/order';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(price);

const formatDate = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });

const formatDateTime = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });

const STATUS_COLORS: Record<string, string> = {
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
  INVOICE_SENT: 'volcano',
  PARTIALLY_PAID: 'lime',
  COMPLETED: 'green',
};

const AdminUserDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();
  const userId = Number(id);
  const adminEmail = useAuthStore((s) => s.user?.email ?? '');

  const [editModalOpen, setEditModalOpen] = useState(false);
  const [form] = Form.useForm<UpdateUserAdminRequest>();

  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ['adminUser', userId],
    queryFn: () => getUserById(userId),
    enabled: !!userId,
  });

  const { data: ordersPage, isLoading: ordersLoading } = useQuery({
    queryKey: ['adminUserOrders', userId],
    queryFn: () => getAdminOrders({ userId, page: 0, size: 10 }),
    enabled: !!userId,
  });

  const { data: legalEntities = [], isLoading: leLoading } = useQuery({
    queryKey: ['adminUserLegalEntities', userId],
    queryFn: () => getUserLegalEntities(userId),
    enabled: !!userId,
  });

  const invalidateUser = () => {
    queryClient.invalidateQueries({ queryKey: ['adminUser', userId] });
    queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
  };

  const roleMutation = useMutation({
    mutationFn: (role: string) => changeUserRole(userId, role),
    onSuccess: () => { messageApi.success('Роль обновлена'); invalidateUser(); },
    onError: () => messageApi.error('Ошибка смены роли'),
  });

  const statusMutation = useMutation({
    mutationFn: (active: boolean) => changeUserStatus(userId, active),
    onSuccess: () => { messageApi.success('Статус обновлён'); invalidateUser(); },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  const editMutation = useMutation({
    mutationFn: (body: UpdateUserAdminRequest) => updateUserAdmin(userId, body),
    onSuccess: () => {
      messageApi.success('Данные обновлены');
      setEditModalOpen(false);
      invalidateUser();
    },
    onError: () => messageApi.error('Ошибка обновления данных'),
  });

  const verifyMutation = useMutation({
    mutationFn: (legalEntityId: number) => verifyLegalEntity(legalEntityId, adminEmail),
    onSuccess: () => {
      messageApi.success('Организация верифицирована');
      queryClient.invalidateQueries({ queryKey: ['adminUserLegalEntities', userId] });
    },
    onError: () => messageApi.error('Ошибка верификации'),
  });

  const detachMutation = useMutation({
    mutationFn: (legalEntityId: number) => detachLegalEntity(legalEntityId, userId),
    onSuccess: () => {
      messageApi.success('Организация отвязана');
      queryClient.invalidateQueries({ queryKey: ['adminUserLegalEntities', userId] });
    },
    onError: () => messageApi.error('Ошибка отвязки организации'),
  });

  const orderColumns: ColumnsType<OrderSummaryDto> = [
    {
      title: '№',
      dataIndex: 'orderNumber',
      key: 'orderNumber',
      render: (num: string, record) => (
        <a onClick={() => navigate(`/admin/orders/${record.id}`)}>{num}</a>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      render: (status: unknown) => {
        const code = extractEnumCode(status);
        return (
          <Tag color={STATUS_COLORS[code] ?? 'default'}>
            {OrderStatusLabels[code as OrderStatus] ?? code}
          </Tag>
        );
      },
    },
    {
      title: 'Сумма',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (amount: number) => formatPrice(amount),
    },
    {
      title: 'Дата',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: string) => formatDateTime(d),
    },
  ];

  const leColumns: ColumnsType<LegalEntityDto> = [
    {
      title: 'Компания',
      dataIndex: 'fullName',
      key: 'fullName',
    },
    {
      title: 'ИНН',
      dataIndex: 'inn',
      key: 'inn',
      width: 120,
    },
    {
      title: 'Статус',
      dataIndex: 'verificationStatus',
      key: 'verificationStatus',
      width: 140,
      render: (s: string) => {
        const colorMap: Record<string, string> = {
          VERIFIED: 'green',
          REJECTED: 'red',
          PENDING: 'orange',
        };
        const labelMap: Record<string, string> = {
          VERIFIED: 'Верифицирована',
          REJECTED: 'Отклонена',
          PENDING: 'На проверке',
        };
        return (
          <Tag color={colorMap[s] ?? 'default'}>
            {labelMap[s] ?? s}
          </Tag>
        );
      },
    },
    {
      title: 'Добавлена',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 110,
      render: (d: string) => formatDate(d),
    },
    {
      title: '',
      key: 'actions',
      width: 80,
      render: (_, record) => (
        <div style={{ display: 'flex', gap: 4 }}>
          {record.verificationStatus !== 'VERIFIED' && (
            <Popconfirm
              title="Верифицировать организацию?"
              onConfirm={() => verifyMutation.mutate(record.id)}
              okText="Да"
              cancelText="Нет"
            >
              <Button
                type="text"
                icon={<CheckOutlined />}
                size="small"
                style={{ color: '#52c41a' }}
              />
            </Popconfirm>
          )}
          <Popconfirm
            title="Отвязать организацию от пользователя?"
            onConfirm={() => detachMutation.mutate(record.id)}
            okText="Да"
            cancelText="Нет"
          >
            <Button type="text" icon={<DisconnectOutlined />} size="small" danger />
          </Popconfirm>
        </div>
      ),
    },
  ];

  if (userLoading || !user) {
    return (
      <div style={{ textAlign: 'center', padding: 120 }}>
        <Spin size="large" />
      </div>
    );
  }

  const currentRole = user.roles[0]?.name ?? 'USER';

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/users')}>
          Назад
        </Button>
        <Title level={3} style={{ margin: 0 }}>
          {user.lastname} {user.firstname}
        </Title>
      </div>

      <Card
        title="Личные данные"
        style={{ borderRadius: 12, marginBottom: 16 }}
        extra={
          <Button
            icon={<EditOutlined />}
            size="small"
            onClick={() => {
              form.setFieldsValue({
                firstname: user.firstname,
                lastname: user.lastname,
                phone: user.phone ?? '',
              });
              setEditModalOpen(true);
            }}
          >
            Редактировать
          </Button>
        }
      >
        <Descriptions column={2} size="small">
          <Descriptions.Item label="Email">
            <Text copyable>{user.email}</Text>
          </Descriptions.Item>
          <Descriptions.Item label="Телефон">{user.phone ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Имя">{user.firstname}</Descriptions.Item>
          <Descriptions.Item label="Фамилия">{user.lastname}</Descriptions.Item>
          <Descriptions.Item label="Дата регистрации">{formatDate(user.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Email подтверждён">
            <Tag color={user.emailVerified ? 'green' : 'orange'}>
              {user.emailVerified ? 'Да' : 'Нет'}
            </Tag>
          </Descriptions.Item>
          <Descriptions.Item label="Роль">
            <Select
              size="small"
              style={{ width: 120 }}
              value={currentRole}
              options={[
                { value: 'USER', label: 'USER' },
                { value: 'ADMIN', label: 'ADMIN' },
              ]}
              onChange={(role) => roleMutation.mutate(role)}
              loading={roleMutation.isPending}
            />
          </Descriptions.Item>
          <Descriptions.Item label="Статус">
            <Select
              size="small"
              style={{ width: 140 }}
              value={user.active}
              options={[
                { value: true, label: 'Активен' },
                { value: false, label: 'Заблокирован' },
              ]}
              onChange={(active) => statusMutation.mutate(active)}
              loading={statusMutation.isPending}
            />
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Заказы" style={{ borderRadius: 12, marginBottom: 16 }}>
        <Table<OrderSummaryDto>
          columns={orderColumns}
          dataSource={ordersPage?.content ?? []}
          rowKey="id"
          loading={ordersLoading}
          pagination={false}
          size="small"
          locale={{ emptyText: 'Заказов нет' }}
        />
      </Card>

      <Card title="Юридические лица" style={{ borderRadius: 12 }}>
        <Table<LegalEntityDto>
          columns={leColumns}
          dataSource={legalEntities}
          rowKey="id"
          loading={leLoading}
          pagination={false}
          size="small"
          locale={{ emptyText: 'Нет привязанных организаций' }}
        />
      </Card>

      <Modal
        title="Редактировать данные"
        open={editModalOpen}
        onCancel={() => setEditModalOpen(false)}
        onOk={() => form.submit()}
        confirmLoading={editMutation.isPending}
        okText="Сохранить"
        cancelText="Отмена"
      >
        <Form<UpdateUserAdminRequest>
          form={form}
          layout="vertical"
          onFinish={(values) => editMutation.mutate(values)}
          style={{ marginTop: 16 }}
        >
          <Form.Item name="lastname" label="Фамилия" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="firstname" label="Имя" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Телефон">
            <Input placeholder="+7 900 000 00 00" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AdminUserDetailPage;
