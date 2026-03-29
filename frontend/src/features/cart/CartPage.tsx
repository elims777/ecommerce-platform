import { useEffect } from 'react';
import {
    Table,
    InputNumber,
    Button,
    Typography,
    Empty,
    Space,
    Card,
    Popconfirm,
    Spin,
    App,
} from 'antd';
import {
    DeleteOutlined,
    ClearOutlined,
    ArrowLeftOutlined,
    ShoppingCartOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import type { CartItemDto } from '@/api/cart';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

const CartPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const {
        items,
        totalAmount,
        isLoading,
        fetchCart,
        updateQuantity,
        removeItem,
        clearCart,
    } = useCartStore();

    useEffect(() => {
        fetchCart();
    }, [fetchCart]);

    const handleRemove = async (item: CartItemDto) => {
        try {
            await removeItem(item.productId);
            messageApi.success(`${item.productName} удалён из корзины`);
        } catch {
            messageApi.error('Ошибка при удалении товара');
        }
    };

    const handleUpdateQuantity = async (productId: number, quantity: number) => {
        try {
            await updateQuantity(productId, quantity);
        } catch {
            messageApi.error('Ошибка при обновлении количества');
        }
    };

    const handleClear = async () => {
        try {
            await clearCart();
            messageApi.success('Корзина очищена');
        } catch {
            messageApi.error('Ошибка при очистке корзины');
        }
    };

    const columns: ColumnsType<CartItemDto> = [
        {
            title: 'Товар',
            dataIndex: 'productName',
            key: 'productName',
            render: (name: string, record) => (
                <Text
                    style={{ cursor: 'pointer', color: '#1677ff' }}
                    onClick={() => navigate(`/products/${record.productId}`)}
                >
                    {name}
                </Text>
            ),
        },
        {
            title: 'Цена',
            dataIndex: 'price',
            key: 'price',
            width: 150,
            render: (price: number) => formatPrice(price),
        },
        {
            title: 'Количество',
            dataIndex: 'quantity',
            key: 'quantity',
            width: 150,
            render: (quantity: number, record) => (
                <InputNumber
                    min={1}
                    value={quantity}
                    onChange={(val) =>
                        handleUpdateQuantity(record.productId, val ?? 1)
                    }
                />
            ),
        },
        {
            title: 'Сумма',
            dataIndex: 'subtotal',
            key: 'subtotal',
            width: 150,
            render: (subtotal: number) => (
                <Text strong>{formatPrice(subtotal)}</Text>
            ),
        },
        {
            title: '',
            key: 'actions',
            width: 60,
            render: (_, record) => (
                <Popconfirm
                    title="Удалить товар из корзины?"
                    onConfirm={() => handleRemove(record)}
                    okText="Да"
                    cancelText="Нет"
                >
                    <Button type="text" danger icon={<DeleteOutlined />} />
                </Popconfirm>
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

    if (items.length === 0) {
        return (
            <Empty
                image={<ShoppingCartOutlined style={{ fontSize: 80, color: '#d9d9d9' }} />}
                description="Корзина пуста"
                style={{ padding: 120 }}
            >
                <Button type="primary" onClick={() => navigate('/')}>
                    Перейти в каталог
                </Button>
            </Empty>
        );
    }

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
                    Корзина
                </Title>
                <Popconfirm
                    title="Очистить корзину?"
                    description="Все товары будут удалены"
                    onConfirm={handleClear}
                    okText="Да, очистить"
                    cancelText="Отмена"
                >
                    <Button icon={<ClearOutlined />} danger>
                        Очистить корзину
                    </Button>
                </Popconfirm>
            </div>

            <Table<CartItemDto>
                columns={columns}
                dataSource={items}
                rowKey="productId"
                pagination={false}
                style={{ marginBottom: 24 }}
            />

            <Card>
                <div
                    style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                    }}
                >
                    <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/')}>
                        Продолжить покупки
                    </Button>

                    <Space size="large" align="center">
                        <div>
                            <Text type="secondary">Итого: </Text>
                            <Title level={3} style={{ display: 'inline', color: '#1677ff' }}>
                                {formatPrice(totalAmount)}
                            </Title>
                        </div>
                        <Button
                            type="primary"
                            size="large"
                            onClick={() => navigate('/checkout')}
                        >
                            Оформить заказ
                        </Button>
                    </Space>
                </div>
            </Card>
        </div>
    );
};

export default CartPage;