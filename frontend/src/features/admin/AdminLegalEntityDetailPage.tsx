import { useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getLegalEntityById, verifyLegalEntity, rejectLegalEntity } from '@/api/adminUsers';
import { useAuthStore } from '@/store/authStore';

const formatDate = (d: string) =>
    new Date(d).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });

const BADGE: Record<string, { cls: string; label: string }> = {
    VERIFIED: { cls: 'rf-badge-success', label: 'Верифицирована' },
    PENDING:  { cls: 'rf-badge-warn',    label: 'На проверке' },
    REJECTED: { cls: 'rf-badge-red',     label: 'Отклонена' },
};

const Row = ({ label, value }: { label: string; value: React.ReactNode }) => (
    <>
        <div className="rf-detail-label">{label}</div>
        <div className="rf-detail-value">{value ?? '—'}</div>
    </>
);

const AdminLegalEntityDetailPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const legalId = Number(id);
    const adminEmail = useAuthStore((s) => s.user?.email ?? '');

    const rejectDialogRef = useRef<HTMLDialogElement>(null);
    const [rejectReason, setRejectReason] = useState('');

    const { data: le, isLoading } = useQuery({
        queryKey: ['adminLegalEntity', legalId],
        queryFn: () => getLegalEntityById(legalId),
        enabled: !!legalId,
    });

    const invalidate = () => {
        queryClient.invalidateQueries({ queryKey: ['adminLegalEntity', legalId] });
        queryClient.invalidateQueries({ queryKey: ['adminLegalEntities'] });
    };

    const verifyMutation = useMutation({
        mutationFn: () => verifyLegalEntity(legalId, adminEmail),
        onSuccess: () => { messageApi.success('Организация верифицирована'); invalidate(); },
        onError: () => messageApi.error('Ошибка верификации'),
    });

    const rejectMutation = useMutation({
        mutationFn: () => rejectLegalEntity(legalId, adminEmail, rejectReason),
        onSuccess: () => {
            messageApi.success('Организация отклонена');
            rejectDialogRef.current?.close();
            setRejectReason('');
            invalidate();
        },
        onError: () => messageApi.error('Ошибка отклонения'),
    });

    if (isLoading || !le) {
        return <div style={{ textAlign: 'center', padding: 120, color: 'var(--ink-3)' }}>Загрузка…</div>;
    }

    const badge = BADGE[le.verificationStatus] ?? { cls: 'rf-badge-neutral', label: le.verificationStatus };

    return (
        <div>
            <div className="rf-admin-back" onClick={() => navigate('/admin/users?tab=legal')}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <path d="M10 4L6 8l4 4"/>
                </svg>
                Клиенты
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
                <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, margin: 0 }}>
                    {le.fullName}
                </h2>
                <span className={`rf-badge ${badge.cls}`}>{badge.label}</span>
            </div>

            {/* Реквизиты */}
            <div className="rf-card" style={{ marginBottom: 16 }}>
                <div className="rf-card-header">
                    <h3>Реквизиты организации</h3>
                    <div style={{ flex: 1 }} />
                    <div style={{ display: 'flex', gap: 8 }}>
                        {le.verificationStatus !== 'VERIFIED' && (
                            <button
                                className="rf-btn rf-btn-sm rf-btn-primary"
                                disabled={verifyMutation.isPending}
                                onClick={() => {
                                    if (window.confirm('Верифицировать организацию?')) verifyMutation.mutate();
                                }}
                            >
                                Верифицировать
                            </button>
                        )}
                        {le.verificationStatus !== 'REJECTED' && (
                            <button
                                className="rf-btn rf-btn-sm rf-btn-quiet"
                                style={{ color: 'var(--brand-red)' }}
                                onClick={() => rejectDialogRef.current?.showModal()}
                            >
                                Отклонить
                            </button>
                        )}
                    </div>
                </div>
                <div className="rf-detail-grid">
                    <Row label="ИНН" value={<span className="rf-mono">{le.inn}</span>} />
                    <Row label="ОГРН" value={le.ogrn} />
                    <Row label="Руководитель" value={le.director} />
                    <Row label="Должность" value={le.directorTitle} />
                    <Row label="Основание полномочий" value={le.basisOfAuthority} />
                    <Row label="Офис" value={le.office} />
                    <Row label="Email" value={le.email} />
                    <Row label="Телефон" value={le.phone} />
                    <Row label="Юр. адрес" value={[le.legalCity, le.legalStreet, le.legalBuilding, le.legalPostalCode].filter(Boolean).join(', ') || null} />
                    <Row label="Дата регистрации" value={formatDate(le.createdAt)} />
                    {le.verifiedAt && <Row label="Дата верификации" value={formatDate(le.verifiedAt)} />}
                    <Row label="Статус" value={
                        <select
                            value={le.verificationStatus}
                            onChange={(e) => {
                                const v = e.target.value;
                                if (v === 'VERIFIED') {
                                    if (window.confirm('Верифицировать организацию?')) verifyMutation.mutate();
                                } else if (v === 'REJECTED') {
                                    rejectDialogRef.current?.showModal();
                                }
                            }}
                            style={{ fontSize: 13, padding: '3px 8px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', background: 'var(--surface)', color: 'var(--ink-1)' }}
                        >
                            <option value="PENDING">На проверке</option>
                            <option value="VERIFIED">Верифицирована</option>
                            <option value="REJECTED">Отклонена</option>
                        </select>
                    } />
                </div>
            </div>

            {/* Банковские счета */}
            {le.bankAccounts.length > 0 && (
                <div className="rf-card" style={{ marginBottom: 16, overflow: 'hidden' }}>
                    <div className="rf-card-header"><h3>Банковские счета</h3></div>
                    <div className="rf-admin-table-wrap">
                        <table className="rf-admin-table">
                            <thead>
                                <tr>
                                    <th>Банк</th>
                                    <th style={{ width: 120 }}>БИК</th>
                                    <th>Корр. счёт</th>
                                    <th>Расч. счёт</th>
                                    <th style={{ width: 80 }}>Основной</th>
                                </tr>
                            </thead>
                            <tbody>
                                {le.bankAccounts.map((acc) => (
                                    <tr key={acc.id}>
                                        <td>{acc.bankName}</td>
                                        <td className="rf-mono">{acc.bik}</td>
                                        <td className="rf-mono" style={{ fontSize: 12 }}>{acc.correspondentAccount}</td>
                                        <td className="rf-mono" style={{ fontSize: 12 }}>{acc.settlementAccount}</td>
                                        <td style={{ textAlign: 'center' }}>
                                            {acc.primary && <span className="rf-badge rf-badge-success">Да</span>}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Адреса доставки */}
            {le.addresses.length > 0 && (
                <div className="rf-card" style={{ overflow: 'hidden' }}>
                    <div className="rf-card-header"><h3>Адреса доставки</h3></div>
                    <div className="rf-admin-table-wrap">
                        <table className="rf-admin-table">
                            <thead>
                                <tr>
                                    <th>Город</th>
                                    <th>Улица</th>
                                    <th>Дом</th>
                                    <th>Офис</th>
                                    <th style={{ width: 100 }}>Индекс</th>
                                    <th style={{ width: 80 }}>Основной</th>
                                </tr>
                            </thead>
                            <tbody>
                                {le.addresses.map((addr) => (
                                    <tr key={addr.id}>
                                        <td>{addr.city}</td>
                                        <td>{addr.street}</td>
                                        <td>{addr.building}</td>
                                        <td>{addr.apartment ?? '—'}</td>
                                        <td className="rf-mono">{addr.postalCode}</td>
                                        <td style={{ textAlign: 'center' }}>
                                            {addr.primary && <span className="rf-badge rf-badge-success">Да</span>}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Диалог отклонения */}
            <dialog
                ref={rejectDialogRef}
                style={{ padding: 0, border: 'none', borderRadius: 'var(--r-4)', boxShadow: '0 8px 32px rgba(0,0,0,0.18)', minWidth: 400 }}
                onClick={(e) => { if (e.target === rejectDialogRef.current) rejectDialogRef.current?.close(); }}
            >
                <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--line-1)' }}>
                    <h3 style={{ margin: 0, fontFamily: 'var(--font-head)', fontSize: 16, fontWeight: 600 }}>Отклонить организацию</h3>
                </div>
                <div style={{ padding: '20px 24px' }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}>
                        <span style={{ color: 'var(--ink-3)', fontWeight: 500 }}>Причина отклонения</span>
                        <textarea
                            rows={4}
                            value={rejectReason}
                            onChange={(e) => setRejectReason(e.target.value)}
                            placeholder="Опишите причину..."
                            style={{ padding: '8px 10px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)', resize: 'vertical', outline: 'none' }}
                        />
                    </label>
                </div>
                <div style={{ padding: '12px 24px 20px', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={() => { rejectDialogRef.current?.close(); setRejectReason(''); }}>Отмена</button>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-primary"
                        style={{ background: 'var(--brand-red)', borderColor: 'var(--brand-red)' }}
                        disabled={rejectMutation.isPending}
                        onClick={() => rejectMutation.mutate()}
                    >
                        {rejectMutation.isPending ? 'Отклонение…' : 'Отклонить'}
                    </button>
                </div>
            </dialog>
        </div>
    );
};

export default AdminLegalEntityDetailPage;
