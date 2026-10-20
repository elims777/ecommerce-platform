import { useEffect, useMemo, useState } from 'react';
import { Modal, Form, Radio, Select, Input, InputNumber, Button, Typography, Divider, message } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { updateOrder } from '@/api/orders';
import { getWarehousePoints } from '@/api/warehouse';
import { CLIENT_DELIVERY_METHODS, DeliveryMethod, DeliveryMethodLabels, PaymentMethod, PaymentMethodLabels } from '@/types/order';
import CarrierPicker from '@/features/checkout/CarrierPicker';
import PointsPicker from '@/features/checkout/PointsPicker';
import { warehouseToPickerPoint } from '@/features/checkout/warehouseToPickerPoint';
import TerminalPicker from '@/features/checkout/TerminalPicker';
import AddressSuggest from '@/features/checkout/AddressSuggest';
import { extractEnumCode } from '@/utils/enumUtils';
import type { OrderDto, UpdateOrderItemRequest } from '@/types/order';
import type { CarrierCode } from '@/types/delivery';
import type { AxiosError } from 'axios';

const { Text } = Typography;

interface EditOrderModalProps {
    order: OrderDto;
    open: boolean;
    onClose: () => void;
    onSuccess: (updated: OrderDto) => void;
}

interface EditOrderFormValues {
    paymentMethod: PaymentMethod;
    deliveryMethod: DeliveryMethod;
    warehousePointId?: number;
    carrierCode?: CarrierCode;
    carrierTerminalId?: number;
    pickupRecipientName?: string;
    pickupRecipientPhone?: string;
    recipientName?: string;
    phone?: string;
    city?: string;
    street?: string;
    building?: string;
    apartment?: string;
    postalCode?: string;
    comment?: string;
}

