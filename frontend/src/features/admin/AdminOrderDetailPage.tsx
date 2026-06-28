import { useParams } from 'react-router-dom';
import { NavLink } from '@/components/navigation';
import { Select, App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { changeOrderStatus } from '@/api/adminOrders';
import { OrderStatus, OrderStatusLabels, PaymentMethodLabels, DeliveryMethodLabels } from '@/types/order';
import { extractEnumCode, extractEnumDisplayName } from '@/utils/enumUtils';
import { getAllowedTransitions } from '@/utils/orderTransitions';
import type { OrderDto, OrderItemDto } from '@/types/order';

const formatPrice = (n: number) =>
  new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(n);

const formatDate = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const STATUS_BADGE: Record<string, { cls: string; label: string }> = {
  CREATED:               { cls: 'rf-badge-warn rf-badge-dot',    label: 'Ждёт согласования' },
  PENDING_PAYMENT:       { cls: 'rf-badge-warn rf-badge-dot',    label: 'Ждёт оплаты' },
  PAID:                  { cls: 'rf-badge-success rf-badge-dot', label: 'Оплачено' },
  PROCESSING:            { cls: 'rf-badge-navy',                 label: 'В обработке' },
  INVOICE_SENT:          { cls: 'rf-badge-navy',                 label: 'Счёт выставлен' },
  SHIPPED:               { cls: 'rf-badge-navy rf-badge-dot',    label: 'В доставке' },
  IN_TRANSIT:            { cls: 'rf-badge-navy rf-badge-dot',    label: 'В пути' },
  DELIVERED:             { cls: 'rf-badge-neutral',              label: 'Доставлено' },
  COMPLETED:             { cls: 'rf-badge-neutral',              label: 'Завершено' },
  PAYMENT_FAILED:        { cls: 'rf-badge-red',                  label: 'Ошибка оплаты' },
  CANCELLED:             { cls: 'rf-badge-red',                  label: 'Отменено' },
  REFUNDED:              { cls: 'rf-badge-neutral',              label: 'Возврат' },
  AWAITING_CONFIRMATION: { cls: 'rf-badge-warn',                 label: 'Ожид. подтверждения' },
};

const AdminOrderDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();

  const { data: order, isLoading } = useQuery({
    queryKey: ['adminOrderDetail', id],
    queryFn: async () => {
      const { data } = await apiClient.get<OrderDto>(`/v1/admin/orders/${id}`);
      return data;
    },
    enabled: !!id,
  });

  const statusMutation = useMutation({
    mutationFn: (newStatus: string) => changeOrderStatus(id!, newStatus),
    onSuccess: () => {
      messageApi.success('Статус обновлён');
      queryClient.invalidateQueries({ queryKey: ['adminOrderDetail', id] });
      queryClient.invalidateQueries({ queryKey: ['adminOrders'] });
    },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  if (isLoading || !order) {
    return (
      <div style={{ textAlign: 'center', padding: 120, color: 'var(--ink-3)' }}>Загрузка…</div>
    );
  }

  const statusCode = extractEnumCode(order.status);
  const transitions = getAllowedTransitions(statusCode);
  const badge = STATUS_BADGE[statusCode] ?? { cls: 'rf-badge-neutral', label: statusCode };
  const paymentCode = extractEnumCode(order.paymentMethod);
  const deliveryCode = extractEnumCode(order.deliveryMethod);

  return (
    <div>
      {/* Back */}
      <NavLink to="/admin/orders" className="rf-admin-back">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
          <path d="M10 4L6 8l4 4"/>
        </svg>
        Заказы
      </NavLink>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
        <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, margin: 0 }}>
          Заказ {order.orderNumber}
        </h2>
        <span className={`rf-badge ${badge.cls}`}>{badge.label}</span>
      </div>

      {/* Order info */}
      <div className="rf-card" style={{ marginBottom: 16, overflow: 'hidden' }}>
        <div className="rf-card-header"><h3>Основная информация</h3></div>
        <div className="rf-detail-grid">
          <div className="rf-detail-label">Статус</div>
          <div className="rf-detail-value">
            {transitions.length > 0 ? (
              <Select
                size="small"
                style={{ width: 220 }}
                value={statusCode}
                options={[
                  { value: statusCode, label: OrderStatusLabels[statusCode as OrderStatus] ?? statusCode, disabled: true },
                  ...transitions.map((s) => ({ value: s, label: OrderStatusLabels[s as OrderStatus] ?? s })),
                ]}
                onChange={(s) => statusMutation.mutate(s)}
                loading={statusMutation.isPending}
              />
            ) : (
              <span className={`rf-badge ${badge.cls}`}>{badge.label}</span>
            )}
          </div>

          <div className="rf-detail-label">Дата создания</div>
          <div className="rf-detail-value rf-tabular">{formatDate(order.createdAt)}</div>

          <div className="rf-detail-label">Клиент</div>
          <div className="rf-detail-value">
            {order.customerName && (
              <div style={{ fontWeight: 500 }}>{order.customerName}</div>
            )}
            <div style={{ color: order.customerName ? 'var(--ink-3)' : undefined }}>{order.customerEmail}</div>
            {order.customerPhone && (
              <div style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 2 }}>{order.customerPhone}</div>
            )}
          </div>

          <div className="rf-detail-label">Способ оплаты</div>
          <div className="rf-detail-value">
            {PaymentMethodLabels[paymentCode as keyof typeof PaymentMethodLabels]
              ?? extractEnumDisplayName(order.paymentMethod, paymentCode)}
          </div>

          <div className="rf-detail-label">Доставка</div>
          <div className="rf-detail-value">
            {DeliveryMethodLabels[deliveryCode as keyof typeof DeliveryMethodLabels]
              ?? extractEnumDisplayName(order.deliveryMethod, deliveryCode)}
          </div>

          {order.trackingNumber && (
            <>
              <div className="rf-detail-label">Трек-номер</div>
              <div className="rf-detail-value rf-mono">{order.trackingNumber}</div>
            </>
          )}
          {order.comment && (
            <>
              <div className="rf-detail-label">Комментарий</div>
              <div className="rf-detail-value" style={{ gridColumn: '2' }}>{order.comment}</div>
            </>
          )}
        </div>
      </div>

      {/* Delivery address */}
      {order.deliveryAddress && (
        <div className="rf-card" style={{ marginBottom: 16, overflow: 'hidden' }}>
          <div className="rf-card-header"><h3>Адрес доставки</h3></div>
          <div className="rf-detail-grid">
            <div className="rf-detail-label">Получатель</div>
            <div className="rf-detail-value">{order.deliveryAddress.recipientName}</div>
            <div className="rf-detail-label">Телефон</div>
            <div className="rf-detail-value">{order.deliveryAddress.phone}</div>
            <div className="rf-detail-label">Адрес</div>
            <div className="rf-detail-value">
              {[
                order.deliveryAddress.postalCode,
                order.deliveryAddress.city,
                order.deliveryAddress.street,
                `д. ${order.deliveryAddress.building}`,
                order.deliveryAddress.apartment ? `кв. ${order.deliveryAddress.apartment}` : null,
              ].filter(Boolean).join(', ')}
            </div>
          </div>
        </div>
      )}

      {/* Items */}
      <div className="rf-card" style={{ overflow: 'hidden' }}>
        <div className="rf-card-header">
          <h3>Позиции ({order.items?.length ?? 0})</h3>
          <div style={{ flex: 1 }} />
          <span style={{ fontWeight: 700, fontSize: 15, fontVariantNumeric: 'tabular-nums' }}>
            {formatPrice(order.totalAmount)}
          </span>
        </div>
        <div className="rf-admin-table-wrap">
          <table className="rf-admin-table">
            <thead>
              <tr>
                <th>Товар</th>
                <th style={{ width: 80, textAlign: 'right' }}>Кол-во</th>
                <th style={{ width: 130, textAlign: 'right' }}>Цена</th>
                <th style={{ width: 130, textAlign: 'right' }}>Сумма</th>
              </tr>
            </thead>
            <tbody>
              {(order.items ?? []).map((item: OrderItemDto) => (
                <tr key={item.productId}>
                  <td>{item.productName}</td>
                  <td className="col-right rf-tabular">{item.quantity}</td>
                  <td className="col-right rf-tabular">{formatPrice(item.price)}</td>
                  <td className="col-right rf-tabular" style={{ fontWeight: 600 }}>
                    {formatPrice(item.price * item.quantity)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default AdminOrderDetailPage;
