import { useState } from 'react';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
    getAllUsers, getAllLegalEntities, changeUserRole, changeUserStatus, verifyLegalEntity,
} from '@/api/adminUsers';
import type { AdminUserDto, LegalEntityDto } from '@/api/adminUsers';

const formatDate = (dateStr: string): string =>
    new Date(dateStr).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });

const PAGE_SIZE = 20;

type Tab = 'individuals' | 'legal';

const VERIFICATION_BADGE: Record<string, { cls: string; label: string }> = {
    PENDING:  { cls: 'rf-badge-warn',    label: 'Ожидает' },
    VERIFIED: { cls: 'rf-badge-success', label: 'Верифицирован' },
    REJECTED: { cls: 'rf-badge-red',     label: 'Отклонён' },
};

const AdminUsersPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    const [tab, setTab] = useState<Tab>('individuals');
    const [searchQuery, setSearchQuery] = useState('');
    const [roleFilter, setRoleFilter] = useState<string | undefined>();
    const [statusFilter, setStatusFilter] = useState<boolean | undefined>();
    const [legalStatusFilter, setLegalStatusFilter] = useState<string | undefined>();
    const [page, setPage] = useState(1);

    const { data: users = [], isLoading: usersLoading, refetch: refetchUsers } = useQuery({
        queryKey: ['adminUsers'],
        queryFn: getAllUsers,
        enabled: tab === 'individuals',
    });

    const { data: legalEntities = [], isLoading: legalLoading, refetch: refetchLegal } = useQuery({
        queryKey: ['adminLegalEntities'],
        queryFn: () => getAllLegalEntities(),
        enabled: tab === 'legal',
    });

    const roleMutation = useMutation({
        mutationFn: ({ id, role }: { id: number; role: string }) => changeUserRole(id, role),
        onSuccess: () => { messageApi.success('Роль обновлена'); queryClient.invalidateQueries({ queryKey: ['adminUsers'] }); },
        onError: () => messageApi.error('Ошибка смены роли'),
    });

    const statusMutation = useMutation({
        mutationFn: ({ id, active }: { id: number; active: boolean }) => changeUserStatus(id, active),
        onSuccess: () => { messageApi.success('Статус обновлён'); queryClient.invalidateQueries({ queryKey: ['adminUsers'] }); },
        onError: () => messageApi.error('Ошибка смены статуса'),
    });

    const verifyMutation = useMutation({
        mutationFn: ({ id }: { id: number }) => verifyLegalEntity(id, 'admin'),
        onSuccess: () => { messageApi.success('Верифицировано'); queryClient.invalidateQueries({ queryKey: ['adminLegalEntities'] }); },
        onError: () => messageApi.error('Ошибка верификации'),
    });

    const switchTab = (t: Tab) => { setTab(t); setSearchQuery(''); setPage(1); };

    // Фильтрация физлиц
    const filteredUsers = users.filter((u) => {
        const matchSearch = searchQuery
            ? u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
              u.firstname.toLowerCase().includes(searchQuery.toLowerCase()) ||
              u.lastname.toLowerCase().includes(searchQuery.toLowerCase())
            : true;
        const matchRole = roleFilter ? u.roles.some((r) => r.name === roleFilter) : true;
        const matchStatus = statusFilter !== undefined ? u.active === statusFilter : true;
        return matchSearch && matchRole && matchStatus;
    });

    // Фильтрация юрлиц
    const filteredLegal = legalEntities.filter((le) => {
        const matchSearch = searchQuery
            ? le.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
              (le.email ?? '').toLowerCase().includes(searchQuery.toLowerCase()) ||
              le.inn.includes(searchQuery)
            : true;
        const matchStatus = legalStatusFilter ? le.verificationStatus === legalStatusFilter : true;
        return matchSearch && matchStatus;
    });

    const isLoading = tab === 'individuals' ? usersLoading : legalLoading;
    const source = tab === 'individuals' ? filteredUsers : filteredLegal;
    const totalPages = Math.ceil(source.length / PAGE_SIZE);
    const paginatedUsers = filteredUsers.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
    const paginatedLegal = filteredLegal.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

    return (
        <div>
            {/* Вкладки */}
            <div className="rf-admin-tabs" style={{ marginBottom: 16 }}>
                <button
                    className={`rf-admin-tab${tab === 'individuals' ? ' active' : ''}`}
                    onClick={() => switchTab('individuals')}
                >
                    Физические лица
                    {users.length > 0 && (
                        <span style={{ marginLeft: 6, fontSize: 11, background: 'var(--surface-3)', borderRadius: 10, padding: '1px 6px' }}>
                            {users.length}
                        </span>
                    )}
                </button>
                <button
                    className={`rf-admin-tab${tab === 'legal' ? ' active' : ''}`}
                    onClick={() => switchTab('legal')}
                >
                    Юридические лица
                    {legalEntities.length > 0 && (
                        <span style={{ marginLeft: 6, fontSize: 11, background: 'var(--surface-3)', borderRadius: 10, padding: '1px 6px' }}>
                            {legalEntities.length}
                        </span>
                    )}
                </button>
            </div>

            {/* Фильтры */}
            <div className="rf-admin-filterbar">
                <div className="rf-admin-search">
                    <svg width="15" height="15" viewBox="0 0 15 15" fill="none">
                        <path d="M10 6.5a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0Zm-.691 3.516a4.5 4.5 0 1 1 .707-.707l2.838 2.837a.5.5 0 0 1-.708.708L9.31 10.016Z" fill="currentColor" fillRule="evenodd" clipRule="evenodd"/>
                    </svg>
                    <input
                        type="text"
                        placeholder={tab === 'individuals' ? 'Поиск по email или имени…' : 'Поиск по названию, ИНН, email…'}
                        value={searchQuery}
                        onChange={(e) => { setSearchQuery(e.target.value); setPage(1); }}
                    />
                </div>

                {tab === 'individuals' && (
                    <>
                        <select
                            value={roleFilter ?? ''}
                            onChange={(e) => { setRoleFilter(e.target.value || undefined); setPage(1); }}
                            style={{ height: 34, padding: '0 10px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)' }}
                        >
                            <option value="">Все роли</option>
                            <option value="ROLE_ADMIN">ADMIN</option>
                            <option value="ROLE_USER">USER</option>
                        </select>
                        <select
                            value={statusFilter === undefined ? '' : String(statusFilter)}
                            onChange={(e) => { const v = e.target.value; setStatusFilter(v === '' ? undefined : v === 'true'); setPage(1); }}
                            style={{ height: 34, padding: '0 10px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)' }}
                        >
                            <option value="">Все статусы</option>
                            <option value="true">Активен</option>
                            <option value="false">Заблокирован</option>
                        </select>
                    </>
                )}

                {tab === 'legal' && (
                    <select
                        value={legalStatusFilter ?? ''}
                        onChange={(e) => { setLegalStatusFilter(e.target.value || undefined); setPage(1); }}
                        style={{ height: 34, padding: '0 10px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)' }}
                    >
                        <option value="">Все статусы</option>
                        <option value="PENDING">Ожидает</option>
                        <option value="VERIFIED">Верифицированы</option>
                        <option value="REJECTED">Отклонённые</option>
                    </select>
                )}

                <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 12 }}>
                    <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>{source.length} записей</span>
                    <button className="rf-btn rf-btn-sm rf-btn-quiet"
                        onClick={() => tab === 'individuals' ? refetchUsers() : refetchLegal()}>
                        <svg width="13" height="13" viewBox="0 0 15 15" fill="none" style={{ marginRight: 4 }}>
                            <path d="M1.85 7.5c0-2.835 2.21-5.15 4.98-5.38l-.37.37a.5.5 0 0 0 .707.708L8.854 1.51a.5.5 0 0 0 0-.707L7.167.116a.5.5 0 1 0-.707.707l.284.284C3.28 1.39.85 4.182.85 7.5c0 3.59 2.91 6.5 6.5 6.5s6.5-2.91 6.5-6.5a.5.5 0 0 0-1 0c0 3.038-2.462 5.5-5.5 5.5S1.85 10.538 1.85 7.5Z" fill="currentColor" fillRule="evenodd" clipRule="evenodd"/>
                        </svg>
                        Обновить
                    </button>
                </div>
            </div>

            <div className="rf-card" style={{ overflow: 'hidden' }}>
                {isLoading ? (
                    <div style={{ padding: 80, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
                ) : (
                    <>
                        <div className="rf-admin-table-wrap">
                            {/* Таблица физлиц */}
                            {tab === 'individuals' && (
                                <table className="rf-admin-table">
                                    <thead>
                                        <tr>
                                            <th style={{ width: 60 }}>ID</th>
                                            <th>Email</th>
                                            <th>Имя</th>
                                            <th style={{ width: 150 }}>Роль</th>
                                            <th style={{ width: 200 }}>Статус</th>
                                            <th style={{ width: 130 }}>Регистрация</th>
                                            <th style={{ width: 50 }} />
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {paginatedUsers.length === 0 ? (
                                            <tr>
                                                <td colSpan={7} style={{ textAlign: 'center', padding: 48, color: 'var(--ink-3)' }}>
                                                    Пользователи не найдены
                                                </td>
                                            </tr>
                                        ) : paginatedUsers.map((record: AdminUserDto) => {
                                            const currentRole = record.roles[0]?.name ?? 'ROLE_USER';
                                            const fullName = [record.lastname, record.firstname].filter(Boolean).join(' ');
                                            return (
                                                <tr key={record.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/users/${record.id}`)}>
                                                    <td className="rf-tabular" style={{ color: 'var(--ink-3)' }}>{record.id}</td>
                                                    <td>
                                                        <a onClick={(e) => { e.stopPropagation(); navigate(`/admin/users/${record.id}`); }}>
                                                            {record.email}
                                                        </a>
                                                    </td>
                                                    <td>{fullName || '—'}</td>
                                                    <td onClick={(e) => e.stopPropagation()}>
                                                        <select
                                                            value={currentRole}
                                                            onChange={(e) => roleMutation.mutate({ id: record.id, role: e.target.value })}
                                                            style={{ fontSize: 12, padding: '2px 6px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', background: 'var(--surface)', color: 'var(--ink-1)' }}
                                                        >
                                                            <option value="ROLE_USER">USER</option>
                                                            <option value="ROLE_ADMIN">ADMIN</option>
                                                        </select>
                                                    </td>
                                                    <td onClick={(e) => e.stopPropagation()}>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                                            <span className={`rf-badge ${record.active ? 'rf-badge-success' : 'rf-badge-red'}`}>
                                                                {record.active ? 'Активен' : 'Заблокирован'}
                                                            </span>
                                                            <select
                                                                value={String(record.active)}
                                                                onChange={(e) => statusMutation.mutate({ id: record.id, active: e.target.value === 'true' })}
                                                                style={{ fontSize: 12, padding: '2px 6px', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', background: 'var(--surface)', color: 'var(--ink-1)' }}
                                                            >
                                                                <option value="true">Активен</option>
                                                                <option value="false">Заблокирован</option>
                                                            </select>
                                                        </div>
                                                    </td>
                                                    <td className="rf-tabular" style={{ color: 'var(--ink-3)' }}>{formatDate(record.createdAt)}</td>
                                                    <td>
                                                        <button
                                                            className="rf-btn rf-btn-sm rf-btn-ghost"
                                                            style={{ padding: '4px 8px' }}
                                                            onClick={(e) => { e.stopPropagation(); navigate(`/admin/users/${record.id}`); }}
                                                        >
                                                            <svg width="14" height="14" viewBox="0 0 15 15" fill="none">
                                                                <path d="M7.5 11C4.803 11 2.53 9.622 1.096 7.5 2.53 5.378 4.803 4 7.5 4c2.697 0 4.97 1.378 6.404 3.5C12.47 9.622 10.197 11 7.5 11Zm0-8C4.308 3 1.656 4.706.076 7.235a.5.5 0 0 0 0 .53C1.656 10.294 4.308 12 7.5 12s5.844-1.706 7.424-4.235a.5.5 0 0 0 0-.53C13.344 4.706 10.692 3 7.5 3Zm0 6.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" fill="currentColor" fillRule="evenodd" clipRule="evenodd"/>
                                                            </svg>
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            )}

                            {/* Таблица юрлиц */}
                            {tab === 'legal' && (
                                <table className="rf-admin-table">
                                    <thead>
                                        <tr>
                                            <th style={{ width: 60 }}>ID</th>
                                            <th>Организация</th>
                                            <th style={{ width: 130 }}>ИНН</th>
                                            <th>Email / Телефон</th>
                                            <th style={{ width: 160 }}>Статус</th>
                                            <th style={{ width: 130 }}>Регистрация</th>
                                            <th style={{ width: 100 }} />
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {paginatedLegal.length === 0 ? (
                                            <tr>
                                                <td colSpan={7} style={{ textAlign: 'center', padding: 48, color: 'var(--ink-3)' }}>
                                                    Юридические лица не найдены
                                                </td>
                                            </tr>
                                        ) : paginatedLegal.map((le: LegalEntityDto) => {
                                            const badge = VERIFICATION_BADGE[le.verificationStatus] ?? { cls: 'rf-badge-neutral', label: le.verificationStatus };
                                            return (
                                                <tr key={le.id} style={{ cursor: 'pointer' }} onClick={() => navigate(`/admin/users/${le.id}?type=legal`)}>
                                                    <td className="rf-tabular" style={{ color: 'var(--ink-3)' }}>{le.id}</td>
                                                    <td>
                                                        <div style={{ fontWeight: 500, fontSize: 13 }}>{le.fullName}</div>
                                                        {le.director && <div style={{ fontSize: 11.5, color: 'var(--ink-3)', marginTop: 1 }}>{le.director}</div>}
                                                    </td>
                                                    <td className="rf-mono" style={{ fontSize: 12 }}>{le.inn}</td>
                                                    <td>
                                                        {le.email && <div style={{ fontSize: 13 }}>{le.email}</div>}
                                                        {le.phone && <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{le.phone}</div>}
                                                    </td>
                                                    <td onClick={(e) => e.stopPropagation()}>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                                            <span className={`rf-badge ${badge.cls}`}>{badge.label}</span>
                                                            {le.verificationStatus === 'PENDING' && (
                                                                <button
                                                                    className="rf-btn rf-btn-sm rf-btn-primary"
                                                                    style={{ fontSize: 11, padding: '2px 8px' }}
                                                                    onClick={(e) => { e.stopPropagation(); verifyMutation.mutate({ id: le.id }); }}
                                                                >
                                                                    Верифицировать
                                                                </button>
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td className="rf-tabular" style={{ color: 'var(--ink-3)' }}>{formatDate(le.createdAt)}</td>
                                                    <td>
                                                        <button
                                                            className="rf-btn rf-btn-sm rf-btn-ghost"
                                                            style={{ padding: '4px 8px' }}
                                                            onClick={(e) => { e.stopPropagation(); navigate(`/admin/users/${le.id}?type=legal`); }}
                                                        >
                                                            <svg width="14" height="14" viewBox="0 0 15 15" fill="none">
                                                                <path d="M7.5 11C4.803 11 2.53 9.622 1.096 7.5 2.53 5.378 4.803 4 7.5 4c2.697 0 4.97 1.378 6.404 3.5C12.47 9.622 10.197 11 7.5 11Zm0-8C4.308 3 1.656 4.706.076 7.235a.5.5 0 0 0 0 .53C1.656 10.294 4.308 12 7.5 12s5.844-1.706 7.424-4.235a.5.5 0 0 0 0-.53C13.344 4.706 10.692 3 7.5 3Zm0 6.5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" fill="currentColor" fillRule="evenodd" clipRule="evenodd"/>
                                                            </svg>
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            )}
                        </div>

                        {totalPages > 1 && (
                            <div className="rf-admin-pagination">
                                <span>Всего {source.length} записей</span>
                                <div className="rf-admin-pagination-pages">
                                    <button className="rf-admin-page-btn" disabled={page === 1} onClick={() => setPage((p) => p - 1)}>‹</button>
                                    {Array.from({ length: totalPages }, (_, i) => i + 1)
                                        .filter((p) => p === 1 || p === totalPages || Math.abs(p - page) <= 2)
                                        .reduce<(number | 'e')[]>((acc, p, idx, arr) => {
                                            if (idx > 0 && p - (arr[idx - 1] as number) > 1) acc.push('e');
                                            acc.push(p);
                                            return acc;
                                        }, [])
                                        .map((p, idx) =>
                                            p === 'e'
                                                ? <span key={`e${idx}`} style={{ padding: '0 4px', color: 'var(--ink-3)' }}>…</span>
                                                : <button key={p} className={`rf-admin-page-btn${page === p ? ' active' : ''}`} onClick={() => setPage(p as number)}>{p}</button>
                                        )}
                                    <button className="rf-admin-page-btn" disabled={page === totalPages} onClick={() => setPage((p) => p + 1)}>›</button>
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
};

export default AdminUsersPage;
