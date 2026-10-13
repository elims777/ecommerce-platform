import { useState } from 'react';
import { App, Button, Checkbox, DatePicker, Form, Input, Modal, Select, Space, Table, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { NavLink } from '@/components/navigation';
import { getAdminCarriers, getAdminShipments, linkShipment, refreshShipment } from '@/api/adminLogistics';
import type { ShipmentDirection, ShipmentDto, ShipmentFilters, ShipmentStatusCode } from '@/types/delivery';
import { ShipmentStatus } from './ShipmentStatus';

const { RangePicker } = DatePicker;
const { Text } = Typography;

const PAGE_SIZE = 20;

const DIRECTION_LABELS: Record<ShipmentDirection, string> = {
    OUTGOING: 'Исходящее',
    INCOMING: 'Входящее',
    UNKNOWN: 'Не определено',
};

const STATUS_OPTIONS: { value: ShipmentStatusCode; label: string }[] = [
    { value: 'IN_TRANSIT', label: 'В пути' },
    { value: 'ARRIVED_AT_TERMINAL', label: 'Прибыл на терминал' },
    { value: 'DELIVERED', label: 'Доставлен' },
    { value: 'CANCELLED', label: 'Отменён' },
    { value: 'PROBLEM', label: 'Проблема' },
    { value: 'NOT_FOUND', label: 'Не найдена' },
];

const formatDateTime = (iso: string) => new Date(iso).toLocaleString('ru-RU');

/** Журнал отправлений: фильтры, серверная пагинация, обновление статуса и привязка к заказу. */
const ShipmentsTab = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [filters, setFilters] = useState<ShipmentFilters>({});
    const [page, setPage] = useState(1);
    const [linking, setLinking] = useState<ShipmentDto | null>(null);
    const [orderNumber, setOrderNumber] = useState('');

    const { data: carriers = [] } = useQuery({ queryKey: ['adminCarriers'], queryFn: getAdminCarriers });

    const { data, isLoading } = useQuery({
        queryKey: ['adminShipments', filters, page],
        queryFn: () => getAdminShipments({ ...filters, page: page - 1, size: PAGE_SIZE }),
    });

    const refreshMutation = useMutation({
        mutationFn: (id: number) => refreshShipment(id),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['adminShipments'] }),
        onError: () => messageApi.error('Не удалось обновить статус'),
    });

    const linkMutation = useMutation({
        mutationFn: () => linkShipment(linking!.id, orderNumber.trim()),
        onSuccess: () => {
            messageApi.success('Отправление привязано к заказу');
            queryClient.invalidateQueries({ queryKey: ['adminShipments'] });
            queryClient.invalidateQueries({ queryKey: ['adminOrderDetail'] });
            setLinking(null);
            setOrderNumber('');
        },
        onError: (error) => messageApi.error(
            (isAxiosError(error) && error.response?.data?.message) || 'Не удалось привязать к заказу'),
    });

    const setFilter = (patch: Partial<ShipmentFilters>) => {
        setFilters((prev) => ({ ...prev, ...patch }));
        setPage(1);
    };

    return (
        <>
            <Space wrap style={{ marginBottom: 16 }}>
                <Select allowClear placeholder="ТК" style={{ width: 150 }}
                        options={carriers.map((c) => ({ value: c.code, label: c.name }))}
                        onChange={(carrier) => setFilter({ carrier })} />
                <Select allowClear placeholder="Направление" style={{ width: 150 }}
                        options={Object.entries(DIRECTION_LABELS).map(([value, label]) => ({ value, label }))}
                        onChange={(direction) => setFilter({ direction })} />
                <Select allowClear placeholder="Статус" style={{ width: 190 }} options={STATUS_OPTIONS}
                        onChange={(statusCode) => setFilter({ statusCode })} />
                <Input.Search allowClear placeholder="Город" style={{ width: 160 }}
                              onSearch={(city) => setFilter({ city: city.trim() || undefined })} />
                <RangePicker onChange={(dates) => setFilter({
                    from: dates?.[0]?.startOf('day').toISOString(),
                    to: dates?.[1]?.endOf('day').toISOString(),
                })} />
                <Checkbox onChange={(e) => setFilter({ problem: e.target.checked || undefined })}>С проблемами</Checkbox>
                <Checkbox onChange={(e) => setFilter({ notFound: e.target.checked || undefined })}>Не найдена</Checkbox>
                <Checkbox onChange={(e) => setFilter({ stale: e.target.checked || undefined })}>Нет движения</Checkbox>
                <Checkbox onChange={(e) => setFilter({ awaitingMore: e.target.checked || undefined })}>Ожидаются ещё отправления</Checkbox>
            </Space>

            <Table<ShipmentDto>
                rowKey="id"
                loading={isLoading}
                dataSource={data?.content ?? []}
                scroll={{ x: 'max-content' }}
                pagination={{
                    current: page,
                    pageSize: PAGE_SIZE,
                    total: data?.totalElements ?? 0,
                    showSizeChanger: false,
                    onChange: setPage,
                }}
                columns={[
                    { title: 'ТК', dataIndex: 'carrierName' },
                    {
                        title: 'Накладная',
                        render: (_, s) => s.trackingUrl
                            ? <a href={s.trackingUrl} target="_blank" rel="noopener noreferrer">{s.waybillNumber}</a>
                            : s.waybillNumber,
                    },
                    { title: 'Направление', render: (_, s) => DIRECTION_LABELS[s.direction] },
                    { title: 'Отправитель → получатель', render: (_, s) => `${s.sender ?? '—'} → ${s.receiver ?? '—'}` },
                    { title: 'Откуда → куда', render: (_, s) => `${s.routeFrom ?? '—'} → ${s.routeTo ?? '—'}` },
                    { title: 'Статус', render: (_, s) => <ShipmentStatus shipment={s} /> },
                    {
                        title: 'Обновлено',
                        render: (_, s) => {
                            const updated = s.carrierStatusUpdatedAt ?? s.polledAt;
                            return updated ? <Text type="secondary" style={{ fontSize: 12 }}>{formatDateTime(updated)}</Text> : '—';
                        },
                    },
                    {
                        title: 'Заказ',
                        render: (_, s) => s.orderId
                            ? <NavLink to={`/admin/orders/${s.orderId}`}>{s.orderNumber}</NavLink>
                            : '—',
                    },
                    {
                        title: 'Действия',
                        render: (_, s) => (
                            <Space>
                                {carriers.find((c) => c.code === s.carrierCode)?.statusTrackingSupported && (
                                    <Button size="small" loading={refreshMutation.isPending && refreshMutation.variables === s.id}
                                            onClick={() => refreshMutation.mutate(s.id)}>
                                        Обновить статус
                                    </Button>
                                )}
                                {!s.orderId && <Button size="small" onClick={() => setLinking(s)}>Привязать к заказу</Button>}
                            </Space>
                        ),
                    },
                ]}
            />

            <Modal
                open={!!linking}
                title={`Привязать накладную ${linking?.waybillNumber ?? ''} к заказу`}
                okText="Привязать"
                cancelText="Отмена"
                okButtonProps={{ disabled: !orderNumber.trim(), loading: linkMutation.isPending }}
                onOk={() => linkMutation.mutate()}
                onCancel={() => { setLinking(null); setOrderNumber(''); }}
            >
                <Form layout="vertical">
                    <Form.Item label="Номер заказа">
                        <Input value={orderNumber} onChange={(e) => setOrderNumber(e.target.value)} onPressEnter={() => orderNumber.trim() && linkMutation.mutate()} />
                    </Form.Item>
                </Form>
            </Modal>
        </>
    );
};

export default ShipmentsTab;
