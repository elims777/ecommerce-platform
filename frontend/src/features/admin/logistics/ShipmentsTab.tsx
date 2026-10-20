import { useEffect, useRef, useState } from 'react';
import { App, Badge, Button, Checkbox, ConfigProvider, DatePicker, Dropdown, Form, Input, Modal, Progress, Select, Space, Table, Tooltip, Typography } from 'antd';
import { EllipsisOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import { NavLink } from '@/components/navigation';
import { downloadShipmentDocument, getAdminCarriers, getAdminShipments, getShipmentImportStatus, importShipments, linkShipment, refreshShipment } from '@/api/adminLogistics';
import type { ShipmentDto, ShipmentFilters, ShipmentStatusCode } from '@/types/delivery';
import { ShipmentStatus } from './ShipmentStatus';
import { deliveryDateLabel } from '@/utils/deliveryDate';
import { SHIPMENT_STATUS } from '@/utils/shipmentStatus';
import { formatDate, formatNumber, payerRole, payerSide, plural, routeLine } from '@/utils/shipment';

const { RangePicker } = DatePicker;
const { Text } = Typography;

const PAGE_SIZE = 20;

/** Мелкие серые подписи в строке, как в ЛК ДЛ */
const HINT = { fontSize: 10 };

const STATUS_OPTIONS = Object.entries(SHIPMENT_STATUS).map(([value, { label }]) => ({ value: value as ShipmentStatusCode, label }));

/** Как часто опрашивать статус, пока идёт импорт */
const IMPORT_POLL_MS = 2000;

const shipmentDay = (s: ShipmentDto) => formatDate(s.shippedAt ?? s.createdAt);

/** Монетка «₽» рядом с именем плательщика, как в ЛК ТК */
const PayerCoin = () => (
    <Tooltip title="Плательщик">
        <span style={{ display: 'inline-block', width: 16, height: 16, lineHeight: '16px', marginLeft: 6, borderRadius: '50%',
            background: 'var(--warn)', color: '#fff', fontSize: 11, fontWeight: 700, textAlign: 'center' }}>₽</span>
    </Tooltip>
);

/** Журнал отправлений: фильтры, серверная пагинация, обновление статуса и привязка к заказу. */
const ShipmentsTab = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const [filters, setFilters] = useState<ShipmentFilters>({});
    const [page, setPage] = useState(1);
    const [linking, setLinking] = useState<ShipmentDto | null>(null);
    const [orderNumber, setOrderNumber] = useState('');
    const [expandedRowKeys, setExpandedRowKeys] = useState<number[]>([]);

    const { data: carriers = [] } = useQuery({ queryKey: ['adminCarriers'], queryFn: getAdminCarriers });

    const { data, isLoading } = useQuery({
        queryKey: ['adminShipments', filters, page],
        queryFn: () => getAdminShipments({ ...filters, page: page - 1, size: PAGE_SIZE }),
    });
    const rows = data?.content ?? [];

    const { data: importStatus, dataUpdatedAt: importStatusAt } = useQuery({
        queryKey: ['shipmentImportStatus'],
        queryFn: getShipmentImportStatus,
        staleTime: 0,
        refetchInterval: (query) => (query.state.data?.running ? IMPORT_POLL_MS : false),
    });

    const wasRunning = useRef(false);
    useEffect(() => {
        const running = importStatus?.running ?? false;
        if (wasRunning.current && !running) {
            queryClient.invalidateQueries({ queryKey: ['adminShipments'] });
            messageApi.success('Импорт завершён');
        }
        wasRunning.current = running;
    }, [importStatus?.running, importStatusAt, queryClient, messageApi]);

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

    const importMutation = useMutation({
        mutationFn: importShipments,
        onSuccess: () => {
            messageApi.success('Импорт запущен');
            wasRunning.current = true; // даже если импорт закончится до первого опроса — таблица обновится
            queryClient.invalidateQueries({ queryKey: ['shipmentImportStatus'] });
        },
        onError: (error) => {
            messageApi.error(
                isAxiosError(error) && error.response?.status === 409
                    ? 'Импорт уже идёт'
                    : (isAxiosError(error) && error.response?.data?.message) || 'Не удалось запустить импорт');
            if (isAxiosError(error) && error.response?.status === 409) {
                queryClient.invalidateQueries({ queryKey: ['shipmentImportStatus'] });
            }
        },
    });

    const downloadDocument = async (s: ShipmentDto, code: string) => {
        try {
            await downloadShipmentDocument(s.id, code, s.waybillNumber);
        } catch (error) {
            // при responseType blob тело ошибки тоже приходит blob'ом
            const body = isAxiosError(error) && error.response?.data instanceof Blob
                ? await error.response.data.text().then((t) => JSON.parse(t) as { message?: string }).catch(() => undefined)
                : undefined;
            messageApi.error(body?.message || 'Не удалось скачать документ');
        }
    };

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
                <Select allowClear placeholder="Статус" style={{ width: 190 }} options={STATUS_OPTIONS}
                        onChange={(statusCode) => setFilter({ statusCode })} />
                <Input.Search allowClear placeholder="Город" style={{ width: 160 }}
                              onSearch={(city) => setFilter({ city: city.trim() || undefined })} />
                <Input.Search allowClear placeholder="Контрагент" style={{ width: 180 }}
                              onSearch={(counterparty) => setFilter({ counterparty: counterparty.trim() || undefined })} />
                <Input.Search allowClear placeholder="Накладная / заказ" style={{ width: 200 }}
                              onSearch={(search) => setFilter({ search: search.trim() || undefined })} />
                <RangePicker onChange={(dates) => setFilter({
                    from: dates?.[0]?.startOf('day').toISOString(),
                    to: dates?.[1]?.endOf('day').toISOString(),
                })} />
                <Checkbox onChange={(e) => setFilter({ problem: e.target.checked || undefined })}>С проблемами</Checkbox>
                <Checkbox onChange={(e) => setFilter({ notFound: e.target.checked || undefined })}>Не найдена</Checkbox>
                <Checkbox onChange={(e) => setFilter({ stale: e.target.checked || undefined })}>Нет движения</Checkbox>
                <Button loading={importMutation.isPending || importStatus?.running} onClick={() => importMutation.mutate()}>Импортировать сейчас</Button>
            </Space>

            <ConfigProvider theme={{ components: { Table: { cellFontSizeSM: 13 } } }}>
            <Table<ShipmentDto>
                size="small"
                rowKey="id"
                loading={isLoading}
                dataSource={rows}
                // завершённые (доставлен/отменён) — приглушённый фон, чтобы сразу отличались от активных
                onRow={(s) => (s.carrierStatusCode === 'DELIVERED' || s.carrierStatusCode === 'CANCELLED'
                    ? { style: { background: 'var(--surface-3)' } } : {})}
                pagination={{
                    current: page,
                    pageSize: PAGE_SIZE,
                    total: data?.totalElements ?? 0,
                    showSizeChanger: false,
                    onChange: setPage,
                }}
                expandable={{
                    expandedRowKeys,
                    showExpandColumn: false,
                    rowExpandable: (s) => s.packages.length > 0,
                    expandedRowRender: (s) => (
                        <Table size="small" pagination={false} rowKey={(p, i) => `${p.number}-${i}`} dataSource={s.packages}
                               columns={[
                                   { title: 'Место', render: (_, p) => p.number ?? '—' },
                                   { title: 'Вес', render: (_, p) => p.weightKg == null ? '—' : `${formatNumber(p.weightKg, 3)} кг` },
                                   {
                                       title: 'Габариты (Д×Ш×В), см',
                                       render: (_, p) => [p.lengthCm, p.widthCm, p.heightCm].some((v) => v == null)
                                           ? '—'
                                           : [p.lengthCm, p.widthCm, p.heightCm].map((v) => formatNumber(v!, 1)).join(' × '),
                                   },
                               ]} />
                    ),
                }}
                columns={[
                    {
                        title: 'Дата',
                        width: 110,
                        render: (_, s) => shipmentDay(s),
                        // одна ячейка на все отправления дня: первая строка дня растягивается, остальные скрываются
                        onCell: (s, index = 0) => {
                            if (index > 0 && shipmentDay(rows[index - 1]) === shipmentDay(s)) {
                                return { rowSpan: 0 };
                            }
                            let span = 1;
                            while (index + span < rows.length && shipmentDay(rows[index + span]) === shipmentDay(s)) {
                                span++;
                            }
                            return { rowSpan: span };
                        },
                    },
                    {
                        title: 'Накладная',
                        width: 240,
                        render: (_, s) => (
                            <div>
                                <div>
                                    {s.trackingUrl
                                        ? <a href={s.trackingUrl} target="_blank" rel="noopener noreferrer"><b>{s.waybillNumber}</b></a>
                                        : <b>{s.waybillNumber}</b>}
                                </div>
                                <Text type="secondary" style={HINT}>
                                    {[
                                        s.carrierName,
                                        [s.weightKg != null && `${formatNumber(s.weightKg, 3)} кг`, s.volumeM3 != null && `${formatNumber(s.volumeM3, 3)} м³`].filter(Boolean).join(', '),
                                        s.placesCount != null && `${s.placesCount} ${plural(s.placesCount, 'груз', 'груза', 'грузов')}`,
                                    ].filter(Boolean).join(' · ')}
                                </Text>
                                {s.orderId && (
                                    <div style={HINT}>
                                        <NavLink to={`/admin/orders/${s.orderId}`}>Заказ {s.orderNumber}</NavLink>
                                    </div>
                                )}
                            </div>
                        ),
                    },
                    {
                        title: 'Отправитель',
                        render: (_, s) => (
                            <div>
                                <div>{s.sender ?? '—'}{payerSide(s, 'SENDER') && <PayerCoin />}</div>
                                <Text type="secondary" style={HINT}>{routeLine(s.routeFrom, s.fromAddress, 'от')}</Text>
                            </div>
                        ),
                    },
                    {
                        title: 'Получатель',
                        render: (_, s) => (
                            <div>
                                <div>{s.receiver ?? '—'}{payerSide(s, 'RECEIVER') && <PayerCoin />}</div>
                                <Text type="secondary" style={HINT}>{routeLine(s.routeTo, s.toAddress, 'до')}</Text>
                                {payerSide(s, 'THIRD') && (
                                    <div><Text type="secondary" style={HINT}>
                                        Плательщик: {s.payers.filter((p) => payerRole(s, p) === 'THIRD').map((p) => p.name ?? p.inn).join(', ')}
                                    </Text></div>
                                )}
                            </div>
                        ),
                    },
                    {
                        title: 'Документы',
                        width: 120,
                        render: (_, s) => s.documents.length === 0 ? '—' : (
                            <Dropdown menu={{
                                items: s.documents.map((d) => ({ key: d.code, label: d.name })),
                                onClick: ({ key }) => downloadDocument(s, key),
                            }}>
                                <Button type="link" size="small">{s.documents.length} {plural(s.documents.length, 'документ', 'документа', 'документов')}</Button>
                            </Dropdown>
                        ),
                    },
                    {
                        title: 'Сумма',
                        width: 120,
                        render: (_, s) => s.cost == null ? '—' : (
                            <div>
                                <div>{s.cost.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} ₽</div>
                                {s.paid != null && <Badge status={s.paid ? 'success' : 'error'} text={<span style={HINT}>{s.paid ? 'Оплачено' : 'Не оплачено'}</span>} />}
                            </div>
                        ),
                    },
                    {
                        title: 'Статус',
                        width: 190,
                        render: (_, s) => {
                            const step = s.carrierStatusCode == null ? null : SHIPMENT_STATUS[s.carrierStatusCode].step;
                            const date = deliveryDateLabel(s);
                            return (
                                <div>
                                    <ShipmentStatus shipment={s} showCarrierText={false} />
                                    {step && <Progress steps={4} percent={step * 25} showInfo={false} size={[36, 4]} />}
                                    {(date || s.packages.length > 0) && (
                                        <div style={HINT}>
                                            {date && <Text type="secondary" style={HINT}>{date}</Text>}
                                            {date && s.packages.length > 0 && ' · '}
                                            {s.packages.length > 0 && (
                                                <Button type="link" size="small" style={{ ...HINT, padding: 0, height: 'auto' }} onClick={() => setExpandedRowKeys((keys) => keys.includes(s.id) ? keys.filter((k) => k !== s.id) : [...keys, s.id])}>
                                                    {expandedRowKeys.includes(s.id) ? 'Скрыть грузы' : 'Показать грузы'}
                                                </Button>
                                            )}
                                        </div>
                                    )}
                                </div>
                            );
                        },
                    },
                    {
                        title: '',
                        width: 40,
                        render: (_, s) => (
                            <Dropdown trigger={['click']} menu={{
                                items: [
                                    ...(carriers.find((c) => c.code === s.carrierCode)?.statusTrackingSupported
                                        ? [{ key: 'refresh', label: 'Обновить статус' }] : []),
                                    ...(!s.orderId ? [{ key: 'link', label: 'Привязать к заказу' }] : []),
                                ],
                                onClick: ({ key }) => key === 'refresh' ? refreshMutation.mutate(s.id) : setLinking(s),
                            }}>
                                <Button type="text" size="small" icon={<EllipsisOutlined />} loading={refreshMutation.isPending && refreshMutation.variables === s.id} />
                            </Dropdown>
                        ),
                    },
                ]}
            />
            </ConfigProvider>

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
