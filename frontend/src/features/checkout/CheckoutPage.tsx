import { useState, useEffect } from 'react';
import {
    Row,
    Col,
    Card,
    Form,
    Input,
    Select,
    Radio,
    Button,
    Typography,
    Divider,
    Table,
    App,
    Steps,
    Result,
    Spin,
} from 'antd';
import {
    ShoppingOutlined,
    EnvironmentOutlined,
    CreditCardOutlined,
    CheckCircleOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import { createOrder } from '@/api/orders';
import { getWarehousePoints } from '@/api/warehouse';
import {
    DeliveryMethod,
    DeliveryMethodLabels,
    PaymentMethod,
    PaymentMethodLabels,
} from '@/types/order';
import type { CreateOrderRequest } from '@/types/order';
import type { CartItemDto } from '@/api/cart';
import type { AxiosError } from 'axios';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;
const { TextArea } = Input;

/** Форматирует цену */
const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

interface CheckoutFormValues {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    warehousePointId?: number;
    city?: string;
    street?: string;
    building?: string;
    apartment?: string;
    postalCode?: string;
    phone?: string;
    recipientName?: string;
    comment?: string;
}

const CheckoutPage = () => {
    const [form] = Form.useForm<CheckoutFormValues>();
    const [loading, setLoading] = useState(false);
    const [orderNumber, setOrderNumber] = useState<string | null>(null);
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
    const user = useAuthStore((state) => state.user);
    const { items, totalAmount, fetchCart, clearCart } = useCartStore();

    const deliveryMethod = Form.useWatch('deliveryMethod', form);

    // Загружаем серверную корзину
    useEffect(() => {
        if (isAuthenticated) {
            fetchCart();
        }
    }, [isAuthenticated, fetchCart]);

    // Автозаполнение ФИО получателя из профиля
    useEffect(() => {
        if (user) {
            const fullName = [user.lastname, user.firstname, user.surname]
                .filter(Boolean)
                .join(' ');
            form.setFieldValue('recipientName', fullName);
        }
    }, [user, form]);

    // Загрузка точек самовывоза
    const { data: warehousePoints = [], isLoading: warehouseLoading } = useQuery({
        queryKey: ['warehousePoints'],
        queryFn: getWarehousePoints,
        staleTime: 5 * 60 * 1000,
    });

    const handleSubmit = async (values: CheckoutFormValues) => {
        setLoading(true);
        try {
            const request: CreateOrderRequest = {
                paymentMethod: values.paymentMethod,
                deliveryMethod: values.deliveryMethod,
                comment: values.comment,
            };

            if (values.deliveryMethod === DeliveryMethod.SUPPLIER_DELIVERY) {
                request.deliveryAddress = {
                    city: values.city!,
                    street: values.street!,
                    building: values.building!,
                    apartment: values.apartment,
                    postalCode: values.postalCode,
                    phone: values.phone!,
                    recipientName: values.recipientName!,
                };
            }

            if (values.deliveryMethod === DeliveryMethod.PICKUP && values.warehousePointId) {
                request.warehousePointId = values.warehousePointId;
            }

            const order = await createOrder(request);
            await clearCart();
            setOrderNumber(order.orderNumber);
        } catch (error) {
            const axiosError = error as AxiosError<{ message?: string }>;
            const errorMessage =
                axiosError.response?.data?.message ||
                'Ошибка при оформлении заказа. Попробуйте позже.';
            messageApi.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    const columns: ColumnsType<CartItemDto> = [
        {
            title: 'Товар',
            dataIndex: 'productName',
            key: 'productName',
        },
        {
            title: 'Кол-во',
            dataIndex: 'quantity',
            key: 'quantity',
            width: 80,
        },
        {
            title: 'Сумма',
            dataIndex: 'subtotal',
            key: 'subtotal',
            width: 130,
            render: (subtotal: number) => formatPrice(subtotal),
        },
    ];

    if (orderNumber) {
        return (
            <Result
                status="success"
                icon={<CheckCircleOutlined />}
                title="Заказ успешно оформлен!"
                subTitle={`Номер заказа: ${orderNumber}`}
                extra={[
                    <Button
                        type="primary"
                        key="orders"
                        onClick={() => navigate('/orders')}
                    >
                        Мои заказы
                    </Button>,
                    <Button key="catalog" onClick={() => navigate('/')}>
                        Вернуться в каталог
                    </Button>,
                ]}
            />
        );
    }

    if (items.length === 0) {
        return (
            <Result
                icon={<ShoppingOutlined style={{ color: '#d9d9d9' }} />}
                title="Корзина пуста"
                subTitle="Добавьте товары в корзину перед оформлением заказа"
                extra={
                    <Button type="primary" onClick={() => navigate('/')}>
                        В каталог
                    </Button>
                }
            />
        );
    }

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>
                Оформление заказа
            </Title>

            <Steps
                current={0}
                style={{ marginBottom: 32 }}
                items={[
                    { title: 'Оформление', icon: <ShoppingOutlined /> },
                    { title: 'Оплата', icon: <CreditCardOutlined /> },
                    { title: 'Готово', icon: <CheckCircleOutlined /> },
                ]}
            />

            <Form<CheckoutFormValues>
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                initialValues={{
                    deliveryMethod: DeliveryMethod.PICKUP,
                    paymentMethod: PaymentMethod.CARD,
                }}
            >
                <Row gutter={24}>
                    <Col xs={24} lg={14}>
                        {/* Способ доставки */}
                        <Card
                            title={
                                <span>
                  <EnvironmentOutlined /> Способ доставки
                </span>
                            }
                            style={{ marginBottom: 16 }}
                        >
                            <Form.Item
                                name="deliveryMethod"
                                rules={[
                                    { required: true, message: 'Выберите способ доставки' },
                                ]}
                            >
                                <Radio.Group>
                                    {Object.entries(DeliveryMethodLabels).map(([key, label]) => (
                                        <Radio.Button key={key} value={key}>
                                            {label}
                                        </Radio.Button>
                                    ))}
                                </Radio.Group>
                            </Form.Item>

                            {/* Точки самовывоза */}
                            {deliveryMethod === DeliveryMethod.PICKUP && (
                                <>
                                    <Divider plain>Точка самовывоза</Divider>
                                    {warehouseLoading ? (
                                        <div style={{ textAlign: 'center', padding: 16 }}>
                                            <Spin />
                                        </div>
                                    ) : warehousePoints.length > 0 ? (
                                        <Form.Item
                                            name="warehousePointId"
                                            rules={[
                                                { required: true, message: 'Выберите точку самовывоза' },
                                            ]}
                                        >
                                            <Select
                                                placeholder="Выберите точку самовывоза"
                                                size="large"
                                            >
                                                {warehousePoints.map((point) => (
                                                    <Select.Option key={point.id} value={point.id}>
                                                        <div>
                                                            <Text strong>{point.name}</Text>
                                                            <br />
                                                            <Text type="secondary">
                                                                {point.city}, {point.street}, д. {point.building}
                                                                {point.workingHours && ` | ${point.workingHours}`}
                                                            </Text>
                                                        </div>
                                                    </Select.Option>
                                                ))}
                                            </Select>
                                        </Form.Item>
                                    ) : (
                                        <Text type="secondary">
                                            Нет доступных точек самовывоза. Выберите доставку.
                                        </Text>
                                    )}
                                </>
                            )}

                            {/* Адрес доставки */}
                            {deliveryMethod === DeliveryMethod.SUPPLIER_DELIVERY && (
                                <>
                                    <Divider plain>Адрес доставки</Divider>
                                    <Row gutter={12}>
                                        <Col span={12}>
                                            <Form.Item
                                                name="recipientName"
                                                label="Получатель"
                                                rules={[
                                                    { required: true, message: 'Укажите получателя' },
                                                ]}
                                            >
                                                <Input placeholder="ФИО получателя" />
                                            </Form.Item>
                                        </Col>
                                        <Col span={12}>
                                            <Form.Item
                                                name="phone"
                                                label="Телефон"
                                                rules={[
                                                    { required: true, message: 'Укажите телефон' },
                                                ]}
                                            >
                                                <Input placeholder="+7 (___) ___-__-__" />
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                    <Row gutter={12}>
                                        <Col span={12}>
                                            <Form.Item
                                                name="city"
                                                label="Город"
                                                rules={[{ required: true, message: 'Укажите город' }]}
                                            >
                                                <Input placeholder="Москва" />
                                            </Form.Item>
                                        </Col>
                                        <Col span={12}>
                                            <Form.Item name="postalCode" label="Индекс">
                                                <Input placeholder="101000" />
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                    <Row gutter={12}>
                                        <Col span={12}>
                                            <Form.Item
                                                name="street"
                                                label="Улица"
                                                rules={[{ required: true, message: 'Укажите улицу' }]}
                                            >
                                                <Input placeholder="ул. Примерная" />
                                            </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                            <Form.Item
                                                name="building"
                                                label="Дом"
                                                rules={[{ required: true, message: 'Укажите дом' }]}
                                            >
                                                <Input placeholder="12" />
                                            </Form.Item>
                                        </Col>
                                        <Col span={6}>
                                            <Form.Item name="apartment" label="Квартира/офис">
                                                <Input placeholder="45" />
                                            </Form.Item>
                                        </Col>
                                    </Row>
                                </>
                            )}
                        </Card>

                        {/* Способ оплаты */}
                        <Card
                            title={
                                <span>
                  <CreditCardOutlined /> Способ оплаты
                </span>
                            }
                            style={{ marginBottom: 16 }}
                        >
                            <Form.Item
                                name="paymentMethod"
                                rules={[{ required: true, message: 'Выберите способ оплаты' }]}
                            >
                                <Select size="large">
                                    {Object.entries(PaymentMethodLabels).map(([key, label]) => (
                                        <Select.Option key={key} value={key}>
                                            {label}
                                        </Select.Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Card>

                        {/* Комментарий */}
                        <Card style={{ marginBottom: 16 }}>
                            <Form.Item name="comment" label="Комментарий к заказу">
                                <TextArea
                                    rows={3}
                                    placeholder="Дополнительные пожелания по заказу..."
                                    maxLength={500}
                                    showCount
                                />
                            </Form.Item>
                        </Card>
                    </Col>

                    {/* Сводка заказа */}
                    <Col xs={24} lg={10}>
                        <Card title="Ваш заказ" style={{ position: 'sticky', top: 88 }}>
                            <Table<CartItemDto>
                                columns={columns}
                                dataSource={items}
                                rowKey="productId"
                                pagination={false}
                                size="small"
                                style={{ marginBottom: 16 }}
                            />

                            <Divider />

                            <div
                                style={{
                                    display: 'flex',
                                    justifyContent: 'space-between',
                                    alignItems: 'center',
                                    marginBottom: 16,
                                }}
                            >
                                <Text style={{ fontSize: 16 }}>Итого:</Text>
                                <Title level={3} style={{ margin: 0, color: '#1677ff' }}>
                                    {formatPrice(totalAmount)}
                                </Title>
                            </div>

                            <Button
                                type="primary"
                                htmlType="submit"
                                size="large"
                                loading={loading}
                                block
                            >
                                Подтвердить заказ
                            </Button>
                        </Card>
                    </Col>
                </Row>
            </Form>
        </div>
    );
};

export default CheckoutPage;