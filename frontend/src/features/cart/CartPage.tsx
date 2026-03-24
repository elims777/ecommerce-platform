import {
    Table,
    InputNumber,
    Button,
    Typography,
    Empty,
    Space,
    Card,
    Popconfirm,
    Image,
    App,
} from 'antd';
import {
    DeleteOutlined,
    ShoppingOutlined,
    ClearOutlined,
    ArrowLeftOutlined,
    ShoppingCartOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import type { CartItem } from '@/store/cartStore';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

/** Форматирует цену в рубли */
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
    const { items, updateQuantity, removeItem, clearCart, getTotalPrice } =
        useCartStore();

    const handleRemove = (item: CartItem) => {
        removeItem(item.productId);
        messageApi.success(`${item.name} удалён из корзины`);
    };

    const handleClear = () => {
        clearCart();
        messageApi.success('Корзина очищена');
    };

    // Определение колонок таблицы
    const columns: ColumnsType<CartItem> = [
        {
            title: 'Товар',
            dataIndex: 'name',
            key: 'name',
            render: (name: string, record) => (
                <Space>
                    {record.imageUrl ? (
                        <Image
                            src={record.imageUrl}
                            alt={name}
                            width={60}
                            height={60}
                            style={{ objectFit: 'contain', borderRadius: 4 }}
                            preview={false}
                        />
                    ) : (
                        <div
                            style={{
                                width: 60,
                                height: 60,
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                background: '#fafafa',
                                borderRadius: 4,
                            }}
                        >
                            <ShoppingOutlined style={{ fontSize: 24, color: '#d9d9d9' }} />
                        </div>
                    )}
                    <Text
                        style={{ cursor: 'pointer', color: '#1677ff' }}
                        onClick={() => navigate(`/products/${record.productId}`)}
                    >
                        {name}
                    </Text>
                </Space>
            ),
        },
        {
            title: 'Цена',
            dataIndex: 'price',
            key: 'price',
            width: 150,
            render: (price: number) => <Text>{formatPrice(price)}</Text>,
        },
        {
            title: 'Количество',
            dataIndex: 'quantity',
            key: 'quantity',
            width: 180,
            render: (quantity: number, record) => (
                <InputNumber
                    min={1}
                    max={record.maxStock}
                    value={quantity}
                    onChange={(val) => updateQuantity(record.productId, val ?? 1)}
                    addonAfter={record.unitOfMeasure || 'шт.'}
                />
            ),
        },
        {
            title: 'Сумма',
            key: 'total',
            width: 150,
            render: (_, record) => (
                <Text strong>{formatPrice(record.price * record.quantity)}</Text>
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

    // Пустая корзина
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

            <Table<CartItem>
                columns={columns}
                dataSource={items}
                rowKey="productId"
                pagination={false}
                style={{ marginBottom: 24 }}
            />

            {/* Итого и кнопки */}
            <Card>
                <div
                    style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                    }}
                >
                    <Button
                        icon={<ArrowLeftOutlined />}
                        onClick={() => navigate('/')}
                    >
                        Продолжить покупки
                    </Button>

                    <Space size="large" align="center">
                        <div>
                            <Text type="secondary">Итого: </Text>
                            <Title level={3} style={{ display: 'inline', color: '#1677ff' }}>
                                {formatPrice(getTotalPrice())}
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