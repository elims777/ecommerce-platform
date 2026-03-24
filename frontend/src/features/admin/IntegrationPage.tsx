import {
    Tabs,
    Table,
    Tag,
    Typography,
    Card,
    Descriptions,
} from 'antd';
import {
    SyncOutlined,
    CheckCircleOutlined,
    CloseCircleOutlined,
    ClockCircleOutlined,
    PictureOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

// ============================================================
// Типы — маппятся на таблицы integration-service
// ============================================================

interface ExchangeSession {
    id: number;
    sessionId: string;
    exchangeType: string;
    status: string;
    startedAt: string;
    finishedAt: string | null;
    errorMessage: string | null;
}

interface ImportLogEntry {
    id: number;
    sessionId: string;
    entityType: string;
    externalId: string;
    action: string;
    status: string;
    errorMessage: string | null;
    createdAt: string;
}

interface ImageTask {
    id: number;
    productExternalId: string;
    sourceUrl: string;
    status: string;
    retryCount: number;
    errorMessage: string | null;
    createdAt: string;
    processedAt: string | null;
}

// ============================================================
// API — integration-service endpoints
// TODO: добавить реальные endpoints когда будут готовы
// Пока используем mock-данные
// ============================================================

/** Форматирует дату */
const formatDate = (dateStr: string | null): string => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    });
};

/** Цвет тега по статусу */
const getStatusTag = (status: string) => {
    const map: Record<string, { color: string; icon: React.ReactNode }> = {
        SUCCESS: { color: 'green', icon: <CheckCircleOutlined /> },
        COMPLETED: { color: 'green', icon: <CheckCircleOutlined /> },
        PROCESSED: { color: 'green', icon: <CheckCircleOutlined /> },
        FAILED: { color: 'red', icon: <CloseCircleOutlined /> },
        ERROR: { color: 'red', icon: <CloseCircleOutlined /> },
        IN_PROGRESS: { color: 'processing', icon: <SyncOutlined spin /> },
        PENDING: { color: 'orange', icon: <ClockCircleOutlined /> },
        CREATED: { color: 'blue', icon: <ClockCircleOutlined /> },
    };
    const config = map[status] || { color: 'default', icon: null };
    return (
        <Tag color={config.color} icon={config.icon}>
            {status}
        </Tag>
    );
};

// ============================================================
// MOCK DATA — заменить на реальные API
// TODO: GET /api/v1/integration/sessions
// TODO: GET /api/v1/integration/import-log
// TODO: GET /api/v1/integration/image-tasks
// ============================================================

const mockSessions: ExchangeSession[] = [
    {
        id: 1,
        sessionId: 'sess-20260324-001',
        exchangeType: 'catalog',
        status: 'COMPLETED',
        startedAt: '2026-03-24T08:00:00',
        finishedAt: '2026-03-24T08:03:45',
        errorMessage: null,
    },
    {
        id: 2,
        sessionId: 'sess-20260324-002',
        exchangeType: 'sale',
        status: 'COMPLETED',
        startedAt: '2026-03-24T08:05:00',
        finishedAt: '2026-03-24T08:05:12',
        errorMessage: null,
    },
    {
        id: 3,
        sessionId: 'sess-20260323-001',
        exchangeType: 'catalog',
        status: 'FAILED',
        startedAt: '2026-03-23T20:00:00',
        finishedAt: '2026-03-23T20:01:30',
        errorMessage: 'Connection refused: product-service unavailable',
    },
    {
        id: 4,
        sessionId: 'sess-20260323-002',
        exchangeType: 'catalog',
        status: 'IN_PROGRESS',
        startedAt: '2026-03-24T10:15:00',
        finishedAt: null,
        errorMessage: null,
    },
];

const mockImportLog: ImportLogEntry[] = [
    { id: 1, sessionId: 'sess-20260324-001', entityType: 'PRODUCT', externalId: '1c-prod-001', action: 'CREATE', status: 'SUCCESS', errorMessage: null, createdAt: '2026-03-24T08:01:00' },
    { id: 2, sessionId: 'sess-20260324-001', entityType: 'PRODUCT', externalId: '1c-prod-002', action: 'UPDATE', status: 'SUCCESS', errorMessage: null, createdAt: '2026-03-24T08:01:01' },
    { id: 3, sessionId: 'sess-20260324-001', entityType: 'CATEGORY', externalId: '1c-cat-005', action: 'CREATE', status: 'SUCCESS', errorMessage: null, createdAt: '2026-03-24T08:00:30' },
    { id: 4, sessionId: 'sess-20260324-001', entityType: 'PRODUCT', externalId: '1c-prod-003', action: 'CREATE', status: 'FAILED', errorMessage: 'Duplicate SKU: RES-122', createdAt: '2026-03-24T08:01:05' },
    { id: 5, sessionId: 'sess-20260324-001', entityType: 'OFFER', externalId: '1c-offer-001', action: 'UPDATE', status: 'SUCCESS', errorMessage: null, createdAt: '2026-03-24T08:02:00' },
];

