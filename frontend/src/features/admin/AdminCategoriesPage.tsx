import { useState } from 'react';
import {
    Card,
    Tree,
    Button,
    Modal,
    Form,
    Input,
    Select,
    Switch,
    Typography,
    Row,
    Col,
    Descriptions,
    Popconfirm,
    App,
    Tag,
} from 'antd';
import {
    PlusOutlined,
    EditOutlined,
    DeleteOutlined,
    ReloadOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    getCategoryTree,
    getCategoryById,
    createCategory,
    updateCategory,
    deleteCategory,
    activateCategory,
    deactivateCategory,
} from '@/api/adminCategories';
import type { CategoryRequest } from '@/api/adminCategories';
import type { CategoryTree } from '@/types/product';
import type { DataNode } from 'antd/es/tree';

const { Title, Text } = Typography;
const { TextArea } = Input;

/** Рекурсивно строит дерево для Ant Design */
const mapToTreeData = (categories: CategoryTree[]): DataNode[] =>
    categories
        .sort((a, b) => a.displayOrder - b.displayOrder)
        .map((cat) => ({
            key: cat.id,
            title: (
                <span>
          {cat.name}{' '}
                    {!cat.isActive && (
                        <Tag color="default" style={{ marginLeft: 4 }}>
                            скрыта
                        </Tag>
                    )}
        </span>
            ),
            children: cat.children.length > 0 ? mapToTreeData(cat.children) : undefined,
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

const AdminCategoriesPage = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [modalOpen, setModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [form] = Form.useForm<CategoryRequest>();

    // Загрузка дерева
    const { data: tree = [], isLoading, refetch } = useQuery({
        queryKey: ['adminCategoryTree'],
        queryFn: getCategoryTree,
    });

    // Загрузка деталей выбранной категории
    const { data: selectedCategory } = useQuery({
        queryKey: ['category', selectedId],
        queryFn: () => getCategoryById(selectedId!),
        enabled: !!selectedId,
    });

    // Мутации
    const createMutation = useMutation({
        mutationFn: createCategory,
        onSuccess: () => {
            messageApi.success('Категория создана');
            queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
            queryClient.invalidateQueries({ queryKey: ['categories'] });
            setModalOpen(false);
            form.resetFields();
        },
        onError: () => messageApi.error('Ошибка при создании категории'),
    });

    const updateMutation = useMutation({
        mutationFn: ({ id, request }: { id: number; request: CategoryRequest }) =>
            updateCategory(id, request),
        onSuccess: () => {
            messageApi.success('Категория обновлена');
            queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
            queryClient.invalidateQueries({ queryKey: ['categories'] });
            queryClient.invalidateQueries({ queryKey: ['category', editingId] });
            setModalOpen(false);
            setEditingId(null);
            form.resetFields();
        },
        onError: () => messageApi.error('Ошибка при обновлении категории'),
    });

    const deleteMutation = useMutation({
        mutationFn: deleteCategory,
        onSuccess: () => {
            messageApi.success('Категория удалена');
            queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
            queryClient.invalidateQueries({ queryKey: ['categories'] });
            setSelectedId(null);
        },
        onError: () => messageApi.error('Ошибка при удалении. Возможно есть товары или подкатегории.'),
    });

    const toggleActiveMutation = useMutation({
        mutationFn: async ({ id, isActive }: { id: number; isActive: boolean }) => {
            return isActive ? deactivateCategory(id) : activateCategory(id);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
            queryClient.invalidateQueries({ queryKey: ['category', selectedId] });
            messageApi.success('Статус обновлён');
        },
        onError: () => messageApi.error('Ошибка при обновлении статуса'),
    });

    const handleAdd = (parentId?: number) => {
        setEditingId(null);
        form.resetFields();
        if (parentId) {
            form.setFieldValue('parentId', parentId);
        }
        setModalOpen(true);
    };

    const handleEdit = async () => {
        if (!selectedCategory) return;
        setEditingId(selectedCategory.id);
        form.setFieldsValue({
            name: selectedCategory.name,
            description: selectedCategory.description || '',
            parentId: selectedCategory.parentId,
        });
        setModalOpen(true);
    };

    const handleSubmit = (values: CategoryRequest) => {
        if (editingId) {
            updateMutation.mutate({ id: editingId, request: values });
        } else {
            createMutation.mutate(values);
        }
    };

    const treeData = mapToTreeData(tree);
    const parentOptions = flattenForSelect(tree, editingId || undefined);

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
                    Категории
                </Title>
                <div style={{ display: 'flex', gap: 8 }}>
                    <Button icon={<ReloadOutlined />} onClick={() => refetch()}>
                        Обновить
                    </Button>
                    <Button type="primary" icon={<PlusOutlined />} onClick={() => handleAdd()}>
                        Добавить категорию
                    </Button>
                </div>
            </div>

            <Row gutter={24}>
                {/* Дерево категорий */}
                <Col xs={24} md={12}>
                    <Card title="Дерево категорий" loading={isLoading} style={{ borderRadius: 12 }}>
                        {treeData.length > 0 ? (
                            <Tree
                                treeData={treeData}
                                selectedKeys={selectedId ? [selectedId] : []}
                                onSelect={(keys) => setSelectedId(keys.length > 0 ? Number(keys[0]) : null)}
                                defaultExpandAll
                                showLine
                                blockNode
                            />
                        ) : (
                            <Text type="secondary">Нет категорий</Text>
                        )}
                    </Card>
                </Col>

                {/* Детали выбранной категории */}
                <Col xs={24} md={12}>
                    {selectedCategory ? (
                        <Card
                            title={selectedCategory.name}
                            style={{ borderRadius: 12 }}
                            extra={
                                <div style={{ display: 'flex', gap: 8 }}>
                                    <Button
                                        size="small"
                                        icon={<PlusOutlined />}
                                        onClick={() => handleAdd(selectedCategory.id)}
                                    >
                                        Подкатегория
                                    </Button>
                                    <Button
                                        size="small"
                                        icon={<EditOutlined />}
                                        onClick={handleEdit}
                                    >
                                        Редактировать
                                    </Button>
                                    <Popconfirm
                                        title="Удалить категорию?"
                                        description="Убедитесь что нет товаров и подкатегорий"
                                        onConfirm={() => deleteMutation.mutate(selectedCategory.id)}
                                        okText="Удалить"
                                        cancelText="Отмена"
                                        okButtonProps={{ danger: true }}
                                    >
                                        <Button size="small" danger icon={<DeleteOutlined />}>
                                            Удалить
                                        </Button>
                                    </Popconfirm>
                                </div>
                            }
                        >
                            <Descriptions column={1} size="small">
                                <Descriptions.Item label="ID">{selectedCategory.id}</Descriptions.Item>
                                <Descriptions.Item label="Slug">{selectedCategory.slug}</Descriptions.Item>
                                <Descriptions.Item label="Описание">
                                    {selectedCategory.description || '—'}
                                </Descriptions.Item>
                                <Descriptions.Item label="Родитель">
                                    {selectedCategory.parentName || 'Корневая'}
                                </Descriptions.Item>
                                <Descriptions.Item label="Порядок">
                                    {selectedCategory.displayOrder}
                                </Descriptions.Item>
                                <Descriptions.Item label="External ID">
                                    {selectedCategory.externalId || '—'}
                                </Descriptions.Item>
                                <Descriptions.Item label="Активна">
                                    <Switch
                                        checked={selectedCategory.isActive}
                                        onChange={() =>
                                            toggleActiveMutation.mutate({
                                                id: selectedCategory.id,
                                                isActive: selectedCategory.isActive,
                                            })
                                        }
                                        loading={toggleActiveMutation.isPending}
                                    />
                                </Descriptions.Item>
                            </Descriptions>
                        </Card>
                    ) : (
                        <Card style={{ borderRadius: 12 }}>
                            <div style={{ textAlign: 'center', padding: 40 }}>
                                <Text type="secondary">Выберите категорию в дереве</Text>
                            </div>
                        </Card>
                    )}
                </Col>
            </Row>

            {/* Модальное окно создания/редактирования */}
            <Modal
                title={editingId ? 'Редактировать категорию' : 'Новая категория'}
                open={modalOpen}
                onCancel={() => {
                    setModalOpen(false);
                    setEditingId(null);
                    form.resetFields();
                }}
                onOk={() => form.submit()}
                confirmLoading={createMutation.isPending || updateMutation.isPending}
                okText={editingId ? 'Сохранить' : 'Создать'}
                cancelText="Отмена"
            >
                <Form<CategoryRequest>
                    form={form}
                    layout="vertical"
                    onFinish={handleSubmit}
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
                        <TextArea rows={3} placeholder="Описание категории" />
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

export default AdminCategoriesPage;