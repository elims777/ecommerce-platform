import { useState, useMemo } from 'react';
import {
    Row,
    Col,
    Card,
    Tree,
    Table,
    Input,
    Button,
    Tag,
    Switch,
    Typography,
    Image,
    Popconfirm,
    Modal,
    Form,
    Select,
    App,
    Spin,
} from 'antd';
import {
    SearchOutlined,
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    ReloadOutlined,
    ShoppingOutlined,
    AppstoreOutlined,
    DragOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getAdminProducts, searchProducts } from '@/api/products';
import {
    getCategoryTree,
    createCategory,
    updateCategory,
    deleteCategory,
    getCategoryById,
    setCategoryParent,
} from '@/api/adminCategories';
import { changeProductCategory } from '@/api/adminProducts';
import apiClient from '@/api/client';
import type { CategoryRequest } from '@/api/adminCategories';
import type { Product, CategoryTree } from '@/types/product';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;
const { TextArea } = Input;

const ALPHABET = 'АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ'.split('');

/** Форматирует цену */
const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(price);

/** Получает URL главного изображения */
const getPrimaryImageUrl = (product: Product): string | null => {
    if (!product.images || product.images.length === 0) return null;
    const primary = product.images.find((img) => img.isPrimary);
    return primary?.fileUrl ?? product.images[0].fileUrl;
};

/** Находит название категории в дереве */
const findCatName = (tree: CategoryTree[], id: number): string => {
    for (const cat of tree) {
        if (cat.id === id) return cat.name;
        const found = findCatName(cat.children, id);
        if (found) return found;
    }
    return '';
};

/** Рекурсивно строит дерево для Ant Design с поддержкой drop товаров */
const mapToTreeData = (
    categories: CategoryTree[],
    dragOverCatId: number | null,
): any[] =>
    categories
        .sort((a, b) => a.displayOrder - b.displayOrder)
        .map((cat) => ({
            key: cat.id,
            title: (
                <span
                    style={{
                        padding: '2px 6px',
                        borderRadius: 4,
                        background: dragOverCatId === cat.id ? '#e6f4ff' : 'transparent',
                        border: dragOverCatId === cat.id ? '1px dashed #1677ff' : '1px solid transparent',
                        transition: 'all 0.2s',
                    }}
                    data-category-id={cat.id}
                >
          {cat.name}
                    {!cat.isActive && (
                        <Tag color="default" style={{ marginLeft: 4, fontSize: 10 }}>
                            скрыта
                        </Tag>
                    )}
        </span>
            ),
            children: cat.children.length > 0 ? mapToTreeData(cat.children, dragOverCatId) : undefined,
        }));

/** Рекурсивно собирает плоский список для Select */
const flattenForSelect = (
    categories: CategoryTree[],
    excludeId?: number,
    prefix = '',
): { value: number; label: string }[] => {
    const result: { value: number; label: string }[] = [];
    for (const cat of categories) {
        if (cat.id !== excludeId) {
            result.push({ value: cat.id, label: prefix + cat.name });
            if (cat.children.length > 0) {
                result.push(...flattenForSelect(cat.children, excludeId, prefix + '— '));
            }
        }
    }
    return result;
};

const AdminCatalogPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    const [selectedCategoryId, setSelectedCategoryId] = useState<number | undefined>();
    const [searchQuery, setSearchQuery] = useState('');
    const [activeLetter, setActiveLetter] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [showAllProducts, setShowAllProducts] = useState(true);
    const [dragOverCatId, setDragOverCatId] = useState<number | null>(null);
    const [draggedProductId, setDraggedProductId] = useState<number | null>(null);

    // Модальное окно категории
    const [catModalOpen, setCatModalOpen] = useState(false);
    const [editingCatId, setEditingCatId] = useState<number | null>(null);
    const [catForm] = Form.useForm<CategoryRequest>();

    const pageSize = 20;

    // Загрузка дерева категорий
    const { data: categoryTree = [], isLoading: treeLoading } = useQuery({
        queryKey: ['adminCategoryTree'],
        queryFn: getCategoryTree,
    });

    // Загрузка товаров
    const {
        data: productsPage,
        isLoading: productsLoading,
        refetch: refetchProducts,
    } = useQuery({
        queryKey: ['adminCatalogProducts', { page: currentPage, category: selectedCategoryId }],
        queryFn: () =>
            getAdminProducts({
                page: currentPage - 1,
                size: pageSize,
                categoryId: selectedCategoryId,
                sort: 'name,asc',
            }),
    });

    // Поиск товаров
    const { data: searchResults } = useQuery({
        queryKey: ['adminProductSearch', searchQuery],
        queryFn: () => searchProducts(searchQuery),
        enabled: searchQuery.length >= 2,
    });

    // Определяем какие товары показывать
    const displayProducts = useMemo(() => {
        let items: Product[] = [];
        if (searchQuery.length >= 2 && searchResults) {
            items = searchResults;
        } else if (productsPage) {
            items = productsPage.content;
        }
        if (activeLetter) {
            items = items.filter((p) => p.name.toUpperCase().startsWith(activeLetter));
        }
        return items;
    }, [searchQuery, searchResults, productsPage, activeLetter]);

    const invalidateAll = () => {
        queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
        queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
        queryClient.invalidateQueries({ queryKey: ['categories'] });
    };

    // === Мутация: перемещение категории (drag-and-drop дерева) ===
    const moveCategoryMutation = useMutation({
        mutationFn: ({ catId, newParentId }: { catId: number; newParentId: number }) =>
            setCategoryParent(catId, newParentId),
        onSuccess: (_, variables) => {
            const catName = findCatName(categoryTree, variables.catId);
            const parentName = findCatName(categoryTree, variables.newParentId);
            messageApi.success(`«${catName}» перемещена в «${parentName}»`);
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при перемещении категории'),
    });

    // === Мутация: перемещение товара в категорию (drag-and-drop) ===
    const moveProductMutation = useMutation({
        mutationFn: ({ productId, categoryId }: { productId: number; categoryId: number }) =>
            changeProductCategory(productId, categoryId),
        onSuccess: (_, variables) => {
            const catName = findCatName(categoryTree, variables.categoryId);
            messageApi.success(`Товар перемещён в «${catName}»`);
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при перемещении товара'),
    });

    // === Мутации категорий ===
    const createCatMutation = useMutation({
        mutationFn: createCategory,
        onSuccess: () => {
            messageApi.success('Категория создана');
            invalidateAll();
            setCatModalOpen(false);
            catForm.resetFields();
        },
        onError: () => messageApi.error('Ошибка при создании категории'),
    });

    const updateCatMutation = useMutation({
        mutationFn: ({ id, request }: { id: number; request: CategoryRequest }) =>
            updateCategory(id, request),
        onSuccess: () => {
            messageApi.success('Категория обновлена');
            invalidateAll();
            setCatModalOpen(false);
            setEditingCatId(null);
            catForm.resetFields();
        },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const deleteCatMutation = useMutation({
        mutationFn: deleteCategory,
        onSuccess: () => {
            messageApi.success('Категория удалена');
            invalidateAll();
            setSelectedCategoryId(undefined);
            setShowAllProducts(true);
        },
        onError: () => messageApi.error('Ошибка. Возможно есть товары или подкатегории.'),
    });

    // === Мутации товаров ===
    const toggleProductMutation = useMutation({
        mutationFn: async ({ id, isActive }: { id: number; isActive: boolean }) => {
            await apiClient.put(`/v1/products/${id}/${isActive ? 'deactivate' : 'activate'}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
            messageApi.success('Статус товара обновлён');
        },
    });

    const deleteProductMutation = useMutation({
        mutationFn: async (id: number) => {
            await apiClient.delete(`/v1/products/${id}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
            messageApi.success('Товар удалён');
        },
    });

    // === Обработчики ===

    const handleCategorySelect = (keys: React.Key[]) => {
        if (keys.length > 0) {
            setSelectedCategoryId(Number(keys[0]));
            setShowAllProducts(false);
            setCurrentPage(1);
            setSearchQuery('');
            setActiveLetter(null);
        }
    };

    const handleShowAll = () => {
        setSelectedCategoryId(undefined);
        setShowAllProducts(true);
        setCurrentPage(1);
        setSearchQuery('');
        setActiveLetter(null);
    };

    const handleAddCategory = (parentId?: number) => {
        setEditingCatId(null);
        catForm.resetFields();
        if (parentId) catForm.setFieldValue('parentId', parentId);
        setCatModalOpen(true);
    };

    const handleEditCategory = async (catId: number) => {
        setEditingCatId(catId);
        try {
            const cat = await getCategoryById(catId);
            catForm.setFieldsValue({
                name: cat.name,
                description: cat.description || '',
                parentId: cat.parentId,
            });
            setCatModalOpen(true);
        } catch {
            messageApi.error('Ошибка загрузки категории');
        }
    };

    const handleCatSubmit = (values: CategoryRequest) => {
        if (editingCatId) {
            updateCatMutation.mutate({ id: editingCatId, request: values });
        } else {
            createCatMutation.mutate(values);
        }
    };

    // === Drag-and-drop дерева категорий ===
    const handleTreeDrop = (info: any) => {
        const dragId = Number(info.dragNode.key);
        const dropId = Number(info.node.key);

        if (dragId === dropId) return;

        // dropToGap = true значит "между нодами", false = "внутрь ноды"
        if (!info.dropToGap) {
            // Перемещаем внутрь dropId
            moveCategoryMutation.mutate({ catId: dragId, newParentId: dropId });
        }
        // Перемещение между (reorder) пока не поддерживаем — нет API для displayOrder
    };

    // === Drag-and-drop товара на категорию ===
    const handleProductDragStart = (productId: number) => {
        setDraggedProductId(productId);
    };

    const handleTreeDragOver = (e: React.DragEvent, categoryId: number) => {
        e.preventDefault();
        e.stopPropagation();
        if (draggedProductId) {
            setDragOverCatId(categoryId);
        }
    };

    const handleTreeDragLeave = () => {
        setDragOverCatId(null);
    };

    const handleTreeDrop2 = (e: React.DragEvent, categoryId: number) => {
        e.preventDefault();
        e.stopPropagation();
        setDragOverCatId(null);
        if (draggedProductId) {
            moveProductMutation.mutate({ productId: draggedProductId, categoryId });
            setDraggedProductId(null);
        }
    };

    // Дерево для Ant Design
    const treeData = mapToTreeData(categoryTree, dragOverCatId);
    const parentOptions = flattenForSelect(categoryTree, editingCatId || undefined);

    // Колонки таблицы товаров
    const columns: ColumnsType<Product> = [
        {
            title: '',
            key: 'drag',
            width: 30,
            render: () => (
                <DragOutlined style={{ cursor: 'grab', color: '#bbb' }} />
            ),
        },
        {
            title: '',
            key: 'image',
            width: 50,
            render: (_, record) => {
                const url = getPrimaryImageUrl(record);
                return url ? (
                    <Image
                        src={url}
                        width={36}
                        height={36}
                        style={{ objectFit: 'contain', borderRadius: 4 }}
                        preview={false}
                    />
                ) : (
                    <ShoppingOutlined style={{ fontSize: 20, color: '#d9d9d9' }} />
                );
            },
        },
        {
            title: 'Название',
            dataIndex: 'name',
            key: 'name',
            ellipsis: true,
            render: (name: string, record) => (
                <a onClick={() => navigate(`/admin/products/${record.id}/edit`)}>
                    {name}
                </a>
            ),
        },
        {
            title: 'Артикул',
            dataIndex: 'sku',
            key: 'sku',
            width: 100,
            render: (sku: string | null) => sku || '—',
        },
        {
            title: 'Цена',
            dataIndex: 'price',
            key: 'price',
            width: 110,
            render: (price: number) => formatPrice(price),
        },
        {
            title: 'Ост.',
            dataIndex: 'stockQuantity',
            key: 'stockQuantity',
            width: 55,
            render: (qty: number) => (
                <Text type={qty > 0 ? 'success' : 'danger'}>{qty}</Text>
            ),
        },
        {
            title: 'Акт.',
            dataIndex: 'isActive',
            key: 'isActive',
            width: 55,
            render: (isActive: boolean, record) => (
                <Switch
                    checked={isActive}
                    size="small"
                    onChange={() => toggleProductMutation.mutate({ id: record.id, isActive })}
                />
            ),
        },
        {
            title: '',
            key: 'actions',
            width: 40,
            render: (_, record) => (
                <Popconfirm
                    title="Удалить товар?"
                    onConfirm={() => deleteProductMutation.mutate(record.id)}
                    okText="Да"
                    cancelText="Нет"
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
                    marginBottom: 16,
                }}
            >
                <Title level={2} style={{ margin: 0 }}>
                    Каталог
                </Title>
                <div style={{ display: 'flex', gap: 8 }}>
                    <Button
                        icon={<ReloadOutlined />}
                        onClick={() => { refetchProducts(); invalidateAll(); }}
                    >
                        Обновить
                    </Button>
                    <Button icon={<PlusOutlined />} onClick={() => handleAddCategory()}>
                        Категория
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={() => navigate('/admin/products/new')}
                    >
                        Товар
                    </Button>
                </div>
            </div>

            {/* Быстрый поиск */}
            <Card size="small" style={{ marginBottom: 12, borderRadius: 12 }}>
                <Input
                    placeholder="Быстрый поиск по названию или артикулу..."
                    prefix={<SearchOutlined />}
                    allowClear
                    value={searchQuery}
                    onChange={(e) => {
                        setSearchQuery(e.target.value);
                        if (e.target.value) {
                            setActiveLetter(null);
                            setSelectedCategoryId(undefined);
                            setShowAllProducts(true);
                        }
                    }}
                />
            </Card>

            {/* Алфавитный указатель */}
            <Card size="small" style={{ marginBottom: 12, borderRadius: 12 }}>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                    <Button
                        size="small"
                        type={!activeLetter ? 'primary' : 'default'}
                        onClick={() => setActiveLetter(null)}
                    >
                        Все
                    </Button>
                    {ALPHABET.map((letter) => (
                        <Button
                            key={letter}
                            size="small"
                            type={activeLetter === letter ? 'primary' : 'default'}
                            onClick={() => setActiveLetter(activeLetter === letter ? null : letter)}
                            style={{ minWidth: 32, padding: '0 6px' }}
                        >
                            {letter}
                        </Button>
                    ))}
                </div>
            </Card>

            <Row gutter={16}>
                {/* Левая колонка — категории */}
                <Col xs={24} md={7}>
                    <Card
                        title={
                            <span>
                <AppstoreOutlined /> Категории
              </span>
                        }
                        size="small"
                        style={{ borderRadius: 12 }}
                        extra={
                            <Text type="secondary" style={{ fontSize: 11 }}>
                                Перетаскивайте для перемещения
                            </Text>
                        }
                    >
                        <Button
                            block
                            type={showAllProducts ? 'primary' : 'default'}
                            onClick={handleShowAll}
                            style={{ marginBottom: 12 }}
                        >
                            Все товары ({productsPage?.totalElements || 0})
                        </Button>

                        {treeLoading ? (
                            <div style={{ textAlign: 'center', padding: 16 }}>
                                <Spin />
                            </div>
                        ) : (
                            <div
                                onDragOver={(e) => {
                                    // Находим ближайший элемент с data-category-id
                                    const target = (e.target as HTMLElement).closest('[data-category-id]');
                                    if (target) {
                                        const catId = Number(target.getAttribute('data-category-id'));
                                        handleTreeDragOver(e, catId);
                                    }
                                }}
                                onDragLeave={handleTreeDragLeave}
                                onDrop={(e) => {
                                    const target = (e.target as HTMLElement).closest('[data-category-id]');
                                    if (target) {
                                        const catId = Number(target.getAttribute('data-category-id'));
                                        handleTreeDrop2(e, catId);
                                    }
                                }}
                            >
                                <Tree
                                    treeData={treeData}
                                    selectedKeys={selectedCategoryId ? [selectedCategoryId] : []}
                                    onSelect={handleCategorySelect}
                                    draggable
                                    onDrop={handleTreeDrop}
                                    defaultExpandAll
                                    showLine
                                    blockNode
                                />
                            </div>
                        )}

                        {/* Действия с выбранной категорией */}
                        {selectedCategoryId && (
                            <div
                                style={{
                                    marginTop: 12,
                                    paddingTop: 12,
                                    borderTop: '1px solid #f0f0f0',
                                    display: 'flex',
                                    gap: 4,
                                    flexWrap: 'wrap',
                                }}
                            >
                                <Button
                                    size="small"
                                    icon={<PlusOutlined />}
                                    onClick={() => handleAddCategory(selectedCategoryId)}
                                >
                                    Подкат.
                                </Button>
                                <Button
                                    size="small"
                                    icon={<EditOutlined />}
                                    onClick={() => handleEditCategory(selectedCategoryId)}
                                >
                                    Изм.
                                </Button>
                                <Popconfirm
                                    title="Удалить категорию?"
                                    onConfirm={() => deleteCatMutation.mutate(selectedCategoryId)}
                                    okText="Да"
                                    cancelText="Нет"
                                >
                                    <Button size="small" danger icon={<DeleteOutlined />}>
                                        Удал.
                                    </Button>
                                </Popconfirm>
                            </div>
                        )}
                    </Card>
                </Col>

                {/* Правая колонка — товары */}
                <Col xs={24} md={17}>
                    <Card
                        title={
                            selectedCategoryId
                                ? findCatName(categoryTree, selectedCategoryId) || 'Товары'
                                : activeLetter
                                    ? `Товары на «${activeLetter}»`
                                    : searchQuery
                                        ? `Поиск: «${searchQuery}»`
                                        : 'Все товары'
                        }
                        size="small"
                        style={{ borderRadius: 12 }}
                        extra={
                            draggedProductId && (
                                <Tag color="blue">Перетащите товар на категорию слева</Tag>
                            )
                        }
                    >
                        <Table<Product>
                            columns={columns}
                            dataSource={displayProducts}
                            rowKey="id"
                            loading={productsLoading}
                            onRow={(record) => ({
                                draggable: true,
                                onDragStart: () => handleProductDragStart(record.id),
                                onDragEnd: () => {
                                    setDraggedProductId(null);
                                    setDragOverCatId(null);
                                },
                                style: {
                                    cursor: 'grab',
                                    opacity: draggedProductId === record.id ? 0.5 : 1,
                                },
                            })}
                            pagination={
                                searchQuery.length >= 2 || activeLetter
                                    ? { pageSize: 50 }
                                    : {
                                        current: currentPage,
                                        total: productsPage?.totalElements || 0,
                                        pageSize,
                                        onChange: (page) => setCurrentPage(page),
                                        showTotal: (total) => `Всего ${total}`,
                                        showSizeChanger: false,
                                    }
                            }
                            size="small"
                            scroll={{ x: 600 }}
                        />
                    </Card>
                </Col>
            </Row>

            {/* Модальное окно категории */}
            <Modal
                title={editingCatId ? 'Редактировать категорию' : 'Новая категория'}
                open={catModalOpen}
                onCancel={() => {
                    setCatModalOpen(false);
                    setEditingCatId(null);
                    catForm.resetFields();
                }}
                onOk={() => catForm.submit()}
                confirmLoading={createCatMutation.isPending || updateCatMutation.isPending}
                okText={editingCatId ? 'Сохранить' : 'Создать'}
                cancelText="Отмена"
            >
                <Form<CategoryRequest>
                    form={catForm}
                    layout="vertical"
                    onFinish={handleCatSubmit}
                >
                    <Form.Item
                        name="name"
                        label="Название"
                        rules={[
                            { required: true, message: 'Введите название' },
                            { min: 2, message: 'Минимум 2 символа' },
                        ]}
                    >
                        <Input placeholder="Название категории" />
                    </Form.Item>
                    <Form.Item name="description" label="Описание">
                        <TextArea rows={3} placeholder="Описание" />
                    </Form.Item>
                    <Form.Item name="parentId" label="Родительская категория">
                        <Select
                            placeholder="Корневая (без родителя)"
                            allowClear
                            options={parentOptions}
                        />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default AdminCatalogPage;