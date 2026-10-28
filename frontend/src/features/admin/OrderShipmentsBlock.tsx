import { useState } from 'react';
import { App, Alert, Button, Input, Popconfirm, Radio, Select, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { isAxiosError } from 'axios';
import {
  addOrderShipment,
  completeOrderShipments,
  getAdminCarriers,
  refreshShipment,
  unlinkOrderShipment,
} from '@/api/adminLogistics';
import type { CarrierCode, ShipmentMode } from '@/types/delivery';
import type { OrderDto } from '@/types/order';
import { ShipmentStatus } from './logistics/ShipmentStatus';
import { deliveryDateLabel } from '@/utils/deliveryDate';

const SWITCH_REQUIRED_CODE = 'SHIPMENT_MODE_SWITCH_REQUIRED';

/** Блок «Отправления» карточки заказа в админке: накладные, внесение, «Все отправления внесены». */
const OrderShipmentsBlock = ({ order }: { order: OrderDto }) => {
  const { message: messageApi, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [carrierCode, setCarrierCode] = useState<CarrierCode | undefined>(order.carrierCode ?? undefined);
  const [waybill, setWaybill] = useState('');
  const [mode, setMode] = useState<ShipmentMode | undefined>();

  const shipments = order.shipments ?? [];
  const isFirst = shipments.length === 0;
  const awaitingMore = order.shipmentMode === 'MULTIPLE' && !order.shipmentsComplete;

  const { data: carriers = [] } = useQuery({ queryKey: ['adminCarriers'], queryFn: getAdminCarriers });

  const onChanged = () => {
    void queryClient.invalidateQueries({ queryKey: ['adminOrderDetail', order.id] });
    void queryClient.invalidateQueries({ queryKey: ['adminOrders'] });
  };

  const addMutation = useMutation({
    mutationFn: (shipmentMode?: ShipmentMode) =>
      addOrderShipment(order.id, { carrierCode: carrierCode!, waybillNumber: waybill.trim(), shipmentMode }),
    onSuccess: () => {
      messageApi.success('Накладная внесена');
      setWaybill('');
      setMode(undefined);
      onChanged();
    },
    onError: (error) => {
      if (isAxiosError(error) && error.response?.status === 409
        && JSON.stringify(error.response.data ?? '').includes(SWITCH_REQUIRED_CODE)) {
        modal.confirm({
          title: 'Переключить на несколько отправлений?',
          content: 'Заказ был внесён как одно отправление. Для второй накладной нужен режим «Несколькими отправлениями».',
          okText: 'Переключить',
          cancelText: 'Отмена',
          onOk: () => addMutation.mutateAsync('MULTIPLE'),
        });
        return;
      }
      messageApi.error((isAxiosError(error) && error.response?.data?.message) || 'Не удалось внести накладную');
    },
  });

  const refreshMutation = useMutation({
    mutationFn: (id: number) => refreshShipment(id),
    onSuccess: onChanged,
    onError: () => messageApi.error('Не удалось обновить статус'),
  });

  const unlinkMutation = useMutation({
    mutationFn: (id: number) => unlinkOrderShipment(order.id, id),
    onSuccess: () => {
      messageApi.success('Накладная отвязана');
      onChanged();
      void queryClient.invalidateQueries({ queryKey: ['adminShipments'] });
    },
    onError: (error) => messageApi.error(
      (isAxiosError(error) && error.response?.data?.message) || 'Не удалось отвязать накладную'),
  });

  const completeMutation = useMutation({
    mutationFn: () => completeOrderShipments(order.id),
    onSuccess: () => { messageApi.success('Все отправления внесены'); onChanged(); },
    onError: () => messageApi.error('Не удалось завершить внесение'),
  });

  return (
    <div className="rf-card" style={{ marginTop: 16, overflow: 'hidden' }}>
      <div className="rf-card-header">
        <h3>Отправления ({shipments.length})</h3>
        {awaitingMore && <Tag color="orange" style={{ marginLeft: 8 }}>Ожидаются ещё отправления</Tag>}
        <div style={{ flex: 1 }} />
        {awaitingMore && (
          <Button size="small" loading={completeMutation.isPending} onClick={() => completeMutation.mutate()}>
            Все отправления внесены
          </Button>
        )}
      </div>

      {shipments.map((s) => {
        const date = deliveryDateLabel(s);
        return (
        <div key={s.id} style={{ padding: 16, borderBottom: '1px solid var(--line-1)', display: 'flex', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ minWidth: 180 }}>
            <div style={{ fontWeight: 600 }}>{s.carrierName}</div>
            <div className="rf-mono">{s.waybillNumber}</div>
            {(s.routeFrom || s.routeTo) && (
              <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>{s.routeFrom ?? '—'} → {s.routeTo ?? '—'}</div>
            )}
          </div>
          <div style={{ flex: 1, minWidth: 180 }}>
            <ShipmentStatus shipment={s} />
            {date && <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{date}</div>}
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {s.trackingUrl && (
              <Button size="small" href={s.trackingUrl} target="_blank" rel="noopener noreferrer">Отследить</Button>
            )}
            {carriers.find((c) => c.code === s.carrierCode)?.statusTrackingSupported && (
              <Button size="small" loading={refreshMutation.isPending && refreshMutation.variables === s.id}
                      onClick={() => refreshMutation.mutate(s.id)}>
                Обновить статус
              </Button>
            )}
            <Popconfirm title="Отвязать накладную от заказа? Она останется в журнале отправлений" okText="Отвязать" cancelText="Отмена" onConfirm={() => unlinkMutation.mutate(s.id)}>
              <Button size="small" loading={unlinkMutation.isPending && unlinkMutation.variables === s.id}>Отвязать</Button>
            </Popconfirm>
          </div>
        </div>
        );
      })}
      {isFirst && (
        <div style={{ textAlign: 'center', color: 'var(--ink-3)', padding: 24 }}>Накладных пока нет</div>
      )}

      <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12, borderTop: '1px solid var(--line-1)' }}>
        {order.shipmentsComplete && (
          <Alert type="info" showIcon title="Отправления отмечены как внесённые полностью" />
        )}
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <Select
            placeholder="ТК"
            style={{ width: 180 }}
            value={carrierCode}
            onChange={setCarrierCode}
            options={carriers.map((c) => ({ value: c.code, label: c.name }))}
          />
          <Input
            placeholder="Номер накладной"
            style={{ width: 220 }}
            value={waybill}
            onChange={(e) => setWaybill(e.target.value)}
          />
          <Button
            type="primary"
            disabled={!carrierCode || !waybill.trim() || (isFirst && !mode)}
            loading={addMutation.isPending}
            onClick={() => addMutation.mutate(isFirst ? mode : undefined)}
          >
            Внести накладную
          </Button>
        </div>
        {isFirst && (
          <Radio.Group value={mode} onChange={(e) => setMode(e.target.value)}>
            <Radio value="SINGLE">Одним отправлением</Radio>
            <Radio value="MULTIPLE">Несколькими отправлениями</Radio>
          </Radio.Group>
        )}
      </div>
    </div>
  );
};

export default OrderShipmentsBlock;
