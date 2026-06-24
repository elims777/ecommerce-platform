import { useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { RowLink } from '@/components/navigation';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  getUserById, changeUserRole, changeUserStatus, updateUserAdmin,
  getUserLegalEntities, verifyLegalEntity, detachLegalEntity,
} from '@/api/adminUsers';
import type { UpdateUserAdminRequest, LegalEntityDto } from '@/api/adminUsers';
import { getAdminOrders } from '@/api/adminOrders';
import { OrderStatusLabels } from '@/types/order';
import { extractEnumCode } from '@/utils/enumUtils';
import { useAuthStore } from '@/store/authStore';
import type { OrderSummaryDto, OrderStatus } from '@/types/order';

const formatPrice = (price: number) =>
  new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(price);

const formatDate = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });

const formatDateTime = (d: string) =>
  new Date(d).toLocaleDateString('ru-RU', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });

const ORDER_STATUS_BADGE: Record<string, string> = {
  CREATED: 'rf-badge-warn rf-badge-dot',
  PENDING_PAYMENT: 'rf-badge-warn rf-badge-dot',
  PAID: 'rf-badge-success',
  DELIVERED: 'rf-badge-success',
  COMPLETED: 'rf-badge-success',
  PROCESSING: 'rf-badge-navy',
  INVOICE_SENT: 'rf-badge-navy',
  SHIPPED: 'rf-badge-navy rf-badge-dot',
  IN_TRANSIT: 'rf-badge-navy rf-badge-dot',
  PAYMENT_FAILED: 'rf-badge-red',
  CANCELLED: 'rf-badge-red',
  REFUNDED: 'rf-badge-neutral',
  AWAITING_CONFIRMATION: 'rf-badge-neutral',
};

const LE_STATUS_BADGE: Record<string, string> = {
  VERIFIED: 'rf-badge-success',
  PENDING: 'rf-badge-warn',
  REJECTED: 'rf-badge-red',
};
const LE_STATUS_LABEL: Record<string, string> = {
  VERIFIED: 'Верифицирована',
  PENDING: 'На проверке',
  REJECTED: 'Отклонена',
};

const AdminUserDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { message: messageApi } = App.useApp();
  const queryClient = useQueryClient();
  const userId = Number(id);
  const adminEmail = useAuthStore((s) => s.user?.email ?? '');

  const dialogRef = useRef<HTMLDialogElement>(null);
  const [editForm, setEditForm] = useState<UpdateUserAdminRequest>({ firstname: '', lastname: '', phone: '' });

  const { data: user, isLoading: userLoading } = useQuery({
    queryKey: ['adminUser', userId],
    queryFn: () => getUserById(userId),
    enabled: !!userId,
  });

  const { data: ordersPage, isLoading: ordersLoading } = useQuery({
    queryKey: ['adminUserOrders', userId],
    queryFn: () => getAdminOrders({ userId, page: 0, size: 10 }),
    enabled: !!userId,
  });

  const { data: legalEntities = [], isLoading: leLoading } = useQuery({
    queryKey: ['adminUserLegalEntities', userId],
    queryFn: () => getUserLegalEntities(userId),
    enabled: !!userId,
  });

  const invalidateUser = () => {
    queryClient.invalidateQueries({ queryKey: ['adminUser', userId] });
    queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
  };

  const roleMutation = useMutation({
    mutationFn: (role: string) => changeUserRole(userId, role),
    onSuccess: () => { messageApi.success('Роль обновлена'); invalidateUser(); },
    onError: () => messageApi.error('Ошибка смены роли'),
  });

  const statusMutation = useMutation({
    mutationFn: (active: boolean) => changeUserStatus(userId, active),
    onSuccess: () => { messageApi.success('Статус обновлён'); invalidateUser(); },
    onError: () => messageApi.error('Ошибка смены статуса'),
  });

  const editMutation = useMutation({
    mutationFn: (body: UpdateUserAdminRequest) => updateUserAdmin(userId, body),
    onSuccess: () => {
      messageApi.success('Данные обновлены');
      dialogRef.current?.close();
      invalidateUser();
    },
    onError: () => messageApi.error('Ошибка обновления данных'),
  });

  const verifyMutation = useMutation({
    mutationFn: (legalEntityId: number) => verifyLegalEntity(legalEntityId, adminEmail),
    onSuccess: () => {
      messageApi.success('Организация верифицирована');
      queryClient.invalidateQueries({ queryKey: ['adminUserLegalEntities', userId] });
    },
    onError: () => messageApi.error('Ошибка верификации'),
  });

  const detachMutation = useMutation({
    mutationFn: (legalEntityId: number) => detachLegalEntity(legalEntityId, userId),
    onSuccess: () => {
      messageApi.success('Организация отвязана');
      queryClient.invalidateQueries({ queryKey: ['adminUserLegalEntities', userId] });
    },
    onError: () => messageApi.error('Ошибка отвязки организации'),
  });

  const openEditModal = () => {
    if (!user) return;
    setEditForm({ firstname: user.firstname, lastname: user.lastname, phone: user.phone ?? '' });
    dialogRef.current?.showModal();
  };

  if (userLoading || !user) {
    return <div style={{ textAlign: 'center', padding: 120, color: 'var(--ink-3)' }}>Загрузка…</div>;
  }

  const currentRole = user.roles[0]?.name ?? 'USER';

  return (
    <div>
      {/* Back */}
      <div className="rf-admin-back" onClick={() => navigate('/admin/users')}>
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
          <path d="M10 4L6 8l4 4"/>
        </svg>
        Пользователи
      </div>

      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
        <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, margin: 0 }}>
          {user.lastname} {user.firstname}
        </h2>
        <span className={`rf-badge ${user.active ? 'rf-badge-success' : 'rf-badge-red'}`}>
          {user.active ? 'Активен' : 'Заблокирован'}
        </span>
        <span className="rf-badge rf-badge-navy">{currentRole}</span>
      </div>

      {/* Личные данные */}
      <div className="rf-card" style={{ marginBottom: 16, overflow: 'hidden' }}>
        <div className="rf-card-header">
          <h3>Личные данные</h3>
          <div style={{ flex: 1 }} />
          <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={openEditModal}>
            Редактировать
          </button>
        </div>
        <div className="rf-detail-grid">
          <div className="rf-detail-label">Email</div>
          <div className="rf-detail-value" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {user.email}
            <button
              className="rf-btn rf-btn-sm rf-btn-ghost"
              style={{ height: 24, padding: '0 8px', fontSize: 11 }}
              onClick={() => navigator.clipboard.writeText(user.email).then(() => messageApi.success('Скопировано'))}
            >
              копировать
            </button>
          </div>

          <div className="rf-detail-label">Телефон</div>
          <div className="rf-detail-value">{user.phone ?? '—'}</div>

          <div className="rf-detail-label">Имя</div>
          <div className="rf-detail-value">{user.firstname}</div>

          <div className="rf-detail-label">Фамилия</div>
          <div className="rf-detail-value">{user.lastname}</div>

          <div className="rf-detail-label">Дата регистрации</div>
          <div className="rf-detail-value rf-tabular">{formatDate(user.createdAt)}</div>

          <div className="rf-detail-label">Email подтверждён</div>
          <div className="rf-detail-value">
            <span className={`rf-badge ${user.emailVerified ? 'rf-badge-success' : 'rf-badge-warn'}`}>
              {user.emailVerified ? 'Да' : 'Нет'}
            </span>
          </div>

          <div className="rf-detail-label">Роль</div>
          <div className="rf-detail-value">
            <select
              value={currentRole}
              onChange={(e) => roleMutation.mutate(e.target.value)}
              disabled={roleMutation.isPending}
              style={{ fontSize: 13, padding: '3px 8px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', background: 'var(--surface)', color: 'var(--ink-1)' }}
            >
              <option value="USER">USER</option>
              <option value="ADMIN">ADMIN</option>
            </select>
          </div>

          <div className="rf-detail-label">Статус</div>
          <div className="rf-detail-value">
            <select
              value={String(user.active)}
              onChange={(e) => statusMutation.mutate(e.target.value === 'true')}
              disabled={statusMutation.isPending}
              style={{ fontSize: 13, padding: '3px 8px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', background: 'var(--surface)', color: 'var(--ink-1)' }}
            >
              <option value="true">Активен</option>
              <option value="false">Заблокирован</option>
            </select>
          </div>
        </div>
      </div>

      {/* Заказы */}
      <div className="rf-card" style={{ marginBottom: 16, overflow: 'hidden' }}>
        <div className="rf-card-header">
          <h3>Заказы</h3>
        </div>
        {ordersLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
        ) : (ordersPage?.content ?? []).length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)' }}>Заказов нет</div>
        ) : (
          <div className="rf-admin-table-wrap">
            <table className="rf-admin-table">
              <thead>
                <tr>
                  <th>№</th>
                  <th>Статус</th>
                  <th style={{ textAlign: 'right' }}>Сумма</th>
                  <th>Дата</th>
                </tr>
              </thead>
              <tbody>
                {(ordersPage?.content ?? []).map((order: OrderSummaryDto) => {
                  const code = extractEnumCode(order.status);
                  const badgeCls = ORDER_STATUS_BADGE[code] ?? 'rf-badge-neutral';
                  return (
                    <tr key={order.id}>
                      <td>
                        <RowLink to={`/admin/orders/${order.id}`} style={{ color: 'var(--brand-navy)' }}>
                          {order.orderNumber}
                        </RowLink>
                      </td>
                      <td>
                        <RowLink to={`/admin/orders/${order.id}`}>
                          <span className={`rf-badge ${badgeCls}`}>
                            {OrderStatusLabels[code as OrderStatus] ?? code}
                          </span>
                        </RowLink>
                      </td>
                      <td className="col-right">
                        <RowLink to={`/admin/orders/${order.id}`}>
                          <span className="rf-tabular">{formatPrice(order.totalAmount)}</span>
                        </RowLink>
                      </td>
                      <td>
                        <RowLink to={`/admin/orders/${order.id}`}>
                          <span className="rf-tabular">{formatDateTime(order.createdAt)}</span>
                        </RowLink>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Юридические лица */}
      <div className="rf-card" style={{ overflow: 'hidden' }}>
        <div className="rf-card-header">
          <h3>Юридические лица</h3>
        </div>
        {leLoading ? (
          <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
        ) : legalEntities.length === 0 ? (
          <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)' }}>Нет привязанных организаций</div>
        ) : (
          <div className="rf-admin-table-wrap">
            <table className="rf-admin-table">
              <thead>
                <tr>
                  <th>Компания</th>
                  <th style={{ width: 120 }}>ИНН</th>
                  <th style={{ width: 140 }}>Статус</th>
                  <th style={{ width: 110 }}>Добавлена</th>
                  <th style={{ width: 80 }}></th>
                </tr>
              </thead>
              <tbody>
                {legalEntities.map((le: LegalEntityDto) => (
                  <tr key={le.id}>
                    <td>
                      <RowLink to={`/admin/legal-entities/${le.id}`}>{le.fullName}</RowLink>
                    </td>
                    <td>
                      <RowLink to={`/admin/legal-entities/${le.id}`}>
                        <span className="rf-mono">{le.inn}</span>
                      </RowLink>
                    </td>
                    <td>
                      <RowLink to={`/admin/legal-entities/${le.id}`}>
                        <span className={`rf-badge ${LE_STATUS_BADGE[le.verificationStatus] ?? 'rf-badge-neutral'}`}>
                          {LE_STATUS_LABEL[le.verificationStatus] ?? le.verificationStatus}
                        </span>
                      </RowLink>
                    </td>
                    <td>
                      <RowLink to={`/admin/legal-entities/${le.id}`}>
                        <span className="rf-tabular">{formatDate(le.createdAt)}</span>
                      </RowLink>
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: 4 }}>
                        {le.verificationStatus !== 'VERIFIED' && (
                          <button
                            className="rf-btn rf-btn-sm rf-btn-ghost"
                            style={{ color: 'var(--brand-green)', height: 28, padding: '0 8px' }}
                            title="Верифицировать"
                            onClick={() => {
                              if (window.confirm('Верифицировать организацию?')) {
                                verifyMutation.mutate(le.id);
                              }
                            }}
                          >
                            ✓
                          </button>
                        )}
                        <button
                          className="rf-btn rf-btn-sm rf-btn-ghost"
                          style={{ color: 'var(--brand-red)', height: 28, padding: '0 8px' }}
                          title="Отвязать"
                          onClick={() => {
                            if (window.confirm('Отвязать организацию от пользователя?')) {
                              detachMutation.mutate(le.id);
                            }
                          }}
                        >
                          ✕
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Edit dialog */}
      <dialog
        ref={dialogRef}
        style={{ padding: 0, border: 'none', borderRadius: 'var(--r-4)', boxShadow: '0 8px 32px rgba(0,0,0,0.18)', minWidth: 400 }}
        onClick={(e) => { if (e.target === dialogRef.current) dialogRef.current?.close(); }}
      >
        <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--line-1)' }}>
          <h3 style={{ margin: 0, fontFamily: 'var(--font-head)', fontSize: 16, fontWeight: 600 }}>Редактировать данные</h3>
        </div>
        <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
          {(['lastname', 'firstname', 'phone'] as const).map((field) => (
            <label key={field} style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
              <span style={{ color: 'var(--ink-3)', fontWeight: 500 }}>
                {field === 'lastname' ? 'Фамилия' : field === 'firstname' ? 'Имя' : 'Телефон'}
                {field !== 'phone' && <span style={{ color: 'var(--brand-red)' }}> *</span>}
              </span>
              <input
                type="text"
                value={editForm[field] ?? ''}
                placeholder={field === 'phone' ? '+7 900 000 00 00' : ''}
                onChange={(e) => setEditForm((f) => ({ ...f, [field]: e.target.value }))}
                style={{ height: 36, padding: '0 10px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)', outline: 'none' }}
              />
            </label>
          ))}
        </div>
        <div style={{ padding: '12px 24px 20px', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={() => dialogRef.current?.close()}>Отмена</button>
          <button
            className="rf-btn rf-btn-sm rf-btn-primary"
            disabled={editMutation.isPending}
            onClick={() => editMutation.mutate(editForm)}
          >
            {editMutation.isPending ? 'Сохранение…' : 'Сохранить'}
          </button>
        </div>
      </dialog>
    </div>
  );
};

export default AdminUserDetailPage;
