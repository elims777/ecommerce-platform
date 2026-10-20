import { Tabs, Typography } from 'antd';
import ShipmentsTab from './logistics/ShipmentsTab';
import CarriersTab from './logistics/CarriersTab';
import WarehousePointsTab from './logistics/WarehousePointsTab';

const { Title } = Typography;

const LogisticsPage = () => (
    <div>
        <Title level={2} style={{ marginBottom: 24 }}>Логистика</Title>
        <Tabs
            items={[
                { key: 'shipments', label: 'Отправления', children: <ShipmentsTab /> },
                { key: 'carriers', label: 'Транспортные компании', children: <CarriersTab /> },
                { key: 'pickup', label: 'Точки самовывоза', children: <WarehousePointsTab /> },
            ]}
        />
    </div>
);

export default LogisticsPage;
