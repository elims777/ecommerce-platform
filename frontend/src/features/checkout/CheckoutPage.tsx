import { useState, useEffect } from 'react';
import {
    Row,
    Col,
    Card,
    Form,
    Typography,
    App,
    Steps,
    Result,
} from 'antd';
import { NavLink } from '@/components/navigation';
import {
    ShoppingOutlined,
    EnvironmentOutlined,
    CheckCircleOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import { createOrder, confirmOrder } from '@/api/orders';
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
    PaymentMethod,
} from '@/types/order';
import type { CreateOrderRequest } from '@/types/order';
import type { AxiosError } from 'axios';
import DeliveryStep from './DeliveryStep';
import RecipientStep from './RecipientStep';
import SummaryStep from './SummaryStep';

const { Title } = Typography;

interface CheckoutFormValues {
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
                paymentMethod: PaymentMethod.INVOICE,
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
            await confirmOrder(order.id);
            await clearCart();
            messageApi.success('Заказ успешно оформлен!');

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
                    <NavLink to="/" variant="button-primary">
                        В каталог
                    </NavLink>
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
                    { title: 'Готово', icon: <CheckCircleOutlined /> },
                ]}
            />

            <Form<CheckoutFormValues>
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
                initialValues={{
                    deliveryMethod: DeliveryMethod.PICKUP,
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

                        <Card style={{ marginBottom: 16 }}>
                            <Form.Item name="comment" label="Комментарий к заказу">
                                <textarea
                                    rows={3}
                                    placeholder="Дополнительные пожелания по заказу..."
                                    maxLength={500}
                                    style={{ width: '100%', padding: '8px 12px', borderRadius: 6, border: '1px solid var(--line-2)', fontSize: 'var(--text-base)', fontFamily: 'var(--font-body)', resize: 'vertical', outline: 'none' }}
                                    onChange={(e) => form.setFieldValue('comment', e.target.value)}
                                />
                            </Form.Item>
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
