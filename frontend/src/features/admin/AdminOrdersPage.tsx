import { useState } from 'react';
import { Select, App, DatePicker } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getAdminOrders, changeOrderStatus } from '@/api/adminOrders';
import { OrderStatus, OrderStatusLabels } from '@/types/order';
import { extractEnumCode } from '@/utils/enumUtils';
import { getAllowedTransitions } from '@/utils/orderTransitions';
import type { OrderSummaryDto } from '@/types/order';

const { RangePicker } = DatePicker;

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

const STATUS_TABS = [
  { label: 'Все',               value: undefined },
  { label: 'Ждёт согласования', value: 'CREATED' as OrderStatus },
  { label: 'Оплачено',          value: 'PAID' as OrderStatus },
  { label: 'В доставке',        value: 'SHIPPED' as OrderStatus },
  { label: 'Завершено',         value: 'COMPLETED' as OrderStatus },
  { label: 'Отменено',          value: 'CANCELLED' as OrderStatus },
];

const AdminOrdersPage = () => {
  const navigate = useNavigate();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();

  const [currentPage, setCurrentPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<OrderStatus | undefined>();
  const [searchQuery, setSearchQuery] = useState('');
  const [dateRange, setDateRange] = useState<[string, string] | null>(null);
  const pageSize = 15;

  const queryParams = {
    page: currentPage - 1,
    size: pageSize,
    status: statusFilter,
    dateFrom: dateRange?.[0],
    dateTo: dateRange?.[1],
  };

  const { data: ordersPage, isLoading } = useQuery({
    queryKey: ['adminOrders', queryParams],
    queryFn: () => getAdminOrders(queryParams),
  });

  const statusMutation = useMutation({
    mutationFn: ({ orderId, status }: { orderId: string; status: string }) =>
      changeOrderStatus(orderId, status),
    onSuccess: () => {
      messageApi.success('Статус обновлён');
      queryClient.invalidateQueries({ queryKey: ['adminOrders'] });
    },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  const orders: OrderSummaryDto[] = (ordersPage?.content ?? []).filter((o) =>
    searchQuery
      ? o.orderNumber.toLowerCase().includes(searchQuery.toLowerCase()) ||
        o.customerEmail.toLowerCase().includes(searchQuery.toLowerCase())
      : true,
  );

  const total = ordersPage?.totalElements ?? 0;
  const totalPages = Math.ceil(total / pageSize);

  return (
    <div>
      {/* Status tabs */}
      <div className="rf-admin-tabs">
        {STATUS_TABS.map((tab) => (
          <button
            key={tab.label}
            className={`rf-admin-tab${statusFilter === tab.value ? ' active' : ''}`}
            onClick={() => { setStatusFilter(tab.value); setCurrentPage(1); }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Filter bar */}
      <div className="rf-admin-filterbar">
        <div className="rf-admin-search" style={{ width: 280 }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5">
            <circle cx="6" cy="6" r="4"/><path d="M10 10l2.5 2.5"/>
          </svg>
          <input
            placeholder="№ заявки, клиент…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <RangePicker
          size="small"
          onChange={(dates) => {
            if (dates?.[0] && dates?.[1]) {
              setDateRange([
                dates[0].startOf('day').toISOString(),
                dates[1].endOf('day').toISOString(),
              ]);
              setCurrentPage(1);
            } else {
              setDateRange(null);
            }
          }}
        />
        <div style={{ flex: 1 }} />
        <button className="rf-btn rf-btn-quiet rf-btn-sm">Выгрузить XLS</button>
        <button className="rf-btn rf-btn-primary rf-btn-sm">+ Новая заявка</button>
      </div>

      {/* Table */}
      <div className="rf-card" style={{ overflow: 'hidden' }}>
        <div className="rf-admin-table-wrap">
          <table className="rf-admin-table">
            <thead>
              <tr>
                <th style={{ width: 140 }}>№ заявки</th>
                <th style={{ width: 100 }}>Дата</th>
                <th>Клиент</th>
                <th style={{ width: 60 }}>Тип</th>
                <th style={{ width: 220 }}>Статус</th>
                <th style={{ width: 130, textAlign: 'right' }}>Сумма</th>
                <th style={{ width: 40 }} />
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: 60, color: 'var(--ink-3)' }}>
                    Загрузка…
                  </td>
                </tr>
              ) : orders.length === 0 ? (
                <tr>
                  <td colSpan={7} style={{ textAlign: 'center', padding: 60, color: 'var(--ink-3)' }}>
                    Заявок нет
                  </td>
                </tr>
              ) : orders.map((o) => {
                const code = extractEnumCode(o.status);
                const badge = STATUS_BADGE[code] ?? { cls: 'rf-badge-neutral', label: OrderStatusLabels[code as OrderStatus] ?? code };
                const transitions = getAllowedTransitions(code);
                const ctCode = extractEnumCode(o.customerType);

                return (
                  <tr
                    key={o.id}
                    style={{ cursor: 'pointer' }}
                    onClick={() => navigate(`/admin/orders/${o.id}`)}
                  >
                    <td>
                      <span className="rf-mono" style={{ fontSize: 12.5, fontWeight: 600, color: 'var(--brand-navy)' }}>
                        {o.orderNumber}
                      </span>
                    </td>
                    <td>
                      <span className="rf-tabular" style={{ color: 'var(--ink-3)', fontSize: 12 }}>
                        {formatDate(o.createdAt)}
                      </span>
                    </td>
                    <td>
                      <div style={{ fontWeight: 500, fontSize: 13 }}>{o.customerEmail}</div>
                      {(o.customerName || o.companyName) && (
                        <div style={{ fontSize: 11.5, color: 'var(--ink-3)', marginTop: 1 }}>
                          {o.customerName || o.companyName}
                        </div>
                      )}
                    </td>
                    <td>
                      <span className={`rf-badge ${ctCode === 'B2B' ? 'rf-badge-navy' : 'rf-badge-neutral'}`} style={{ fontSize: 11 }}>
                        {ctCode || '—'}
                      </span>
                    </td>
                    <td onClick={(e) => e.stopPropagation()}>
                      {transitions.length > 0 ? (
                        <Select
                          size="small"
                          style={{ width: 200 }}
                          value={code}
                          options={[
                            { value: code, label: OrderStatusLabels[code as OrderStatus] ?? code, disabled: true },
                            ...transitions.map((s) => ({ value: s, label: OrderStatusLabels[s] ?? s })),
                          ]}
                          onChange={(newStatus) => statusMutation.mutate({ orderId: o.id, status: newStatus })}
                        />
                      ) : (
                        <span className={`rf-badge ${badge.cls}`}>{badge.label}</span>
                      )}
                    </td>
                    <td className="col-right">
                      <span className="rf-tabular" style={{ fontWeight: 600 }}>
                        {formatPrice(o.totalAmount)}
                      </span>
                    </td>
                    <td>
                      <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="var(--ink-3)" strokeWidth="1.5">
                        <path d="M6 4l4 4-4 4"/>
                      </svg>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="rf-admin-pagination">
          <span>Показано {orders.length} из {total}</span>
          <div className="rf-admin-pagination-pages">
            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => i + 1).map((page) => (
              <button
                key={page}
                className={`rf-admin-page-btn${currentPage === page ? ' active' : ''}`}
                onClick={() => setCurrentPage(page)}
              >
                {page}
              </button>
            ))}
            {totalPages > 5 && <span style={{ padding: '0 4px' }}>…</span>}
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminOrdersPage;
