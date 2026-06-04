import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { getAdminOrders } from '@/api/adminOrders';
import { OrderStatusLabels } from '@/types/order';
import { extractEnumCode } from '@/utils/enumUtils';
import type { OrderSummaryDto, OrderStatus } from '@/types/order';

const formatPrice = (n: number) =>
  new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(n);

const formatDate = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

const STATUS_BADGE: Record<string, { cls: string; label: string }> = {
  CREATED:               { cls: 'rf-badge-warn rf-badge-dot',    label: 'Новый' },
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
  AWAITING_CONFIRMATION: { cls: 'rf-badge-warn',                 label: 'Ожидает подтверждения' },
};

const KPI_CARDS = [
  { label: 'Выручка',          value: '—', unit: '',   accent: '',       sub: 'Метрики будут настроены' },
  { label: 'Новых заявок',     value: '—', unit: '',   accent: 'navy',   sub: 'За текущий период' },
  { label: 'Товаров в наличии',value: '—', unit: 'SKU',accent: 'warn',   sub: 'В каталоге' },
  { label: 'К отгрузке',       value: '—', unit: '',   accent: 'green',  sub: 'Сегодня' },
];

const DashboardPage = () => {
  const navigate = useNavigate();

  const { data: recentPage, isLoading } = useQuery({
    queryKey: ['dashboardRecentOrders'],
    queryFn: () => getAdminOrders({ page: 0, size: 5 }),
  });

  const recentOrders: OrderSummaryDto[] = recentPage?.content ?? [];

  return (
    <div>
      {/* KPI cards */}
      <div className="rf-kpi-grid">
        {KPI_CARDS.map((kpi) => (
          <div key={kpi.label} className={`rf-kpi-card${kpi.accent ? ' ' + kpi.accent : ''}`}>
            <div className="rf-kpi-label">{kpi.label}</div>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
              <span className="rf-kpi-value">{kpi.value}</span>
              {kpi.unit && <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>{kpi.unit}</span>}
            </div>
            <div className="rf-kpi-sub">{kpi.sub}</div>
          </div>
        ))}
      </div>

      {/* Chart area placeholder */}
      <div style={{ display: 'grid', gridTemplateColumns: '1.6fr 1fr', gap: 16, marginBottom: 16 }}>
        <div className="rf-card">
          <div className="rf-card-header">
            <h3>Выручка по периодам</h3>
          </div>
          <div className="rf-card-body" style={{ textAlign: 'center', padding: '60px 22px', color: 'var(--ink-3)', fontSize: 13 }}>
            График появится после настройки источника данных
          </div>
        </div>
        <div className="rf-card">
          <div className="rf-card-header">
            <h3>Топ категорий</h3>
          </div>
          <div className="rf-card-body" style={{ textAlign: 'center', padding: '60px 22px', color: 'var(--ink-3)', fontSize: 13 }}>
            Данные появятся после настройки
          </div>
        </div>
      </div>

      {/* Recent orders */}
      <div className="rf-card" style={{ overflow: 'hidden' }}>
        <div className="rf-card-header">
          <h3>Последние заявки</h3>
          <div style={{ flex: 1 }} />
          <a
            style={{ fontSize: 13, color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}
            onClick={() => navigate('/admin/orders')}
          >
            Все заявки →
          </a>
        </div>
        <div className="rf-admin-table-wrap">
          <table className="rf-admin-table">
            <thead>
              <tr>
                <th style={{ width: 140 }}>№ заявки</th>
                <th style={{ width: 100 }}>Дата</th>
                <th>Клиент</th>
                <th style={{ width: 160 }}>Статус</th>
                <th style={{ width: 130, textAlign: 'right' }}>Сумма</th>
              </tr>
            </thead>
            <tbody>
              {isLoading ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', padding: 40, color: 'var(--ink-3)' }}>
                    Загрузка…
                  </td>
                </tr>
              ) : recentOrders.length === 0 ? (
                <tr>
                  <td colSpan={5} style={{ textAlign: 'center', padding: 40, color: 'var(--ink-3)' }}>
                    Заявок пока нет
                  </td>
                </tr>
              ) : recentOrders.map((o) => {
                const code = extractEnumCode(o.status);
                const badge = STATUS_BADGE[code] ?? { cls: 'rf-badge-neutral', label: OrderStatusLabels[code as OrderStatus] ?? code };
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
                    <td><span className={`rf-badge ${badge.cls}`}>{badge.label}</span></td>
                    <td className="col-right">
                      <span className="rf-tabular" style={{ fontWeight: 600 }}>{formatPrice(o.totalAmount)}</span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
