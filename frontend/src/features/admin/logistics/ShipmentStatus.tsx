import { Tag, Tooltip, Typography } from 'antd';
import { CheckCircleFilled } from '@ant-design/icons';
import type { ShipmentDto } from '@/types/delivery';
import { SHIPMENT_STATUS } from '@/utils/shipmentStatus';

const { Text } = Typography;

/** Статус отправления: тег по коду, сырой текст ТК под ним (в плотной таблице скрыт — тогда в подсказке), пометки «Не найдена» / «Нет движения». */
export const ShipmentStatus = ({ shipment, showCarrierText = true }: { shipment: ShipmentDto; showCarrierText?: boolean }) => {
    const code = shipment.carrierStatusCode;
    const tag = code && SHIPMENT_STATUS[code];
    return (
        <div>
            {code && tag && (
                <Tooltip title={showCarrierText ? undefined : shipment.carrierStatus}>
                    {code === 'DELIVERED' ? (
                        <span>
                            <CheckCircleFilled style={{ color: 'var(--brand-green)', marginRight: 4 }} />
                            <Text type="secondary">{tag.label}</Text>
                        </span>
                    ) : (
                        <Tag color={tag.color}>{tag.label}</Tag>
                    )}
                </Tooltip>
            )}
            {shipment.notFound && code !== 'NOT_FOUND' && <Tag color="red">Не найдена</Tag>}
            {shipment.pollingStopped && <Tag>Нет движения</Tag>}
            {shipment.carrierStatus && (showCarrierText || !code) && (
                <div><Text type="secondary" style={{ fontSize: 12 }}>{shipment.carrierStatus}</Text></div>
            )}
        </div>
    );
};
