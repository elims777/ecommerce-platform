import { useState } from 'react';
import {
  Table, Typography, Input, Button, Card, Row, Col, Select, App,
} from 'antd';
import { SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getAllUsers, changeUserRole, changeUserStatus } from '@/api/adminUsers';
import type { AdminUserDto } from '@/api/adminUsers';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

const formatDate = (dateStr: string): string =>
  new Date(dateStr).toLocaleDateString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });

const AdminUsersPage = () => {
  const navigate = useNavigate();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();

  const [searchQuery, setSearchQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<boolean | undefined>();

  const { data: users = [], isLoading, refetch } = useQuery({
    queryKey: ['adminUsers'],
    queryFn: getAllUsers,
  });

  const roleMutation = useMutation({
    mutationFn: ({ id, role }: { id: number; role: string }) => changeUserRole(id, role),
    onSuccess: () => {
      messageApi.success('Роль обновлена');
      queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
    },
    onError: () => messageApi.error('Ошибка смены роли'),
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => changeUserStatus(id, active),
    onSuccess: () => {
      messageApi.success('Статус обновлён');
      queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
    },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  const filtered = users.filter((u) => {
    const matchSearch = searchQuery
      ? u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
        u.firstname.toLowerCase().includes(searchQuery.toLowerCase()) ||
        u.lastname.toLowerCase().includes(searchQuery.toLowerCase())
      : true;
    const matchRole = roleFilter
      ? u.roles.some((r) => r.name === roleFilter)
      : true;
    const matchStatus = statusFilter !== undefined
      ? u.active === statusFilter
      : true;
    return matchSearch && matchRole && matchStatus;
  });

  const columns: ColumnsType<AdminUserDto> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      render: (email: string, record) => (
        <a onClick={() => navigate(`/admin/users/${record.id}`)}>{email}</a>
      ),
    },
    {
      title: 'Имя',
      key: 'name',
      render: (_, record) =>
        [record.lastname, record.firstname, record.surname].filter(Boolean).join(' '),
    },
    {
      title: 'Роль',
      key: 'role',
      width: 150,
      render: (_, record) => {
        const currentRole = record.roles[0]?.name ?? 'USER';
        return (
          <Select
            size="small"
            style={{ width: 120 }}
            value={currentRole}
            options={[
              { value: 'USER', label: 'USER' },
              { value: 'ADMIN', label: 'ADMIN' },
            ]}
            onChange={(role) => roleMutation.mutate({ id: record.id, role })}
            onClick={(e) => e.stopPropagation()}
          />
        );
      },
    },
    {
      title: 'Статус',
      key: 'status',
      width: 150,
      render: (_, record) => (
        <Select
          size="small"
          style={{ width: 130 }}
          value={record.active}
          options={[
            { value: true, label: 'Активен' },
            { value: false, label: 'Заблокирован' },
          ]}
          onChange={(active) => statusMutation.mutate({ id: record.id, active })}
          onClick={(e) => e.stopPropagation()}
        />
      ),
    },
    {
      title: 'Регистрация',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 130,
      render: (date: string) => formatDate(date),
    },
    {
      title: '',
      key: 'actions',
      width: 50,
      render: (_, record) => (
        <Button
          type="text"
          icon={<EyeOutlined />}
          size="small"
          onClick={() => navigate(`/admin/users/${record.id}`)}
        />
      ),
    },
  ];

  return (
    <div>
      <Title level={2} style={{ marginBottom: 24 }}>Пользователи</Title>

      <Card style={{ marginBottom: 16, borderRadius: 12 }}>
        <Row gutter={[16, 8]} align="middle">
          <Col xs={24} sm={8}>
            <Input
              placeholder="Поиск по email или имени..."
              prefix={<SearchOutlined />}
              allowClear
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </Col>
          <Col xs={12} sm={5}>
            <Select
              placeholder="Все роли"
              allowClear
              style={{ width: '100%' }}
              options={[{ value: 'ADMIN', label: 'ADMIN' }, { value: 'USER', label: 'USER' }]}
              value={roleFilter}
              onChange={setRoleFilter}
            />
          </Col>
          <Col xs={12} sm={5}>
            <Select
              placeholder="Все статусы"
              allowClear
              style={{ width: '100%' }}
              options={[
                { value: true, label: 'Активен' },
                { value: false, label: 'Заблокирован' },
              ]}
              value={statusFilter}
              onChange={setStatusFilter}
            />
          </Col>
          <Col xs={24} sm={6} style={{ textAlign: 'right' }}>
            <Text type="secondary" style={{ marginRight: 12 }}>
              {filtered.length} из {users.length}
            </Text>
            <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
              Обновить
            </Button>
          </Col>
        </Row>
      </Card>

      <Card style={{ borderRadius: 12 }}>
        <Table<AdminUserDto>
          columns={columns}
          dataSource={filtered}
          rowKey="id"
          loading={isLoading}
          onRow={(record) => ({
            style: { cursor: 'pointer' },
            onClick: () => navigate(`/admin/users/${record.id}`),
          })}
          pagination={{ pageSize: 20, showTotal: (total) => `Всего ${total} пользователей` }}
          size="middle"
          scroll={{ x: 900 }}
        />
      </Card>
    </div>
  );
};

export default AdminUsersPage;
