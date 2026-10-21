import { Tabs, Typography } from 'antd';
import ShipmentsTab from './logistics/ShipmentsTab';
import CarriersTab from './logistics/CarriersTab';
import WarehousePointsTab from './logistics/WarehousePointsTab';

import WarehousesTab from './logistics/WarehousesTab';

const { Title } = Typography;

const LogisticsPage = () => (
    <div>
        <Title level={2} style={{ marginBottom: 24 }}>Логистика</Title>
        <Tabs
            items={[
                { key: 'shipments', label: 'Отправления', children: <ShipmentsTab /> },
                { key: 'carriers', label: 'Транспортные компании', children: <CarriersTab /> },
                { key: 'warehouses', label: 'Склады', children: <WarehousesTab /> },
                { key: 'pickup', label: 'Точки самовывоза', children: <WarehousePointsTab /> },
            ]}
        />
    </div>
);

export default LogisticsPage;