const EditOrderModal = ({ order, open, onClose, onSuccess }: EditOrderModalProps) => {
    const [form] = Form.useForm<EditOrderFormValues>();
    const queryClient = useQueryClient();
    const [items, setItems] = useState<UpdateOrderItemRequest[]>([]);

    const deliveryMethod = Form.useWatch('deliveryMethod', form);

    const { data: warehousePoints = [] } = useQuery({
        queryKey: ['warehousePoints'],
        queryFn: getWarehousePoints,
        staleTime: 5 * 60 * 1000,
    });

    const warehousePickerPoints = useMemo(() => warehousePoints.map(warehouseToPickerPoint), [warehousePoints]);

    // open: false — модалка монтируется уже открытой, товары должны заполниться на первом рендере
    const [prevInput, setPrevInput] = useState({ open: false, order });
    if (prevInput.open !== open || prevInput.order !== order) {
        setPrevInput({ open, order });
        if (open) setItems(order.items.map((i) => ({ productId: i.productId, quantity: i.quantity })));
    }

    useEffect(() => {
        if (!open) return;
        form.setFieldsValue({
            paymentMethod: extractEnumCode(order.paymentMethod) as PaymentMethod,
            deliveryMethod: extractEnumCode(order.deliveryMethod) as DeliveryMethod,
            warehousePointId: order.warehousePoint?.id,
            carrierCode: order.carrierCode ?? undefined,
            carrierTerminalId: order.carrierTerminalId ?? undefined,
            pickupRecipientName: order.pickupRecipientName ?? undefined,
            pickupRecipientPhone: order.pickupRecipientPhone ?? undefined,
            recipientName: order.deliveryAddress?.recipientName,
            phone: order.deliveryAddress?.phone,
            city: order.deliveryAddress?.city,
            street: order.deliveryAddress?.street,
            building: order.deliveryAddress?.building,
            apartment: order.deliveryAddress?.apartment,
            postalCode: order.deliveryAddress?.postalCode,
            comment: order.comment,
        });
    }, [open, order, form]);

    const mutation = useMutation({
        mutationFn: (values: EditOrderFormValues) => updateOrder(order.id, {
            paymentMethod: values.paymentMethod,
            deliveryMethod: values.deliveryMethod,
            warehousePointId: values.deliveryMethod === DeliveryMethod.PICKUP ? values.warehousePointId : undefined,
            carrierCode: (values.deliveryMethod === DeliveryMethod.CARRIER_ADDRESS
                || values.deliveryMethod === DeliveryMethod.CARRIER_TERMINAL) ? values.carrierCode : undefined,
            carrierTerminalId: values.deliveryMethod === DeliveryMethod.CARRIER_TERMINAL ? values.carrierTerminalId : undefined,
            pickupRecipientName: values.deliveryMethod === DeliveryMethod.CARRIER_TERMINAL ? values.pickupRecipientName : undefined,
            pickupRecipientPhone: values.deliveryMethod === DeliveryMethod.CARRIER_TERMINAL ? values.pickupRecipientPhone : undefined,
            deliveryAddress: (values.deliveryMethod === DeliveryMethod.CARRIER_ADDRESS
                || values.deliveryMethod === DeliveryMethod.SUPPLIER_DELIVERY) ? {
                recipientName: values.recipientName!,
                phone: values.phone!,
                city: values.city!,
                street: values.street!,
                building: values.building!,
                apartment: values.apartment,
                postalCode: values.postalCode,
            } : undefined,
            items,
            comment: values.comment,
        }),
        onSuccess: (updated) => {
            message.success('Заказ обновлён');
            queryClient.invalidateQueries({ queryKey: ['myOrders'] });
            onSuccess(updated);
            onClose();
        },
        onError: (error) => {
            const axiosError = error as AxiosError<{ message?: string }>;
            message.error(axiosError.response?.data?.message || 'Не удалось сохранить изменения');
        },
    });

    const handleQuantityChange = (productId: number, quantity: number) => {
        setItems((prev) => prev.map((i) => (i.productId === productId ? { ...i, quantity } : i)));
    };

    const handleRemoveItem = (productId: number) => {
        setItems((prev) => prev.filter((i) => i.productId !== productId));
    };

    const handleSubmit = (values: EditOrderFormValues) => {
        if (items.length === 0) {
            message.error('В заказе должна остаться хотя бы одна позиция');
            return;
        }
        mutation.mutate(values);
    };

    // SUPPLIER_DELIVERY назначает менеджер: клиент видит его, только если заказ уже в этом способе
    const orderMethod = extractEnumCode(order.deliveryMethod) as DeliveryMethod;
    const methods = orderMethod === DeliveryMethod.SUPPLIER_DELIVERY
        ? [...CLIENT_DELIVERY_METHODS, DeliveryMethod.SUPPLIER_DELIVERY]
        : CLIENT_DELIVERY_METHODS;
    const needsAddress = deliveryMethod === DeliveryMethod.CARRIER_ADDRESS
        || deliveryMethod === DeliveryMethod.SUPPLIER_DELIVERY;

    return (
        <Modal
            open={open}
            onCancel={onClose}
            title={`Редактирование заказа ${order.orderNumber}`}
            footer={null}
            destroyOnHidden
        >
            <Form<EditOrderFormValues>
                form={form}
                layout="vertical"
                onFinish={handleSubmit}
            >
                <Form.Item name="paymentMethod" label="Способ оплаты" rules={[{ required: true }]}>
                    <Select options={Object.entries(PaymentMethodLabels).map(([value, label]) => ({ value, label }))} />
                </Form.Item>

                <Form.Item name="deliveryMethod" label="Способ доставки" rules={[{ required: true }]}>
                    <Radio.Group
                        style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}
                        onChange={() => form.setFieldsValue({ carrierCode: undefined, carrierTerminalId: undefined })}
                    >
                        {methods.map((method) => (
                            <Radio.Button key={method} value={method}>{DeliveryMethodLabels[method]}</Radio.Button>
                        ))}
                    </Radio.Group>
                </Form.Item>

                {deliveryMethod === DeliveryMethod.PICKUP && (
                    <Form.Item name="warehousePointId" label="Точка самовывоза" rules={[{ required: true, message: 'Выберите точку самовывоза' }]}>
                        <PointsPicker points={warehousePickerPoints} />
                    </Form.Item>
                )}

                {deliveryMethod === DeliveryMethod.CARRIER_ADDRESS && <CarrierPicker mode="address" />}

                {deliveryMethod === DeliveryMethod.CARRIER_TERMINAL && (
                    <>
                        <CarrierPicker mode="terminal" />
                        <TerminalPicker current={order.carrierTerminalId ? {
                            id: order.carrierTerminalId,
                            name: order.terminalName ?? '',
                            address: order.terminalCurrentAddress ?? order.terminalAddress ?? '',
                        } : undefined} />
                    </>
                )}

                {needsAddress && (
                    <>
                        <Form.Item name="recipientName" label="Получатель" rules={[{ required: true, message: 'Укажите получателя' }]}>
                            <Input />
                        </Form.Item>
                        <Form.Item name="phone" label="Телефон" rules={[{ required: true, message: 'Укажите телефон' }]}>
                            <Input />
                        </Form.Item>
                        <AddressSuggest fields={{ city: 'city', street: 'street', building: 'building', postalCode: 'postalCode' }} />
                        <Form.Item name="city" label="Город" rules={[{ required: true, message: 'Укажите город' }]}>
                            <Input />
                        </Form.Item>
                        <Form.Item name="street" label="Улица" rules={[{ required: true, message: 'Укажите улицу' }]}>
                            <Input />
                        </Form.Item>
                        <Form.Item name="building" label="Дом" rules={[{ required: true, message: 'Укажите дом' }]}>
                            <Input />
                        </Form.Item>
                        <Form.Item name="apartment" label="Квартира/офис">
                            <Input />
                        </Form.Item>
                        <Form.Item name="postalCode" label="Индекс">
                            <Input />
                        </Form.Item>
                    </>
                )}

                <Divider titlePlacement="left" plain>Товары</Divider>
                {order.items.map((orderItem) => {
                    const current = items.find((i) => i.productId === orderItem.productId);
                    if (!current) return null;
                    return (
                        <div key={orderItem.productId} style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                            <Text style={{ flex: 1 }}>{orderItem.productName}</Text>
                            <InputNumber
                                min={1}
                                value={current.quantity}
                                onChange={(value) => handleQuantityChange(orderItem.productId, value ?? 1)}
                            />
                            <Button
                                type="text"
                                danger
                                icon={<DeleteOutlined />}
                                onClick={() => handleRemoveItem(orderItem.productId)}
                            />
                        </div>
                    );
                })}

                <Form.Item name="comment" label="Комментарий к заказу" style={{ marginTop: 16 }}>
                    <Input.TextArea rows={3} maxLength={500} />
                </Form.Item>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Button onClick={onClose}>Отмена</Button>
                    <Button type="primary" htmlType="submit" loading={mutation.isPending}>
                        Сохранить
                    </Button>
                </div>
            </Form>
        </Modal>
    );
};

export default EditOrderModal;
