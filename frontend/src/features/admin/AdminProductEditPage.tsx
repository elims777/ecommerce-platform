import { useState } from 'react';
import {
    Card,
    Form,
    Input,
    InputNumber,
    Select,
    Switch,
    Button,
    Typography,
    Row,
    Col,
    Upload,
    Image,
    Table,
    Popconfirm,
    Spin,
    App,
    Divider,
} from 'antd';
import {
    SaveOutlined,
    ArrowLeftOutlined,
    UploadOutlined,
    DeleteOutlined,
    StarOutlined,
    PlusOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { getProductById } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import {
    updateProduct,
    uploadImage,
    deleteImage,
    setPrimaryImage,
    addAttribute,
    updateAttribute,
    deleteAttribute,
} from '@/api/adminProducts';
import type { ProductRequest, ProductAttributeRequest } from '@/api/adminProducts';
import type { CategoryTree, ProductImage } from '@/types/product';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;
const { TextArea } = Input;

/** Рекурсивно собирает категории для Select */
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

/** Интерфейс атрибута для таблицы */
interface AttributeRow {
    id: number;
    attributeName: string;
    attributeValue: string;
}

const AdminProductEditPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [form] = Form.useForm<ProductRequest>();
    const [attrForm] = Form.useForm<ProductAttributeRequest>();
    const [editingAttrId, setEditingAttrId] = useState<number | null>(null);
    const productId = Number(id);

    // Загрузка товара
    const { data: product, isLoading } = useQuery({
        queryKey: ['adminProduct', productId],
        queryFn: () => getProductById(productId),
        enabled: !!id,
    });

    // Загрузка категорий
    const { data: categoryTree = [] } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    const categoryOptions = flattenCategories(categoryTree.filter((c) => c.isActive));

    // Инициализация формы при загрузке товара
    if (product && !form.isFieldsTouched()) {
        form.setFieldsValue({
            name: product.name,
            description: product.description || '',
            shortDescription: product.shortDescription || '',
            price: product.price,
            stockQuantity: product.stockQuantity,
            categoryId: product.categoryId,
            isActive: product.isActive,
            isFeatured: product.isFeatured,
            sku: product.sku || '',
            unitOfMeasure: product.unitOfMeasure || '',
        });
    }

    const invalidateProduct = () => {
        queryClient.invalidateQueries({ queryKey: ['adminProduct', productId] });
        queryClient.invalidateQueries({ queryKey: ['adminProducts'] });
    };

    // Мутации
    const saveMutation = useMutation({
        mutationFn: (values: ProductRequest) => updateProduct(productId, values),
        onSuccess: () => {
            messageApi.success('Товар сохранён');
            invalidateProduct();
        },
        onError: () => messageApi.error('Ошибка при сохранении'),
    });

    const uploadMutation = useMutation({
        mutationFn: (file: File) => uploadImage(productId, file),
        onSuccess: () => {
            messageApi.success('Изображение загружено');
            invalidateProduct();
        },
        onError: () => messageApi.error('Ошибка при загрузке изображения'),
    });

    const deleteImageMutation = useMutation({
        mutationFn: (imageId: number) => deleteImage(productId, imageId),
        onSuccess: () => {
            messageApi.success('Изображение удалено');
            invalidateProduct();
        },
        onError: () => messageApi.error('Ошибка при удалении'),
    });

    const setPrimaryMutation = useMutation({
        mutationFn: (imageId: number) => setPrimaryImage(productId, imageId),
        onSuccess: () => {
            messageApi.success('Главное изображение обновлено');
            invalidateProduct();
        },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const addAttrMutation = useMutation({
        mutationFn: (request: ProductAttributeRequest) => addAttribute(productId, request),
        onSuccess: () => {
            messageApi.success('Характеристика добавлена');
            invalidateProduct();
            attrForm.resetFields();
        },
        onError: () => messageApi.error('Ошибка при добавлении'),
    });

    const updateAttrMutation = useMutation({
        mutationFn: ({ attrId, request }: { attrId: number; request: ProductAttributeRequest }) =>
            updateAttribute(productId, attrId, request),
        onSuccess: () => {
            messageApi.success('Характеристика обновлена');
            invalidateProduct();
            setEditingAttrId(null);
            attrForm.resetFields();
        },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const deleteAttrMutation = useMutation({
        mutationFn: (attrId: number) => deleteAttribute(productId, attrId),
        onSuccess: () => {
            messageApi.success('Характеристика удалена');
            invalidateProduct();
        },
        onError: () => messageApi.error('Ошибка при удалении'),
    });

    const handleAttrSubmit = (values: ProductAttributeRequest) => {
        if (editingAttrId) {
            updateAttrMutation.mutate({ attrId: editingAttrId, request: values });
        } else {
            addAttrMutation.mutate(values);
        }
    };

    // Колонки таблицы изображений
    const imageColumns: ColumnsType<ProductImage> = [
        {
            title: '',
            key: 'preview',
            width: 80,
            render: (_, record) => (
                <Image src={record.fileUrl} width={60} height={60} style={{ objectFit: 'contain' }} />
            ),
        },
        {
            title: 'Файл',
            key: 'info',
            render: (_, record) => (
                <div>
                    <Text>{record.contentType}</Text>
                    <br />
                    <Text type="secondary">
                        {record.width}×{record.height}, {Math.round((record.fileSize || 0) / 1024)} КБ
                    </Text>
                </div>
            ),
        },
        {
            title: 'Главное',
            dataIndex: 'isPrimary',
            key: 'isPrimary',
            width: 80,
            render: (isPrimary: boolean, record) =>
                isPrimary ? (
                    <StarOutlined style={{ color: '#faad14', fontSize: 18 }} />
                ) : (
                    <Button
                        type="link"
                        size="small"
                        onClick={() => setPrimaryMutation.mutate(record.id)}
                    >
                        Сделать
                    </Button>
                ),
        },
        {
            title: '',
            key: 'actions',
            width: 50,
            render: (_, record) => (
                <Popconfirm
                    title="Удалить изображение?"
                    onConfirm={() => deleteImageMutation.mutate(record.id)}
                    okText="Да"
                    cancelText="Нет"
                >
                    <Button type="text" danger icon={<DeleteOutlined />} size="small" />
                </Popconfirm>
            ),
        },
    ];

    // Колонки таблицы характеристик
    const attrColumns: ColumnsType<AttributeRow> = [
        {
            title: 'Название',
            dataIndex: 'attributeName',
            key: 'attributeName',
        },
        {
            title: 'Значение',
            dataIndex: 'attributeValue',
            key: 'attributeValue',
        },
        {
            title: '',
            key: 'actions',
            width: 100,
            render: (_, record) => (
                <div style={{ display: 'flex', gap: 4 }}>
                    <Button
                        type="link"
                        size="small"
                        onClick={() => {
                            setEditingAttrId(record.id);
                            attrForm.setFieldsValue({
                                attributeName: record.attributeName,
                                attributeValue: record.attributeValue,
                            });
                        }}
                    >
                        Изменить
                    </Button>
                    <Popconfirm
                        title="Удалить?"
                        onConfirm={() => deleteAttrMutation.mutate(record.id)}
                        okText="Да"
                        cancelText="Нет"
                    >
                        <Button type="text" danger icon={<DeleteOutlined />} size="small" />
                    </Popconfirm>
                </div>
            ),
        },
    ];

    if (isLoading) {
        return (
            <div style={{ textAlign: 'center', padding: 120 }}>
                <Spin size="large" />
            </div>
        );
    }

    if (!product) return null;

    return (
        <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 24 }}>
                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/products')}>
                    Назад
                </Button>
                <Title level={2} style={{ margin: 0 }}>
                    {product.name}
                </Title>
            </div>

            <Row gutter={24}>
                {/* Левая колонка — основные данные */}
                <Col xs={24} lg={16}>
                    <Card title="Основная информация" style={{ marginBottom: 16, borderRadius: 12 }}>
                        <Form<ProductRequest>
                            form={form}
                            layout="vertical"
                            onFinish={(values) => saveMutation.mutate(values)}
                        >
                            <Form.Item
                                name="name"
                                label="Название"
                                rules={[{ required: true, message: 'Введите название' }]}
                            >
                                <Input />
                            </Form.Item>

                            <Row gutter={16}>
                                <Col span={12}>
                                    <Form.Item name="price" label="Цена">
                                        <InputNumber
                                            style={{ width: '100%' }}
                                            min={0}
                                            precision={2}
                                            addonAfter="₽"
                                        />
                                    </Form.Item>
                                </Col>
                                <Col span={12}>
                                    <Form.Item name="stockQuantity" label="Остаток">
                                        <InputNumber style={{ width: '100%' }} min={0} />
                                    </Form.Item>
                                </Col>
                            </Row>

                            <Row gutter={16}>
                                <Col span={12}>
                                    <Form.Item name="categoryId" label="Категория">
                                        <Select
                                            placeholder="Без категории"
                                            allowClear
                                            options={categoryOptions}
                                        />
                                    </Form.Item>
                                </Col>
                                <Col span={6}>
                                    <Form.Item name="sku" label="Артикул">
                                        <Input />
                                    </Form.Item>
                                </Col>
                                <Col span={6}>
                                    <Form.Item name="unitOfMeasure" label="Ед. измерения">
                                        <Input placeholder="шт." />
                                    </Form.Item>
                                </Col>
                            </Row>

                            <Form.Item name="shortDescription" label="Краткое описание">
                                <Input.TextArea rows={2} />
                            </Form.Item>

                            <Form.Item name="description" label="Полное описание">
                                <TextArea rows={5} />
                            </Form.Item>

                            <Row gutter={16}>
                                <Col span={6}>
                                    <Form.Item name="isActive" label="Активен" valuePropName="checked">
                                        <Switch />
                                    </Form.Item>
                                </Col>
                                <Col span={6}>
                                    <Form.Item name="isFeatured" label="Хит продаж" valuePropName="checked">
                                        <Switch />
                                    </Form.Item>
                                </Col>
                            </Row>

                            <Button
                                type="primary"
                                htmlType="submit"
                                icon={<SaveOutlined />}
                                loading={saveMutation.isPending}
                                size="large"
                            >
                                Сохранить
                            </Button>
                        </Form>
                    </Card>

                    {/* Характеристики */}
                    <Card title="Характеристики" style={{ borderRadius: 12 }}>
                        <Table<AttributeRow>
                            columns={attrColumns}
                            dataSource={product.attributes as AttributeRow[]}
                            rowKey="id"
                            pagination={false}
                            size="small"
                            style={{ marginBottom: 16 }}
                        />

                        <Divider plain>
                            {editingAttrId ? 'Редактировать характеристику' : 'Добавить характеристику'}
                        </Divider>

                        <Form<ProductAttributeRequest>
                            form={attrForm}
                            layout="inline"
                            onFinish={handleAttrSubmit}
                            style={{ gap: 8 }}
                        >
                            <Form.Item
                                name="attributeName"
                                rules={[{ required: true, message: 'Название' }]}
                            >
                                <Input placeholder="Название" style={{ width: 200 }} />
                            </Form.Item>
                            <Form.Item
                                name="attributeValue"
                                rules={[{ required: true, message: 'Значение' }]}
                            >
                                <Input placeholder="Значение" style={{ width: 200 }} />
                            </Form.Item>
                            <Form.Item>
                                <Button
                                    type="primary"
                                    htmlType="submit"
                                    icon={<PlusOutlined />}
                                    loading={addAttrMutation.isPending || updateAttrMutation.isPending}
                                >
                                    {editingAttrId ? 'Сохранить' : 'Добавить'}
                                </Button>
                                {editingAttrId && (
                                    <Button
                                        style={{ marginLeft: 8 }}
                                        onClick={() => {
                                            setEditingAttrId(null);
                                            attrForm.resetFields();
                                        }}
                                    >
                                        Отмена
                                    </Button>
                                )}
                            </Form.Item>
                        </Form>
                    </Card>
                </Col>

                {/* Правая колонка — изображения */}
                <Col xs={24} lg={8}>
                    <Card title="Изображения" style={{ borderRadius: 12 }}>
                        <Upload
                            beforeUpload={(file) => {
                                uploadMutation.mutate(file);
                                return false;
                            }}
                            showUploadList={false}
                            accept="image/*"
                        >
                            <Button
                                icon={<UploadOutlined />}
                                loading={uploadMutation.isPending}
                                block
                                style={{ marginBottom: 16 }}
                            >
                                Загрузить изображение
                            </Button>
                        </Upload>

                        {product.images && product.images.length > 0 ? (
                            <Table<ProductImage>
                                columns={imageColumns}
                                dataSource={[...product.images].sort((a, b) => {
                                    if (a.isPrimary) return -1;
                                    if (b.isPrimary) return 1;
                                    return a.displayOrder - b.displayOrder;
                                })}
                                rowKey="id"
                                pagination={false}
                                size="small"
                            />
                        ) : (
                            <Text type="secondary">Нет изображений</Text>
                        )}
                    </Card>
                </Col>
            </Row>
        </div>
    );
};

export default AdminProductEditPage;