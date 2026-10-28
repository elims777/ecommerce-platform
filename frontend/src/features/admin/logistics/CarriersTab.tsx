import { App, Button, Input, InputNumber, Switch, Table, Tag, Typography } from 'antd';
import { useState } from 'react';
import { isAxiosError } from 'axios';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAdminCarriers, syncCarrierTerminals, updateCarrier } from '@/api/adminLogistics';
import type { CarrierDto, CarrierSyncReport, CarrierSyncStatus } from '@/types/delivery';

const { Text } = Typography;

const SYNC_STATUS_TAGS: Record<CarrierSyncStatus, { color: string; label: string }> = {
    OK: { color: 'green', label: 'Успешно' },
    WARNING: { color: 'orange', label: 'С предупреждениями' },
    ERROR: { color: 'red', label: 'Ошибка' },
    NOT_CONFIGURED: { color: 'default', label: 'Не настроена' },
};

const formatDateTime = (iso: string) => new Date(iso).toLocaleString('ru-RU');

/** Раскрываемый отчёт последней синхронизации терминалов. */
const SyncReportDetails = ({ report }: { report: CarrierSyncReport }) => (
    <div style={{ fontSize: 13 }}>
        <div>
            Получено: {report.received}, добавлено: {report.added}, обновлено: {report.updated},
            деактивировано: {report.deactivated}, пропущено: {report.skipped}. Длительность: {(report.durationMs / 1000).toFixed(1)} с
        </div>
        {report.message && <div style={{ marginTop: 4 }}>{report.message}</div>}
        {report.skippedItems.length > 0 && (
            <ul style={{ margin: '8px 0 0', paddingLeft: 20 }}>
                {report.skippedItems.map((item, i) => (
                    <li key={i}>{item.externalId ?? 'без ID'} — {item.reason}</li>
                ))}
                {report.moreSkipped > 0 && <li>и ещё {report.moreSkipped}</li>}
            </ul>
        )}
    </div>
);

