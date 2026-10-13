import { Tag, Typography } from 'antd';
import type { ShipmentDto, ShipmentStatusCode } from '@/types/delivery';

const { Text } = Typography;

const STATUS_CODE_TAGS: Record<ShipmentStatusCode, { color: string; label: string }> = {
    IN_TRANSIT: { color: 'blue', label: 'В пути' },
    ARRIVED_AT_TERMINAL: { color: 'cyan', label: 'Прибыл на терминал' },
    DELIVERED: { color: 'green', label: 'Доставлен' },
    CANCELLED: { color: 'default', label: 'Отменён' },
    PROBLEM: { color: 'orange', label: 'Проблема' },
    NOT_FOUND: { color: 'red', label: 'Не найдена' },
};

/** Статус отправления: тег по коду, сырой текст ТК и пометки «Не найдена» / «Нет движения». */
export const ShipmentStatus = ({ shipment }: { shipment: ShipmentDto }) => (
    <div>
        {shipment.carrierStatusCode && (
            <Tag color={STATUS_CODE_TAGS[shipment.carrierStatusCode].color}>
                {STATUS_CODE_TAGS[shipment.carrierStatusCode].label}
            </Tag>
        )}
        {shipment.notFound && shipment.carrierStatusCode !== 'NOT_FOUND' && <Tag color="red">Не найдена</Tag>}
        {shipment.pollingStopped && <Tag>Нет движения</Tag>}
        {shipment.carrierStatus && <div><Text type="secondary" style={{ fontSize: 12 }}>{shipment.carrierStatus}</Text></div>}
    </div>
);
