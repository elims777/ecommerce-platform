import { useState } from 'react';
import {
    Table,
    Button,
    Input,
    Select,
    Tag,
    Typography,
    Image,
    Popconfirm,
    App,
    Card,
    Row,
    Col,
    Switch,
} from 'antd';
import {
    PlusOutlined,
    DeleteOutlined,
    SearchOutlined,
    ShoppingOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getProducts } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import apiClient from '@/api/client';
import type { Product, CategoryTree } from '@/types/product';
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
    });

/** Получает URL главного изображения */
const getPrimaryImageUrl = (product: Product): string | null => {
    if (!product.images || product.images.length === 0) return null;
    const primary = product.images.find((img) => img.isPrimary);
    return primary?.fileUrl ?? product.images[0].fileUrl;
};

/** Рекурсивно собирает все категории в плоский список для Select */
const flattenCategories = (
    tree: CategoryTree[],
    prefix = '',
): { value: number; label: string }[] => {
    const result: { value: number; label: string }[] = [];
    for (const node of tree) {
        result.push({ value: node.id, label: prefix + node.name });
        if (node.children.length > 0) {
            result.push(...flattenCategories(node.children, prefix + '— '));
        }
    }
    return result;
};

const AdminProductsPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    // Фильтры
    const [currentPage, setCurrentPage] = useState(1);
    const [searchQuery, setSearchQuery] = useState('');
    const [categoryFilter, setCategoryFilter] = useState<number | undefined>();
    const pageSize = 20;

    // Загрузка товаров
    const {
        data: productsPage,
        isLoading,
        refetch,
    } = useQuery({
        queryKey: ['adminProducts', { page: currentPage, category: categoryFilter }],
        queryFn: () =>
            getProducts({
                page: currentPage - 1,
                size: pageSize,
                categoryId: categoryFilter,
            }),
    });

    // Загрузка категорий для фильтра
    const { data: categoryTree = [] } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    const categoryOptions = flattenCategories(
        categoryTree.filter((c) => c.isActive),
    );

    // Мутация: активация/деактивация товара
    const toggleActiveMutation = useMutation({
        mutationFn: async ({
                               id,
                               isActive,
                           }: {
            id: number;
            isActive: boolean;
        }) => {
            const endpoint = isActive
                ? `/v1/products/${id}/deactivate`
                : `/v1/products/${id}/activate`;
            await apiClient.put(endpoint);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminProducts'] });
            messageApi.success('Статус товара обновлён');
        },
        onError: () => {
            messageApi.error('Ошибка при обновлении статуса');
        },
    });

    // Мутация: удаление товара
    const deleteMutation = useMutation({
        mutationFn: async (id: number) => {
            await apiClient.delete(`/v1/products/${id}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminProducts'] });
            messageApi.success('Товар удалён');
        },
        onError: () => {
            messageApi.error('Ошибка при удалении товара');
        },
    });

    // Фильтрация по поиску на клиенте (поверх серверной пагинации)
    const filteredProducts = productsPage?.content.filter((p) =>
        searchQuery
            ? p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            p.sku?.toLowerCase().includes(searchQuery.toLowerCase())
            : true,
    );

    // Колонки таблицы
    const columns: ColumnsType<Product> = [
        {
            title: '',
            key: 'image',
            width: 60,
            render: (_, record) => {
                const url = getPrimaryImageUrl(record);
                return url ? (
                    <Image
                        src={url}
                        alt={record.name}
                        width={40}
                        height={40}
                        style={{ objectFit: 'contain', borderRadius: 4 }}
                        preview={false}
                    />
                ) : (
                    <div
                        style={{
                            width: 40,
                            height: 40,
                            background: '#fafafa',
                            borderRadius: 4,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                        }}
                    >
                        <ShoppingOutlined style={{ color: '#d9d9d9' }} />
                    </div>
                );
            },
        },
        {
            title: 'Название',
            dataIndex: 'name',
            key: 'name',
            ellipsis: true,
            render: (name: string, record) => (
                <a onClick={() => navigate(`/products/${record.id}`)}>{name}</a>
            ),
        },
        {
            title: 'Артикул',
            dataIndex: 'sku',
            key: 'sku',
            width: 120,
            render: (sku: string | null) => sku || '—',
        },
        {
            title: 'Категория',
            dataIndex: 'categoryName',
            key: 'categoryName',
            width: 180,
            render: (name: string | null) =>
                name ? <Tag color="blue">{name}</Tag> : <Text type="secondary">—</Text>,
        },
        {
            title: 'Цена',
            dataIndex: 'price',
            key: 'price',
            width: 130,
            sorter: (a, b) => a.price - b.price,
            render: (price: number) => formatPrice(price),
        },
        {
            title: 'Остаток',
            dataIndex: 'stockQuantity',
            key: 'stockQuantity',
            width: 100,
            sorter: (a, b) => a.stockQuantity - b.stockQuantity,
            render: (qty: number) => (
                <Text type={qty > 0 ? 'success' : 'danger'}>{qty}</Text>
            ),
        },
        {
            title: 'Активен',
            dataIndex: 'isActive',
            key: 'isActive',
            width: 90,
            render: (isActive: boolean, record) => (
                <Switch
                    checked={isActive}
                    size="small"
                    loading={toggleActiveMutation.isPending}
                    onChange={() =>
                        toggleActiveMutation.mutate({ id: record.id, isActive })
                    }
                />
            ),
        },
        {
            title: 'Создан',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 110,
            render: (date: string) => formatDate(date),
        },
        {
            title: '',
            key: 'actions',
            width: 50,
            render: (_, record) => (
                <Popconfirm
                    title="Удалить товар?"
                    description="Это действие необратимо"
                    onConfirm={() => deleteMutation.mutate(record.id)}
                    okText="Удалить"
                    cancelText="Отмена"
                    okButtonProps={{ danger: true }}
                >
                    <Button type="text" danger icon={<DeleteOutlined />} size="small" />
                </Popconfirm>
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
                    Товары
                </Title>
                <Button type="primary" icon={<PlusOutlined />}>
                    Добавить товар
                </Button>
            </div>

            {/* Фильтры */}
            <Card style={{ marginBottom: 16, borderRadius: 12 }}>
                <Row gutter={16} align="middle">
                    <Col xs={24} sm={10}>
                        <Input
                            placeholder="Поиск по названию или артикулу..."
                            prefix={<SearchOutlined />}
                            allowClear
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                    </Col>
                    <Col xs={24} sm={8}>
                        <Select
                            placeholder="Все категории"
                            allowClear
                            style={{ width: '100%' }}
                            options={categoryOptions}
                            value={categoryFilter}
                            onChange={(val) => {
                                setCategoryFilter(val);
                                setCurrentPage(1);
                            }}
                        />
                    </Col>
                    <Col xs={24} sm={6} style={{ textAlign: 'right' }}>
                        <Button
                            icon={<ReloadOutlined />}
                            onClick={() => refetch()}
                        >
                            Обновить
                        </Button>
                    </Col>
                </Row>
            </Card>

            {/* Таблица */}
            <Card style={{ borderRadius: 12 }}>
                <Table<Product>
                    columns={columns}
                    dataSource={filteredProducts}
                    rowKey="id"
                    loading={isLoading}
                    pagination={{
                        current: currentPage,
                        total: productsPage?.totalElements || 0,
                        pageSize,
                        onChange: (page) => setCurrentPage(page),
                        showTotal: (total) => `Всего ${total} товаров`,
                        showSizeChanger: false,
                    }}
                    size="middle"
                    scroll={{ x: 900 }}
                />
            </Card>
        </div>
    );
};

export default AdminProductsPage;