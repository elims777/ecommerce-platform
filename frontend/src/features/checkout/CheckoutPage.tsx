import { useState } from 'react';
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
    Space,
    Table,
    App,
    Steps,
    Result,
} from 'antd';
import {
    ShoppingOutlined,
    EnvironmentOutlined,
    CreditCardOutlined,
    CheckCircleOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import { createOrder } from '@/api/orders';
import {
    DeliveryMethod,
    DeliveryMethodLabels,
    PaymentMethod,
    PaymentMethodLabels,
} from '@/types/order';
import type { CreateOrderRequest } from '@/types/order';
import type { CartItem } from '@/store/cartStore';
import type { AxiosError } from 'axios';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;
const { TextArea } = Input;

/** Форматирует цену в рубли */
const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

/** Форма checkout — расширяет CreateOrderRequest плоскими полями адреса */
interface CheckoutFormValues {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
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
    const { items, getTotalPrice, clearCart } = useCartStore();

    // Следим за выбранным способом доставки для условного показа адреса
    const deliveryMethod = Form.useWatch('deliveryMethod', form);

    const handleSubmit = async (values: CheckoutFormValues) => {
        setLoading(true);
        try {
            // Собираем CreateOrderRequest из плоской формы
            const request: CreateOrderRequest = {
                paymentMethod: values.paymentMethod,
                deliveryMethod: values.deliveryMethod,
                comment: values.comment,
            };

            // Адрес доставки — только если выбрана доставка
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

            const order = await createOrder(request);
            clearCart();
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

    // Колонки для таблицы товаров в сводке
    const columns: ColumnsType<CartItem> = [
        {
            title: 'Товар',
            dataIndex: 'name',
            key: 'name',
        },
        {
            title: 'Кол-во',
            dataIndex: 'quantity',
            key: 'quantity',
            width: 100,
            render: (qty: number, record) =>
                `${qty} ${record.unitOfMeasure || 'шт.'}`,
        },
        {
            title: 'Сумма',
            key: 'total',
            width: 130,
            render: (_, record) => formatPrice(record.price * record.quantity),
        },
    ];

    // Успешное оформление заказа
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

    // Пустая корзина — нечего оформлять
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
                    {/* Левая колонка — форма */}
                    <Col xs={24} lg={14}>
                        {/* Способ доставки */}
                        <Card
                            title={
                                <Space>
                                    <EnvironmentOutlined />
                                    <span>Способ доставки</span>
                                </Space>
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

                            {/* Адрес доставки — показывается только при доставке */}
                            {deliveryMethod === DeliveryMethod.SUPPLIER_DELIVERY && (
                                <>
                                    <Divider plain>
                                        Адрес доставки
                                    </Divider>
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
                                                rules={[
                                                    { required: true, message: 'Укажите дом' },
                                                ]}
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
                                <Space>
                                    <CreditCardOutlined />
                                    <span>Способ оплаты</span>
                                </Space>
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

                    {/* Правая колонка — сводка заказа */}
                    <Col xs={24} lg={10}>
                        <Card title="Ваш заказ" style={{ position: 'sticky', top: 88 }}>
                            <Table<CartItem>
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
                                    {formatPrice(getTotalPrice())}
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