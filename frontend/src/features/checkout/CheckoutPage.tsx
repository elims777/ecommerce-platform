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
    Space,
} from 'antd';
import {
    ShoppingOutlined,
    EnvironmentOutlined,
    CreditCardOutlined,
    CheckCircleOutlined,
    PlusOutlined,
    UserOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import { createOrder } from '@/api/orders';
import { getWarehousePoints } from '@/api/warehouse';
import {
    getRecipients,
    getRecipientAddresses,
    createRecipient,
    createRecipientAddress,
    type RecipientDto,
    type RecipientAddressDto,
} from '@/api/recipients';
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
    comment?: string;
    // Поля нового получателя (показываются только в режиме ручного ввода)
    newRecipientName?: string;
    newRecipientPhone?: string;
    newAddressLabel?: string;
    newCity?: string;
    newStreet?: string;
    newBuilding?: string;
    newApartment?: string;
    newPostalCode?: string;
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
    const queryClient = useQueryClient();

    const deliveryMethod = Form.useWatch('deliveryMethod', form);

    // Выбранный получатель и адрес
    const [selectedRecipient, setSelectedRecipient] = useState<RecipientDto | null>(null);
    const [selectedAddress, setSelectedAddress] = useState<RecipientAddressDto | null>(null);
    // Режим ручного ввода нового получателя/адреса
    const [manualInput, setManualInput] = useState(false);

    useEffect(() => {
        if (isAuthenticated) fetchCart();
    }, [isAuthenticated, fetchCart]);

    // Список получателей
    const { data: recipients = [], isLoading: recipientsLoading } = useQuery({
        queryKey: ['recipients'],
        queryFn: getRecipients,
        enabled: isAuthenticated,
    });

    // Адреса выбранного получателя
    const { data: addresses = [], isLoading: addressesLoading } = useQuery({
        queryKey: ['recipientAddresses', selectedRecipient?.id],
        queryFn: () => getRecipientAddresses(selectedRecipient!.id),
        enabled: !!selectedRecipient,
    });

    // Автовыбор получателя по умолчанию при загрузке
    useEffect(() => {
        if (recipients.length === 0) {
            // Нет получателей — сразу показываем форму ввода
            setManualInput(true);
            // Автозаполняем имя из профиля
            if (user) {
                const fullName = [user.lastname, user.firstname, user.surname]
                    .filter(Boolean)
                    .join(' ');
                form.setFieldValue('newRecipientName', fullName);
            }
        } else {
            const defaultRecipient = recipients.find((r) => r.isDefault) ?? recipients[0];
            setSelectedRecipient(defaultRecipient);
        }
    }, [recipients, user, form]);

    // Автовыбор адреса по умолчанию при смене получателя
    useEffect(() => {
        setSelectedAddress(null);
        if (addresses.length > 0) {
            const defaultAddr = addresses.find((a) => a.isDefault) ?? addresses[0];
            setSelectedAddress(defaultAddr);
        }
    }, [addresses]);

    // Мутации для создания нового получателя и адреса
    const createRecipientMutation = useMutation({
        mutationFn: createRecipient,
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['recipients'] }),
    });

    const createAddressMutation = useMutation({
        mutationFn: ({ recipientId, data }: { recipientId: number; data: Parameters<typeof createRecipientAddress>[1] }) =>
            createRecipientAddress(recipientId, data),
        onSuccess: (_, { recipientId }) =>
            queryClient.invalidateQueries({ queryKey: ['recipientAddresses', recipientId] }),
    });

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
                if (manualInput) {
                    // Режим ручного ввода — сначала создаём получателя и адрес
                    const newRecipient = await createRecipientMutation.mutateAsync({
                        name: values.newRecipientName!,
                        phone: values.newRecipientPhone!,
                    });
                    await createAddressMutation.mutateAsync({
                        recipientId: newRecipient.id,
                        data: {
                            label: values.newAddressLabel || 'Основной',
                            city: values.newCity!,
                            street: values.newStreet!,
                            building: values.newBuilding!,
                            apartment: values.newApartment,
                            postalCode: values.newPostalCode,
                        },
                    });
                    request.deliveryAddress = {
                        city: values.newCity!,
                        street: values.newStreet!,
                        building: values.newBuilding!,
                        apartment: values.newApartment,
                        postalCode: values.newPostalCode,
                        phone: values.newRecipientPhone!,
                        recipientName: values.newRecipientName!,
                    };
                } else {
                    // Режим выбора из существующих
                    if (!selectedRecipient || !selectedAddress) {
                        messageApi.error('Выберите получателя и адрес доставки');
                        setLoading(false);
                        return;
                    }
                    request.deliveryAddress = {
                        city: selectedAddress.city,
                        street: selectedAddress.street,
                        building: selectedAddress.building,
                        apartment: selectedAddress.apartment,
                        postalCode: selectedAddress.postalCode,
                        phone: selectedRecipient.phone,
                        recipientName: selectedRecipient.name,
                    };
                }
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
                axiosError.response?.data?.message || 'Ошибка при оформлении заказа. Попробуйте позже.';
            messageApi.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    const columns: ColumnsType<CartItemDto> = [
        { title: 'Товар', dataIndex: 'productName', key: 'productName' },
        { title: 'Кол-во', dataIndex: 'quantity', key: 'quantity', width: 80 },
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
                    <Button type="primary" key="orders" onClick={() => navigate('/orders')}>
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

    // Секция выбора/ввода получателя и адреса (только при SUPPLIER_DELIVERY)
    const renderRecipientSection = () => {
        if (deliveryMethod !== DeliveryMethod.SUPPLIER_DELIVERY) return null;

        return (
            <>
                {/* @ts-ignore */}
                <Divider orientation="left" plain>
                    Получатель и адрес доставки
                </Divider>

                {recipientsLoading ? (
                    <div style={{ textAlign: 'center', padding: 16 }}>
                        <Spin />
                    </div>
                ) : !manualInput ? (
                    // Режим выбора из существующих
                    <>
                        <Form.Item label="Получатель">
                            <Select
                                value={selectedRecipient?.id}
                                onChange={(id) => {
                                    const r = recipients.find((r) => r.id === id) ?? null;
                                    setSelectedRecipient(r);
                                    setSelectedAddress(null);
                                }}
                                placeholder="Выберите получателя"
                                suffixIcon={<UserOutlined />}
                                size="large"
                            >
                                {recipients.map((r) => (
                                    <Select.Option key={r.id} value={r.id}>
                                        <Space>
                                            <Text strong>{r.name}</Text>
                                            <Text type="secondary">{r.phone}</Text>
                                            {r.isDefault && (
                                                <Text type="secondary" style={{ fontSize: 12 }}>
                                                    (по умолчанию)
                                                </Text>
                                            )}
                                        </Space>
                                    </Select.Option>
                                ))}
                            </Select>
                        </Form.Item>

                        {selectedRecipient && (
                            <Form.Item label="Адрес доставки">
                                {addressesLoading ? (
                                    <Spin size="small" />
                                ) : addresses.length === 0 ? (
                                    <Text type="secondary">
                                        У этого получателя нет сохранённых адресов.{' '}
                                        <Button
                                            type="link"
                                            style={{ padding: 0 }}
                                            onClick={() => setManualInput(true)}
                                        >
                                            Добавить адрес
                                        </Button>
                                    </Text>
                                ) : (
                                    <Select
                                        value={selectedAddress?.id}
                                        onChange={(id) => {
                                            const a = addresses.find((a) => a.id === id) ?? null;
                                            setSelectedAddress(a);
                                        }}
                                        placeholder="Выберите адрес"
                                        suffixIcon={<EnvironmentOutlined />}
                                        size="large"
                                    >
                                        {addresses.map((a) => (
                                            <Select.Option key={a.id} value={a.id}>
                                                <Space>
                                                    <Text strong>{a.label}</Text>
                                                    <Text type="secondary">
                                                        {a.city}, {a.street}, д. {a.building}
                                                        {a.apartment ? `, кв. ${a.apartment}` : ''}
                                                    </Text>
                                                </Space>
                                            </Select.Option>
                                        ))}
                                    </Select>
                                )}
                            </Form.Item>
                        )}

                        <Button
                            type="dashed"
                            icon={<PlusOutlined />}
                            onClick={() => setManualInput(true)}
                            block
                        >
                            Новый получатель и адрес
                        </Button>
                    </>
                ) : (
                    // Режим ручного ввода нового получателя
                    <>
                        <Row gutter={12}>
                            <Col span={12}>
                                <Form.Item
                                    name="newRecipientName"
                                    label="ФИО получателя"
                                    rules={[{ required: true, message: 'Укажите получателя' }]}
                                >
                                    <Input placeholder="Иванов Иван Иванович" />
                                </Form.Item>
                            </Col>
                            <Col span={12}>
                                <Form.Item
                                    name="newRecipientPhone"
                                    label="Телефон"
                                    rules={[
                                        { required: true, message: 'Укажите телефон' },
                                        {
                                            pattern: /^\+?[0-9]{11}$/,
                                            message: 'Введите 11 цифр',
                                        },
                                    ]}
                                >
                                    <Input placeholder="+79001234567" />
                                </Form.Item>
                            </Col>
                        </Row>

                        <Row gutter={12}>
                            <Col span={8}>
                                <Form.Item name="newAddressLabel" label="Название адреса">
                                    <Input placeholder="Офис, склад, дом..." />
                                </Form.Item>
                            </Col>
                            <Col span={10}>
                                <Form.Item
                                    name="newCity"
                                    label="Город"
                                    rules={[{ required: true, message: 'Укажите город' }]}
                                >
                                    <Input placeholder="Москва" />
                                </Form.Item>
                            </Col>
                            <Col span={6}>
                                <Form.Item name="newPostalCode" label="Индекс">
                                    <Input placeholder="101000" />
                                </Form.Item>
                            </Col>
                        </Row>

                        <Row gutter={12}>
                            <Col span={12}>
                                <Form.Item
                                    name="newStreet"
                                    label="Улица"
                                    rules={[{ required: true, message: 'Укажите улицу' }]}
                                >
                                    <Input placeholder="ул. Примерная" />
                                </Form.Item>
                            </Col>
                            <Col span={6}>
                                <Form.Item
                                    name="newBuilding"
                                    label="Дом"
                                    rules={[{ required: true, message: 'Укажите дом' }]}
                                >
                                    <Input placeholder="12" />
                                </Form.Item>
                            </Col>
                            <Col span={6}>
                                <Form.Item name="newApartment" label="Квартира/офис">
                                    <Input placeholder="45" />
                                </Form.Item>
                            </Col>
                        </Row>

                        {/* Кнопка "назад" только если есть сохранённые получатели */}
                        {recipients.length > 0 && (
                            <Button
                                type="link"
                                style={{ padding: 0, marginBottom: 8 }}
                                onClick={() => {
                                    setManualInput(false);
                                    form.resetFields([
                                        'newRecipientName', 'newRecipientPhone', 'newAddressLabel',
                                        'newCity', 'newStreet', 'newBuilding', 'newApartment', 'newPostalCode',
                                    ]);
                                }}
                            >
                                ← Выбрать из сохранённых
                            </Button>
                        )}
                    </>
                )}
            </>
        );
    };

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
                                rules={[{ required: true, message: 'Выберите способ доставки' }]}
                            >
                                <Radio.Group
                                    onChange={() => {
                                        // Сбрасываем состояние при смене способа доставки
                                        setManualInput(recipients.length === 0);
                                    }}
                                >
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
                                    {/* @ts-ignore */}
                                    <Divider orientation="left" plain>
                                        Точка самовывоза
                                    </Divider>
                                    {warehouseLoading ? (
                                        <div style={{ textAlign: 'center', padding: 16 }}>
                                            <Spin />
                                        </div>
                                    ) : warehousePoints.length > 0 ? (
                                        <Form.Item
                                            name="warehousePointId"
                                            rules={[{ required: true, message: 'Выберите точку самовывоза' }]}
                                        >
                                            <Select placeholder="Выберите точку самовывоза" size="large">
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

                            {/* Секция получателя и адреса */}
                            {renderRecipientSection()}
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