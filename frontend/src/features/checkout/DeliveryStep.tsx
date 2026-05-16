import { Form, Radio, Select, Typography, Divider, Spin } from 'antd';
import { DeliveryMethod, DeliveryMethodLabels } from '@/types/order';
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
            >
                {Object.entries(DeliveryMethodLabels).map(([key, label]) => (
                    <Radio.Button key={key} value={key}>
                        {label}
                    </Radio.Button>
                ))}
            </Radio.Group>
        </Form.Item>

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
    </>
);

export default DeliveryStep;
