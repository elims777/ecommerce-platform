import { Table, Button, Tag, Space, Typography, Popconfirm, App } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { getAdminNews, deleteNews } from '@/api/news';
import type { NewsAdmin } from '@/types/news';

const { Title } = Typography;

const PAGE_SIZE = 20;

const formatDate = (iso: string | null): string =>
    iso ? new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—';

const AdminNewsPage = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const [page, setPage] = useState(1);

    const { data, isLoading } = useQuery({
        queryKey: ['adminNews', page],
        queryFn: () => getAdminNews(page - 1, PAGE_SIZE),
    });

    const deleteMutation = useMutation({
        mutationFn: deleteNews,
        onSuccess: () => {
            messageApi.success('Новость удалена');
            queryClient.invalidateQueries({ queryKey: ['adminNews'] });
        },
        onError: () => messageApi.error('Не удалось удалить новость'),
    });

    const columns = [
        {
            title: 'Заголовок',
            dataIndex: 'title',
            key: 'title',
            render: (title: string, record: NewsAdmin) => (
                <Link to={`/admin/news/${record.id}/edit`}>{title}</Link>
            ),
        },
        {
            title: 'Статус',
            dataIndex: 'isPublished',
            key: 'isPublished',
            width: 130,
            render: (isPublished: boolean) => (
                <Tag color={isPublished ? 'green' : 'default'}>
                    {isPublished ? 'Опубликована' : 'Черновик'}
                </Tag>
            ),
        },
        {
            title: 'Опубликована',
            dataIndex: 'publishedAt',
            key: 'publishedAt',
            width: 130,
            render: formatDate,
        },
        {
            title: 'Изменена',
            dataIndex: 'updatedAt',
            key: 'updatedAt',
            width: 130,
            render: formatDate,
        },
        {
            title: '',
            key: 'actions',
            width: 130,
            render: (_: unknown, record: NewsAdmin) => (
                <Space size="small">
                    {record.isPublished && (
                        <Button
                            size="small"
                            icon={<EyeOutlined />}
                            title="Посмотреть на сайте"
                            onClick={() => window.open(`/news/${record.slug}`, '_blank')}
                        />
                    )}
                    <Button
                        size="small"
                        icon={<EditOutlined />}
                        title="Редактировать"
                        onClick={() => navigate(`/admin/news/${record.id}/edit`)}
                    />
                    <Popconfirm
                        title="Удалить новость?"
                        description="Действие необратимо"
                        okText="Удалить"
                        cancelText="Отмена"
                        okButtonProps={{ danger: true }}
                        onConfirm={() => deleteMutation.mutate(record.id)}
                    >
                        <Button size="small" danger icon={<DeleteOutlined />} title="Удалить" />
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <Title level={3} style={{ margin: 0 }}>Новости</Title>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/news/new/edit')}>
                    Добавить новость
                </Button>
            </div>

            <Table
                rowKey="id"
                columns={columns}
                dataSource={data?.content ?? []}
                loading={isLoading}
                pagination={{
                    current: page,
                    pageSize: PAGE_SIZE,
                    total: data?.totalElements ?? 0,
                    showSizeChanger: false,
                    onChange: setPage,
                }}
            />
        </div>
    );
};

export default AdminNewsPage;
