import { useMemo } from 'react';
import { Form, Radio, Typography, Divider, Spin } from 'antd';
import { CLIENT_DELIVERY_METHODS, DeliveryMethod, DeliveryMethodLabels } from '@/types/order';
import CarrierPicker from './CarrierPicker';
import PointsPicker from './PointsPicker';
import { warehouseToPickerPoint } from './warehouseToPickerPoint';
import TerminalPicker from './TerminalPicker';
import type { WarehousePointDto } from '@/types/order';

const { Text } = Typography;

interface DeliveryStepProps {
    deliveryMethod: DeliveryMethod | undefined;
    warehousePoints: WarehousePointDto[];
    warehouseLoading: boolean;
    recipientsCount: number;
    onDeliveryMethodChange: (recipientsCount: number) => void;
}

const DeliveryStep = ({
    deliveryMethod,
    warehousePoints,
    warehouseLoading,
    recipientsCount,
    onDeliveryMethodChange,
}: DeliveryStepProps) => {
    const form = Form.useFormInstance();
    const warehousePickerPoints = useMemo(() => warehousePoints.map(warehouseToPickerPoint), [warehousePoints]);
    return (
    <>
        <Form.Item
            name="deliveryMethod"
            rules={[{ required: true, message: 'Выберите способ доставки' }]}
        >
            <Radio.Group
                onChange={() => {
                    // Выбор ТК и терминала относится к конкретному способу — не переносим его в другой
                    form.setFieldsValue({ carrierCode: undefined, carrierTerminalId: undefined });
                    onDeliveryMethodChange(recipientsCount);
                }}
                style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}
            >
                {CLIENT_DELIVERY_METHODS.map((method) => (
                    <Radio.Button key={method} value={method}>
                        {DeliveryMethodLabels[method]}
                    </Radio.Button>
                ))}
            </Radio.Group>
        </Form.Item>

        {deliveryMethod === DeliveryMethod.CARRIER_ADDRESS && <CarrierPicker mode="address" />}

        {deliveryMethod === DeliveryMethod.CARRIER_TERMINAL && (
            <>
                <CarrierPicker mode="terminal" />
                <TerminalPicker />
            </>
        )}

        {deliveryMethod === DeliveryMethod.PICKUP && (
            <>
                <Divider titlePlacement="left" plain>
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
                        <PointsPicker points={warehousePickerPoints} />
                    </Form.Item>
                ) : (
                    <Text type="secondary">
                        Нет доступных точек самовывоза. Выберите доставку.
                    </Text>
                )}
            </>
        )}
    </>
    );
};

export default DeliveryStep;