/** Включение ТК, доставки до адреса и до терминала, порядок вывода клиенту, синхронизация терминалов. */
const CarriersTab = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    // Поле «Порядок» неуправляемое — при ошибке сохранения пересоздаём его, чтобы вернуть значение из БД
    const [resetKey, setResetKey] = useState(0);

    const { data: carriers = [], isLoading } = useQuery({
        queryKey: ['adminCarriers'],
        queryFn: getAdminCarriers,
    });

    const mutation = useMutation({
        mutationFn: (carrier: CarrierDto) => updateCarrier(carrier.code, {
            enabled: carrier.enabled,
            addressDeliveryEnabled: carrier.addressDeliveryEnabled,
            terminalDeliveryEnabled: carrier.terminalDeliveryEnabled,
            sortOrder: carrier.sortOrder,
            trackingUrlTemplate: carrier.trackingUrlTemplate ?? undefined,
        }),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['adminCarriers'] });
            void queryClient.invalidateQueries({ queryKey: ['carriers'] });
        },
        onError: (error) => {
            messageApi.error((isAxiosError(error) && error.response?.data?.message) || 'Не удалось сохранить настройки ТК');
            setResetKey((k) => k + 1);
        },
    });

    const syncMutation = useMutation({
        mutationFn: (code: CarrierDto['code']) => syncCarrierTerminals(code),
        onSuccess: (report) => {
            void queryClient.invalidateQueries({ queryKey: ['adminCarriers'] });
            void queryClient.invalidateQueries({ queryKey: ['carriers'] });
            void queryClient.invalidateQueries({ queryKey: ['deliveryCities'] });
            void queryClient.invalidateQueries({ queryKey: ['deliveryTerminals'] });
            if (report.status === 'OK') messageApi.success(`${report.carrierName}: терминалы обновлены`);
            else messageApi.warning(`${report.carrierName}: ${report.message ?? SYNC_STATUS_TAGS[report.status].label}`);
        },
        onError: () => messageApi.error('Не удалось запустить синхронизацию'),
    });

    return (
        <>
            <Table<CarrierDto>
                rowKey="code"
                loading={isLoading}
                dataSource={carriers}
                pagination={false}
                scroll={{ x: true }}
                expandable={{
                    rowExpandable: (c) => !!c.lastSyncReport,
                    expandedRowRender: (c) => <SyncReportDetails report={c.lastSyncReport!} />,
                }}
                columns={[
                    {
                        title: 'Транспортная компания',
                        render: (_, c) => (
                            <>
                                {c.name}
                                {!c.apiKeyConfigured && <Tag color="red" style={{ marginLeft: 8 }}>Ключ API не настроен</Tag>}
                                {!c.statusTrackingSupported && <Tag style={{ marginLeft: 8 }}>Трекинг не поддерживается</Tag>}
                            </>
                        ),
                    },
                    {
                        title: 'Включена',
                        render: (_, c) => (
                            <Switch checked={c.enabled} loading={mutation.isPending && mutation.variables?.code === c.code}
                                    onChange={(enabled) => mutation.mutate({ ...c, enabled })} />
                        ),
                    },
                    {
                        title: 'Доставка до адреса',
                        render: (_, c) => (
                            <Switch checked={c.addressDeliveryEnabled} loading={mutation.isPending && mutation.variables?.code === c.code}
                                    onChange={(addressDeliveryEnabled) => mutation.mutate({ ...c, addressDeliveryEnabled })} />
                        ),
                    },
                    {
                        title: 'До терминала',
                        render: (_, c) => (
                            <Switch checked={c.terminalDeliveryEnabled} loading={mutation.isPending && mutation.variables?.code === c.code}
                                    onChange={(terminalDeliveryEnabled) => mutation.mutate({ ...c, terminalDeliveryEnabled })} />
                        ),
                    },
                    {
                        title: 'Терминалы',
                        render: (_, c) => (
                            <div style={{ minWidth: 200 }}>
                                {c.lastSyncReport ? (
                                    <div>
                                        <Tag color={SYNC_STATUS_TAGS[c.lastSyncReport.status].color}>
                                            {SYNC_STATUS_TAGS[c.lastSyncReport.status].label}
                                        </Tag>
                                        <Text type="secondary" style={{ fontSize: 12 }}>
                                            {c.terminalsSyncedAt ? formatDateTime(c.terminalsSyncedAt) : formatDateTime(c.lastSyncReport.finishedAt)}
                                        </Text>
                                    </div>
                                ) : <Text type="secondary" style={{ fontSize: 12 }}>Синхронизаций не было</Text>}
                                <div style={{ fontSize: 12 }}>Активных точек: {c.activeTerminalsCount}</div>
                                <Button size="small" style={{ marginTop: 4 }} disabled={!c.apiKeyConfigured}
                                        loading={syncMutation.isPending && syncMutation.variables === c.code}
                                        onClick={() => syncMutation.mutate(c.code)}>
                                    Обновить терминалы сейчас
                                </Button>
                            </div>
                        ),
                    },
                    {
                        title: 'Ссылка отслеживания',
                        render: (_, c) => (
                            <Input key={resetKey} defaultValue={c.trackingUrlTemplate ?? ''} style={{ width: 280 }}
                                   placeholder="https://tk.ru/track?n={number}"
                                   title="Вместо {number} подставляется номер накладной"
                                   onBlur={(e) => {
                                       const trackingUrlTemplate = e.target.value.trim();
                                       if (trackingUrlTemplate !== (c.trackingUrlTemplate ?? '')) {
                                           mutation.mutate({ ...c, trackingUrlTemplate });
                                       }
                                   }} />
                        ),
                    },
                    {
                        title: 'Порядок',
                        render: (_, c) => (
                            <InputNumber key={resetKey} min={0} defaultValue={c.sortOrder} style={{ width: 80 }}
                                         onBlur={(e) => {
                                             const sortOrder = Number(e.target.value);
                                             if (!Number.isNaN(sortOrder) && sortOrder !== c.sortOrder) {
                                                 mutation.mutate({ ...c, sortOrder });
                                             }
                                         }} />
                        ),
                    },
                ]}
            />
            <Text type="secondary" style={{ display: 'block', marginTop: 12, fontSize: 12 }}>
                Клиент видит только включённые ТК. Терминалы обновляются автоматически каждую ночь; отчёт — в раскрывающейся строке. В ссылке отслеживания {'{number}'} заменяется номером накладной.
            </Text>
        </>
    );
};

export default CarriersTab;
