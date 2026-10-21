import { useEffect } from 'react';
import { Form, Radio } from 'antd';
import { DeliveryMethod } from '@/types/order';
import type { Warehouse } from '@/types/product';

/** Город отгрузки. Если у выбранного склада нет самовывоза — сбрасывает выбранный PICKUP. */
const ShippingWarehouseField = ({ warehouses }: { warehouses: Warehouse[] }) => {
    const form = Form.useFormInstance();
    const warehouseId = Form.useWatch('shippingWarehouseId', form);
    const deliveryMethod = Form.useWatch('deliveryMethod', form);

    useEffect(() => {
        const selected = warehouses.find((w) => w.id === warehouseId);
        if (selected && !selected.pickupAvailable && deliveryMethod === DeliveryMethod.PICKUP) {
            form.setFieldsValue({ deliveryMethod: undefined, warehousePointId: undefined });
        }
    }, [warehouses, warehouseId, deliveryMethod, form]);

    return (
        <Form.Item
            name="shippingWarehouseId"
            label="Город отгрузки"
            rules={[{ required: true, message: 'Выберите город отгрузки' }]}
        >
            <Radio.Group style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {warehouses.map((w) => (
                    <Radio.Button key={w.id} value={w.id}>{w.city}</Radio.Button>
                ))}
            </Radio.Group>
        </Form.Item>
    );
};

export default ShippingWarehouseField;
