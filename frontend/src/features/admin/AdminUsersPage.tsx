import { useState } from 'react';
import {
    Table,
    Tag,
    Typography,
    Input,
    Button,
    Card,
    Row,
    Col,
} from 'antd';
import {
    SearchOutlined,
    ReloadOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import type { ColumnsType } from 'antd/es/table';


const { Title, Text } = Typography;

interface UserRow {
    id: number;
    email: string;
    firstname: string;
    lastname: string;
    surname: string | null;
    emailVerified: boolean;
    roles: { name: string }[];
    createdAt: string;
    updatedAt: string;
}

/** Форматирует дату */
const formatDate = (dateStr: string): string =>
    new Date(dateStr).toLocaleDateString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });

/** Получить пользователей (админ) */
const getUsers = async (): Promise<UserRow[]> => {
    const { data } = await apiClient.get<UserRow[]>('/v1/users/all');
    return data;
};

const AdminUsersPage = () => {
    const [searchQuery, setSearchQuery] = useState('');

    const {
        data: users = [],
        isLoading,
        refetch,
    } = useQuery({
        queryKey: ['adminUsers'],
        queryFn: getUsers,
    });

    // Фильтрация по поиску на клиенте
    const filteredUsers = users.filter((u) =>
        searchQuery
            ? u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
            u.firstname.toLowerCase().includes(searchQuery.toLowerCase()) ||
            u.lastname.toLowerCase().includes(searchQuery.toLowerCase())
            : true,
    );

    const columns: ColumnsType<UserRow> = [
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
            render: (email: string) => <Text strong>{email}</Text>,
        },
        {
            title: 'Имя',
            key: 'name',
            render: (_, record) =>
                [record.lastname, record.firstname, record.surname]
                    .filter(Boolean)
                    .join(' '),
        },
        {
            title: 'Email подтверждён',
            dataIndex: 'emailVerified',
            key: 'emailVerified',
            width: 150,
            render: (verified: boolean) =>
                verified ? (
                    <Tag icon={<CheckCircleOutlined />} color="green">
                        Да
                    </Tag>
                ) : (
                    <Tag icon={<CloseCircleOutlined />} color="orange">
                        Нет
                    </Tag>
                ),
        },
        {
            title: 'Роли',
            dataIndex: 'roles',
            key: 'roles',
            width: 180,
            render: (roles: { name: string }[]) =>
                roles.map((r) => (
                    <Tag key={r.name} color={r.name === 'ADMIN' ? 'red' : 'blue'}>
                        {r.name}
                    </Tag>
                )),
        },
        {
            title: 'Дата регистрации',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 160,
            render: (date: string) => formatDate(date),
        },
    ];

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>
                Пользователи
            </Title>

            <Card style={{ marginBottom: 16, borderRadius: 12 }}>
                <Row gutter={16} align="middle">
                    <Col xs={24} sm={12}>
                        <Input
                            placeholder="Поиск по email или имени..."
                            prefix={<SearchOutlined />}
                            allowClear
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </Col>
                    <Col xs={24} sm={12} style={{ textAlign: 'right' }}>
                        <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
                            Обновить
                        </Button>
                    </Col>
                </Row>
            </Card>

            <Card style={{ borderRadius: 12 }}>
                <Table<UserRow>
                    columns={columns}
                    dataSource={filteredUsers}
                    rowKey="id"
                    loading={isLoading}
                    pagination={{
                        pageSize: 20,
                        showTotal: (total) => `Всего ${total} пользователей`,
                    }}
                    size="middle"
                    scroll={{ x: 800 }}
                />
            </Card>
        </div>
    );
};

export default AdminUsersPage;