import { Form, Radio, Select, Typography, Divider, Spin } from 'antd';
import { CLIENT_DELIVERY_METHODS, DeliveryMethod, DeliveryMethodLabels } from '@/types/order';
import CarrierPicker from './CarrierPicker';
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
}: DeliveryStepProps) => (
    <>
        <Form.Item
            name="deliveryMethod"
            rules={[{ required: true, message: 'Выберите способ доставки' }]}
        >
            <Radio.Group
                onChange={() => onDeliveryMethodChange(recipientsCount)}
                style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}
            >
                {CLIENT_DELIVERY_METHODS.map((method) => (
                    <Radio.Button key={method} value={method}>
                        {DeliveryMethodLabels[method]}
                    </Radio.Button>
                ))}
            </Radio.Group>
        </Form.Item>

        {deliveryMethod === DeliveryMethod.CARRIER_ADDRESS && <CarrierPicker />}

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
    </>
);

export default DeliveryStep;
