import { useState } from 'react';

import {
    Row,
    Col,
    Typography,
    Spin,
    Tag,
    Button,
    InputNumber,
    Divider,
    Image,
    Descriptions,
    Breadcrumb,
    Space,
    Card,
    Empty,
    App,
} from 'antd';
import {
    ShoppingCartOutlined,
    ShoppingOutlined,
    HomeOutlined,
    ArrowLeftOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { getProductById } from '@/api/products';
import type { ProductImage } from '@/types/product';
import { useCartStore } from '@/store/cartStore';

const { Title, Text, Paragraph } = Typography;

/** Форматирует цену в рубли */
const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

/** Сортирует изображения: primary первым, затем по displayOrder */
const sortImages = (images: ProductImage[]): ProductImage[] =>
    [...images].sort((a, b) => {
        if (a.isPrimary && !b.isPrimary) return -1;
        if (!a.isPrimary && b.isPrimary) return 1;
        return a.displayOrder - b.displayOrder;
    });

const ProductPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const [quantity, setQuantity] = useState(1);
    const addItem = useCartStore((state) => state.addItem);

    const {
        data: product,
        isLoading,
        isError,
    } = useQuery({
        queryKey: ['product', id],
        queryFn: () => getProductById(Number(id)),
        enabled: !!id,
    });

    const handleAddToCart = async () => {
        if (!product) return;
        try {
            await addItem(product.id, quantity);
            messageApi.success(`${product.name} (${quantity} шт.) добавлен в корзину`);
        } catch {
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    if (isLoading) {
        return (
            <div style={{ textAlign: 'center', padding: 120 }}>
                <Spin size="large" description="Загрузка товара..." />
            </div>
        );
    }

    if (isError || !product) {
        return (
            <Empty
                description="Товар не найден"
                style={{ padding: 120 }}
            >
                <Button type="primary" onClick={() => navigate('/')}>
                    Вернуться в каталог
                </Button>
            </Empty>
        );
    }

    const sortedImages = sortImages(product.images || []);
    const inStock = product.stockQuantity > 0;

    return (
        <div>
            {/* Хлебные крошки */}
            <Breadcrumb
                style={{ marginBottom: 24 }}
                items={[
                    {
                        title: (
                            <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>
                <HomeOutlined /> Каталог
              </span>
                        ),
                    },
                    ...(product.categoryName
                        ? [
                            {
                                title: (
                                    <span
                                        onClick={() =>
                                            navigate(`/?category=${product.categoryId}`)
                                        }
                                        style={{ cursor: 'pointer' }}
                                    >
                      {product.categoryName}
                    </span>
                                ),
                            },
                        ]
                        : []),
                    { title: product.name },
                ]}
            />

            {/* Кнопка "Назад" */}
            <Button
                type="link"
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate(-1)}
                style={{ padding: 0, marginBottom: 16 }}
            >
                Назад
            </Button>

            <Row gutter={32}>
                {/* Левая колонка — галерея изображений */}
                <Col xs={24} md={10}>
                    {sortedImages.length > 0 ? (
                        <Image.PreviewGroup>
                            {/* Главное изображение */}
                            <Image
                                src={sortedImages[0].fileUrl}
                                alt={sortedImages[0].altText || product.name}
                                style={{
                                    width: '100%',
                                    maxHeight: 450,
                                    objectFit: 'contain',
                                    borderRadius: 8,
                                    background: '#fafafa',
                                }}
                            />
                            {/* Миниатюры остальных */}
                            {sortedImages.length > 1 && (
                                <Row gutter={[8, 8]} style={{ marginTop: 12 }}>
                                    {sortedImages.slice(1).map((img) => (
                                        <Col span={6} key={img.id}>
                                            <Image
                                                src={img.fileUrl}
                                                alt={img.altText || product.name}
                                                style={{
                                                    width: '100%',
                                                    height: 80,
                                                    objectFit: 'cover',
                                                    borderRadius: 4,
                                                    cursor: 'pointer',
                                                }}
                                            />
                                        </Col>
                                    ))}
                                </Row>
                            )}
                        </Image.PreviewGroup>
                    ) : (
                        <div
                            style={{
                                height: 400,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                background: '#fafafa',
                                borderRadius: 8,
                            }}
                        >
                            <ShoppingOutlined style={{ fontSize: 80, color: '#d9d9d9' }} />
                        </div>
                    )}
                </Col>

                {/* Правая колонка — информация о товаре */}
                <Col xs={24} md={14}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, width: '100%' }}>
                        {/* Название и категория */}
                        <div>
                            <Title level={2} style={{ marginBottom: 8 }}>
                                {product.name}
                            </Title>
                            {product.categoryName && (
                                <Tag color="blue">{product.categoryName}</Tag>
                            )}
                            {product.isFeatured && <Tag color="red">Хит продаж</Tag>}
                        </div>

                        {/* Цена */}
                        <Title level={2} style={{ color: '#1677ff', margin: 0 }}>
                            {formatPrice(product.price)}
                        </Title>

                        {/* Наличие */}
                        <Space>
                            {inStock ? (
                                <>
                                    <CheckCircleOutlined style={{ color: '#52c41a' }} />
                                    <Text type="success">
                                        В наличии ({product.stockQuantity}{' '}
                                        {product.unitOfMeasure || 'шт.'})
                                    </Text>
                                </>
                            ) : (
                                <>
                                    <CloseCircleOutlined style={{ color: '#ff4d4f' }} />
                                    <Text type="danger">Нет в наличии</Text>
                                </>
                            )}
                        </Space>

                        <Divider style={{ margin: '8px 0' }} />

                        {/* Краткое описание */}
                        {product.shortDescription && (
                            <Paragraph type="secondary">{product.shortDescription}</Paragraph>
                        )}

                        {/* Добавление в корзину */}
                        {inStock && (
                            <Space size="middle">
                                <InputNumber
                                    min={1}
                                    max={product.stockQuantity}
                                    value={quantity}
                                    onChange={(val) => setQuantity(val ?? 1)}
                                    size="large"
                                    style={{ width: 120 }}
                                />
                                <Text type="secondary" style={{ marginLeft: 8 }}>
                                    {product.unitOfMeasure || 'шт.'}
                                </Text>
                                <Button
                                    type="primary"
                                    size="large"
                                    icon={<ShoppingCartOutlined />}
                                    onClick={handleAddToCart}
                                >
                                    В корзину
                                </Button>
                            </Space>
                        )}

                        <Divider style={{ margin: '8px 0' }} />

                        {/* Артикул и коды */}
                        <Descriptions
                            column={1}
                            size="small"
                            labelStyle={{ color: '#888', width: 160 }}
                        >
                            {product.sku && (
                                <Descriptions.Item label="Артикул">
                                    {product.sku}
                                </Descriptions.Item>
                            )}
                            {product.externalCode && (
                                <Descriptions.Item label="Код 1С">
                                    {product.externalCode}
                                </Descriptions.Item>
                            )}
                            {product.vatRate != null && (
                                <Descriptions.Item label="НДС">
                                    {product.vatRate}%
                                </Descriptions.Item>
                            )}
                        </Descriptions>
                    </div>
                </Col>
            </Row>

            {/* Полное описание */}
            {product.description && (
                <Card style={{ marginTop: 32 }}>
                    <Title level={4}>Описание</Title>
                    <Paragraph style={{ whiteSpace: 'pre-wrap' }}>
                        {product.description}
                    </Paragraph>
                </Card>
            )}

            {/* Характеристики */}
            {product.attributes && product.attributes.length > 0 && (
                <Card style={{ marginTop: 16 }}>
                    <Title level={4}>Характеристики</Title>
                    <Descriptions column={{ xs: 1, sm: 2 }} bordered size="small">
                        {product.attributes.map((attr) => (
                            <Descriptions.Item label={attr.name} key={attr.id}>
                                {attr.value}
                            </Descriptions.Item>
                        ))}
                    </Descriptions>
                </Card>
            )}
        </div>
    );
};

export default ProductPage;