import { Alert, Form, Radio, Spin, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getCarriers } from '@/api/delivery';
import { DELIVERY_DISCLAIMER } from '@/types/delivery';

const { Text } = Typography;

/**
 * Выбор транспортной компании для доставки до адреса + дисклеймер об оплате доставки.
 * Значение пишется в поле формы carrierCode — используется в оформлении и в редактировании заказа.
 */
const CarrierPicker = () => {
    const { data: carriers = [], isLoading } = useQuery({
        queryKey: ['carriers'],
        queryFn: getCarriers,
        staleTime: 5 * 60 * 1000,
    });

    if (isLoading) {
        return <div style={{ textAlign: 'center', padding: 16 }}><Spin /></div>;
    }

    const available = carriers.filter((c) => c.addressDeliveryEnabled);

    if (available.length === 0) {
        // Скрытое обязательное поле не даёт отправить форму, пока клиент не сменит способ доставки
        return (
            <>
                <Alert type="warning" showIcon style={{ marginBottom: 8 }}
                       message="Доставка до адреса временно недоступна, выберите самовывоз" />
                <Form.Item name="carrierCode" rules={[{ required: true, message: 'Выберите самовывоз' }]}>
                    <input type="hidden" />
                </Form.Item>
            </>
        );
    }

    return (
        <>
            <Form.Item
                name="carrierCode"
                label="Транспортная компания"
                rules={[{ required: true, message: 'Выберите транспортную компанию' }]}
            >
                <Radio.Group style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {available.map((c) => (
                        <Radio.Button key={c.code} value={c.code}>{c.name}</Radio.Button>
                    ))}
                </Radio.Group>
            </Form.Item>
            <Text type="secondary" style={{ display: 'block', fontSize: 12, lineHeight: 1.5, marginBottom: 16 }}>
                {DELIVERY_DISCLAIMER}
            </Text>
        </>
    );
};

export default CarrierPicker;
