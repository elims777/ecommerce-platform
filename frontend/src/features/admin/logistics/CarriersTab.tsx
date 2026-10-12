import { App, InputNumber, Switch, Table, Typography } from 'antd';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getAdminCarriers, updateCarrier } from '@/api/adminLogistics';
import type { CarrierDto } from '@/types/delivery';

const { Text } = Typography;

/** Включение ТК и доставки до адреса, порядок вывода клиенту. */
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
            sortOrder: carrier.sortOrder,
        }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCarriers'] });
            queryClient.invalidateQueries({ queryKey: ['carriers'] });
        },
        onError: () => {
            messageApi.error('Не удалось сохранить настройки ТК');
            setResetKey((k) => k + 1);
        },
    });

    return (
        <>
            <Table<CarrierDto>
                rowKey="code"
                loading={isLoading}
                dataSource={carriers}
                pagination={false}
                scroll={{ x: true }}
                columns={[
                    { title: 'Транспортная компания', dataIndex: 'name' },
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
                Клиент видит только включённые ТК. Доставка до терминала появится после подключения API ТК.
            </Text>
        </>
    );
};

export default CarriersTab;