const mockImageTasks: ImageTask[] = [
    { id: 1, productExternalId: '1c-prod-001', sourceUrl: '/uploads/1c/img001.jpg', status: 'PROCESSED', retryCount: 0, errorMessage: null, createdAt: '2026-03-24T08:01:10', processedAt: '2026-03-24T08:01:25' },
    { id: 2, productExternalId: '1c-prod-002', sourceUrl: '/uploads/1c/img002.jpg', status: 'PROCESSED', retryCount: 0, errorMessage: null, createdAt: '2026-03-24T08:01:11', processedAt: '2026-03-24T08:01:28' },
    { id: 3, productExternalId: '1c-prod-003', sourceUrl: '/uploads/1c/img003.jpg', status: 'PENDING', retryCount: 0, errorMessage: null, createdAt: '2026-03-24T08:01:12', processedAt: null },
    { id: 4, productExternalId: '1c-prod-004', sourceUrl: '/uploads/1c/img004.jpg', status: 'FAILED', retryCount: 3, errorMessage: 'WebP conversion failed: unsupported format', createdAt: '2026-03-23T20:01:00', processedAt: null },
];

// ============================================================

const IntegrationPage = () => {
    // Сессии обмена
    const sessionColumns: ColumnsType<ExchangeSession> = [
        {
            title: 'ID сессии',
            dataIndex: 'sessionId',
            key: 'sessionId',
            render: (id: string) => <Text code>{id}</Text>,
        },
        {
            title: 'Тип',
            dataIndex: 'exchangeType',
            key: 'exchangeType',
            width: 120,
            render: (type: string) => (
                <Tag color={type === 'catalog' ? 'blue' : 'purple'}>
                    {type === 'catalog' ? 'Каталог' : 'Продажи'}
                </Tag>
            ),
        },
        {
            title: 'Статус',
            dataIndex: 'status',
            key: 'status',
            width: 150,
            render: (status: string) => getStatusTag(status),
        },
        {
            title: 'Начало',
            dataIndex: 'startedAt',
            key: 'startedAt',
            width: 170,
            render: (date: string) => formatDate(date),
        },
        {
            title: 'Окончание',
            dataIndex: 'finishedAt',
            key: 'finishedAt',
            width: 170,
            render: (date: string | null) => formatDate(date),
        },
        {
            title: 'Ошибка',
            dataIndex: 'errorMessage',
            key: 'errorMessage',
            ellipsis: true,
            render: (msg: string | null) =>
                msg ? <Text type="danger">{msg}</Text> : <Text type="secondary">—</Text>,
        },
    ];

    // Лог импорта
    const importLogColumns: ColumnsType<ImportLogEntry> = [
        {
            title: 'Сессия',
            dataIndex: 'sessionId',
            key: 'sessionId',
            width: 180,
            render: (id: string) => <Text code>{id}</Text>,
        },
        {
            title: 'Тип',
            dataIndex: 'entityType',
            key: 'entityType',
            width: 110,
            render: (type: string) => <Tag>{type}</Tag>,
        },
        {
            title: 'External ID',
            dataIndex: 'externalId',
            key: 'externalId',
            width: 150,
            render: (id: string) => <Text code>{id}</Text>,
        },
        {
            title: 'Действие',
            dataIndex: 'action',
            key: 'action',
            width: 100,
            render: (action: string) => (
                <Tag color={action === 'CREATE' ? 'green' : 'blue'}>{action}</Tag>
            ),
        },
        {
            title: 'Статус',
            dataIndex: 'status',
            key: 'status',
            width: 120,
            render: (status: string) => getStatusTag(status),
        },
        {
            title: 'Ошибка',
            dataIndex: 'errorMessage',
            key: 'errorMessage',
            ellipsis: true,
            render: (msg: string | null) =>
                msg ? <Text type="danger">{msg}</Text> : <Text type="secondary">—</Text>,
        },
        {
            title: 'Дата',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 170,
            render: (date: string) => formatDate(date),
        },
    ];

    // Задачи обработки изображений
    const imageTaskColumns: ColumnsType<ImageTask> = [
        {
            title: 'Product External ID',
            dataIndex: 'productExternalId',
            key: 'productExternalId',
            width: 160,
            render: (id: string) => <Text code>{id}</Text>,
        },
        {
            title: 'Источник',
            dataIndex: 'sourceUrl',
            key: 'sourceUrl',
            ellipsis: true,
        },
        {
            title: 'Статус',
            dataIndex: 'status',
            key: 'status',
            width: 130,
            render: (status: string) => getStatusTag(status),
        },
        {
            title: 'Попытки',
            dataIndex: 'retryCount',
            key: 'retryCount',
            width: 90,
            render: (count: number) => (
                <Text type={count > 0 ? 'warning' : 'secondary'}>{count}</Text>
            ),
        },
        {
            title: 'Ошибка',
            dataIndex: 'errorMessage',
            key: 'errorMessage',
            ellipsis: true,
            render: (msg: string | null) =>
                msg ? <Text type="danger">{msg}</Text> : <Text type="secondary">—</Text>,
        },
        {
            title: 'Создана',
            dataIndex: 'createdAt',
            key: 'createdAt',
            width: 170,
            render: (date: string) => formatDate(date),
        },
        {
            title: 'Обработана',
            dataIndex: 'processedAt',
            key: 'processedAt',
            width: 170,
            render: (date: string | null) => formatDate(date),
        },
    ];

    // Сводка по последней сессии
    const lastSession = mockSessions[0];
    const successCount = mockImportLog.filter((l) => l.status === 'SUCCESS').length;
    const failedCount = mockImportLog.filter((l) => l.status === 'FAILED').length;
    const pendingImages = mockImageTasks.filter((t) => t.status === 'PENDING').length;

    const tabItems = [
        {
            key: 'sessions',
            label: (
                <span>
          <SyncOutlined /> Сессии обмена
        </span>
            ),
            children: (
                <Table<ExchangeSession>
                    columns={sessionColumns}
                    dataSource={mockSessions}
                    rowKey="id"
                    pagination={false}
                    size="small"
                />
            ),
        },
        {
            key: 'importLog',
            label: (
                <span>
          <CheckCircleOutlined /> Лог импорта
        </span>
            ),
            children: (
                <Table<ImportLogEntry>
                    columns={importLogColumns}
                    dataSource={mockImportLog}
                    rowKey="id"
                    pagination={{ pageSize: 20 }}
                    size="small"
                />
            ),
        },
        {
            key: 'images',
            label: (
                <span>
          <PictureOutlined /> Обработка изображений
        </span>
            ),
            children: (
                <Table<ImageTask>
                    columns={imageTaskColumns}
                    dataSource={mockImageTasks}
                    rowKey="id"
                    pagination={{ pageSize: 20 }}
                    size="small"
                />
            ),
        },
    ];

    return (
        <div>
            <Title level={2} style={{ marginBottom: 24 }}>
                Интеграция 1С
            </Title>

            {/* Сводка */}
            <Card style={{ marginBottom: 16, borderRadius: 12 }}>
                <Descriptions
                    title="Последний обмен"
                    column={{ xs: 1, sm: 2, lg: 4 }}
                    size="small"
                >
                    <Descriptions.Item label="Сессия">
                        <Text code>{lastSession.sessionId}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="Тип">
                        <Tag color="blue">
                            {lastSession.exchangeType === 'catalog' ? 'Каталог' : 'Продажи'}
                        </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Статус">
                        {getStatusTag(lastSession.status)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Время">
                        {formatDate(lastSession.startedAt)}
                    </Descriptions.Item>
                    <Descriptions.Item label="Импортировано успешно">
                        <Text type="success">{successCount}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="С ошибками">
                        <Text type="danger">{failedCount}</Text>
                    </Descriptions.Item>
                    <Descriptions.Item label="Изображений в очереди">
                        <Text type="warning">{pendingImages}</Text>
                    </Descriptions.Item>
                </Descriptions>
            </Card>

            {/* Табы с таблицами */}
            <Card style={{ borderRadius: 12 }}>
                <Tabs items={tabItems} />
            </Card>
        </div>
    );
};

export default IntegrationPage;