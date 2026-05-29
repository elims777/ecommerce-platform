import { useState, useEffect } from 'react';
import {
    Row,
    Col,
    Card,
    Form,
    Button,
    Typography,
    App,
    Steps,
    Modal,
    QRCode,
    Result,
} from 'antd';
import {
    ShoppingOutlined,
    EnvironmentOutlined,
    CreditCardOutlined,
    CheckCircleOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import { createOrder, confirmOrder, initiatePayment } from '@/api/orders';
import { getWarehousePoints } from '@/api/warehouse';
import {
    getRecipients,
    getRecipientAddresses,
    createRecipient,
    createRecipientAddress,
    type RecipientDto,
    type RecipientAddressDto,
} from '@/api/recipients';
import { getPaymentSettings } from '@/api/paymentSettings';
import {
    DeliveryMethod,
    PaymentMethod,
} from '@/types/order';
import type { CreateOrderRequest } from '@/types/order';
import type { AxiosError } from 'axios';
import DeliveryStep from './DeliveryStep';
import RecipientStep from './RecipientStep';
import PaymentStep from './PaymentStep';
import SummaryStep from './SummaryStep';

const { Title } = Typography;

interface CheckoutFormValues {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    warehousePointId?: number;
    comment?: string;
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
    const [sbpModal, setSbpModal] = useState<{ orderNumber: string; paymentLink: string } | null>(null);
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
    const user = useAuthStore((state) => state.user);
    const { items, totalAmount, fetchCart, clearCart } = useCartStore();
    const queryClient = useQueryClient();

    const deliveryMethod = Form.useWatch('deliveryMethod', form);

    const [selectedRecipient, setSelectedRecipient] = useState<RecipientDto | null>(null);
    const [selectedAddress, setSelectedAddress] = useState<RecipientAddressDto | null>(null);
    const [manualInput, setManualInput] = useState(false);

    useEffect(() => {
        if (isAuthenticated) fetchCart();
    }, [isAuthenticated, fetchCart]);

    const { data: recipients = [], isLoading: recipientsLoading } = useQuery({
        queryKey: ['recipients'],
        queryFn: getRecipients,
        enabled: isAuthenticated,
    });

    const { data: addresses = [], isLoading: addressesLoading } = useQuery({
        queryKey: ['recipientAddresses', selectedRecipient?.id],
        queryFn: () => getRecipientAddresses(selectedRecipient!.id),
        enabled: !!selectedRecipient,
    });

    const { data: paymentSettings } = useQuery({
        queryKey: ['paymentSettings'],
        queryFn: getPaymentSettings,
        staleTime: 60_000,
    });

    useEffect(() => {
        if (recipients.length === 0) {
            setManualInput(true);
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

    useEffect(() => {
        setSelectedAddress(null);
        if (addresses.length > 0) {
            const defaultAddr = addresses.find((a) => a.isDefault) ?? addresses[0];
            setSelectedAddress(defaultAddr);
        }
    }, [addresses]);

    // Автовыбор метода оплаты
    useEffect(() => {
        if (user?.clientType === 'B2B') {
            form.setFieldValue('paymentMethod', PaymentMethod.INVOICE);
            return;
        }
        if (!paymentSettings) return;
        const { cardEnabled, sbpEnabled } = paymentSettings;
        if (!cardEnabled && !sbpEnabled) {
            form.setFieldValue('paymentMethod', PaymentMethod.INVOICE);
        } else if (cardEnabled && !sbpEnabled) {
            form.setFieldValue('paymentMethod', PaymentMethod.CARD);
        } else if (!cardEnabled && sbpEnabled) {
            form.setFieldValue('paymentMethod', PaymentMethod.SBP);
        }
    }, [paymentSettings, user?.clientType]);

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

    const { data: warehousePoints = [], isLoading: warehouseLoading } = useQuery({
        queryKey: ['warehousePoints'],
        queryFn: getWarehousePoints,
        staleTime: 5 * 60 * 1000,
    });

    const handleSubmit = async (values: CheckoutFormValues) => {
        setLoading(true);
        try {
            // Определяем имя и телефон заказчика
            let customerName: string | undefined;
            let customerPhone: string | undefined;
            if (user?.clientType === 'B2B') {
                customerName = user.companyName ?? undefined;
            } else if (manualInput) {
                customerName = values.newRecipientName;
                customerPhone = values.newRecipientPhone;
            } else if (selectedRecipient) {
                customerName = selectedRecipient.name;
                customerPhone = selectedRecipient.phone;
            }
            if (!customerName && user) {
                customerName = [user.firstname, user.lastname].filter(Boolean).join(' ') || undefined;
            }

            const request: CreateOrderRequest = {
                paymentMethod: values.paymentMethod,
                deliveryMethod: values.deliveryMethod,
                comment: values.comment,
                customerName,
                customerPhone,
                ...(user?.clientType === 'B2B' ? {
                    companyName: user.companyName ?? undefined,
                    inn: user.inn ?? undefined,
                } : {}),
            };

            if (values.deliveryMethod === DeliveryMethod.SUPPLIER_DELIVERY) {
                if (manualInput) {
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
            messageApi.success('Заказ успешно оформлен!');

            const isB2C = user?.clientType !== 'B2B';

            if (!isB2C) {
                navigate('/orders');
                return;
            }

            // B2C: confirmOrder обязателен перед оплатой (отправляет в 1С, меняет статус CREATED → PROCESSING)
            await confirmOrder(order.id);

            if (values.paymentMethod === PaymentMethod.CARD || values.paymentMethod === PaymentMethod.SBP) {
                const resp = await initiatePayment(order.id);
                if (values.paymentMethod === PaymentMethod.CARD && resp.paymentLink) {
                    window.location.href = resp.paymentLink;
                    return;
                }
                if (values.paymentMethod === PaymentMethod.SBP && resp.paymentLink) {
                    setSbpModal({ orderNumber: order.orderNumber, paymentLink: resp.paymentLink });
                    return;
                }
            }

            // INVOICE и CASH_ON_DELIVERY — просто переходим на /orders
            navigate('/orders');
        } catch (error) {
            const axiosError = error as AxiosError<{ message?: string }>;
            const errorMessage =
                axiosError.response?.data?.message || 'Ошибка при оформлении заказа. Попробуйте позже.';
            messageApi.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    const handleRecipientChange = (id: number) => {
        const r = recipients.find((r) => r.id === id) ?? null;
        setSelectedRecipient(r);
        setSelectedAddress(null);
    };

    const handleAddressChange = (id: number) => {
        const a = addresses.find((a) => a.id === id) ?? null;
        setSelectedAddress(a);
    };

    const handleSwitchToSaved = () => {
        setManualInput(false);
        form.resetFields([
            'newRecipientName', 'newRecipientPhone', 'newAddressLabel',
            'newCity', 'newStreet', 'newBuilding', 'newApartment', 'newPostalCode',
        ]);
    };

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

    const isB2C = user?.clientType !== 'B2B';

    return (
        <div>
            <Modal
                open={!!sbpModal}
                onCancel={() => { setSbpModal(null); navigate('/orders'); }}
                footer={[
                    <Button key="link" onClick={() => { window.location.href = sbpModal!.paymentLink; }}>
                        Открыть ссылку СБП
                    </Button>,
                    <Button key="orders" type="primary" onClick={() => { setSbpModal(null); navigate('/orders'); }}>
                        Мои заказы
                    </Button>,
                ]}
                title={`Оплата заказа ${sbpModal?.orderNumber}`}
                centered
            >
                <div style={{ textAlign: 'center', padding: '16px 0' }}>
                    <div style={{ marginBottom: 16, fontSize: 14, color: 'rgba(0,0,0,0.45)' }}>
                        Отсканируйте QR-кодом в банковском приложении
                    </div>
                    {sbpModal && <QRCode value={sbpModal.paymentLink} size={220} />}
                    <div style={{ marginTop: 12, fontSize: 12, color: 'rgba(0,0,0,0.35)' }}>
                        Сумма уже указана — вводить вручную не нужно
                    </div>
                </div>
            </Modal>

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
                        <Card
                            title={
                                <span>
                                    <EnvironmentOutlined /> Способ доставки
                                </span>
                            }
                            style={{ marginBottom: 16 }}
                        >
                            <DeliveryStep
                                deliveryMethod={deliveryMethod}
                                warehousePoints={warehousePoints}
                                warehouseLoading={warehouseLoading}
                                recipientsCount={recipients.length}
                                onDeliveryMethodChange={(count) => setManualInput(count === 0)}
                            />
                            <RecipientStep
                                deliveryMethod={deliveryMethod}
                                recipients={recipients}
                                recipientsLoading={recipientsLoading}
                                addresses={addresses}
                                addressesLoading={addressesLoading}
                                selectedRecipient={selectedRecipient}
                                selectedAddress={selectedAddress}
                                manualInput={manualInput}
                                onRecipientChange={handleRecipientChange}
                                onAddressChange={handleAddressChange}
                                onSwitchToManual={() => setManualInput(true)}
                                onSwitchToSaved={handleSwitchToSaved}
                            />
                        </Card>

                        <Card
                            title={
                                <span>
                                    <CreditCardOutlined /> Способ оплаты
                                </span>
                            }
                            style={{ marginBottom: 16 }}
                        >
                            <PaymentStep
                                settings={paymentSettings}
                                isB2C={isB2C}
                            />
                        </Card>
                    </Col>

                    <Col xs={24} lg={10}>
                        <Card title="Ваш заказ" style={{ position: 'sticky', top: 88 }}>
                            <SummaryStep
                                items={items}
                                totalAmount={totalAmount}
                                loading={loading}
                            />
                        </Card>
                    </Col>
                </Row>
            </Form>
        </div>
    );
};

export default CheckoutPage;
