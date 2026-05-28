import { useState } from 'react';
import { Form, Input, App, Spin } from 'antd';
import { EditOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { updateProfile } from '@/api/profile';
import type { UpdateProfileRequest } from '@/api/profile';
import type { AxiosError } from 'axios';
import {
    getLinkStatus,
    getLegalEntity,
    registerLegalEntity,
    updateLegalEntity,
    type RegisterLegalEntityRequest,
    type UpdateLegalEntityRequest,
    type LegalEntityResponse,
} from '@/api/legalEntity';

// ─── Общие стили ──────────────────────────────────────────────
const sectionStyle: React.CSSProperties = {
    border: '1px solid var(--line-1)', borderRadius: 8,
    background: 'var(--surface)', overflow: 'hidden', marginBottom: 16,
};
const headerStyle: React.CSSProperties = {
    display: 'flex', alignItems: 'center', justifyContent: 'space-between',
    padding: '14px 20px', borderBottom: '1px solid var(--line-1)', background: 'var(--surface-2)',
};
const inputStyle: React.CSSProperties = {
    height: 40, border: '1px solid var(--line-2)', borderRadius: 6,
    fontFamily: 'var(--font-body)', fontSize: 14,
};
const labelStyle: React.CSSProperties = {
    fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block',
};
const rowStyle: React.CSSProperties = {
    display: 'flex', fontSize: 14, padding: '10px 0', borderBottom: '1px solid var(--line-1)',
};
const rowLast: React.CSSProperties = { ...rowStyle, borderBottom: 'none' };

const Row = ({ label, value, last }: { label: string; value: React.ReactNode; last?: boolean }) => (
    <div style={last ? rowLast : rowStyle}>
        <span style={{ color: 'var(--ink-3)', width: 180, flexShrink: 0 }}>{label}</span>
        <span style={{ fontWeight: 500 }}>{value || '—'}</span>
    </div>
);

const formatDate = (dateStr: string | null | undefined): string => {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
};

// ─── Профиль юрлица ───────────────────────────────────────────
const B2BProfilePage = ({ legalId }: { legalId: number }) => {
    const [editing, setEditing] = useState(false);
    const [form] = Form.useForm<UpdateLegalEntityRequest>();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    const { data: entity, isLoading } = useQuery<LegalEntityResponse>({
        queryKey: ['legal-entity', legalId],
        queryFn: () => getLegalEntity(legalId),
    });

    const updateMutation = useMutation({
        mutationFn: (values: UpdateLegalEntityRequest) => updateLegalEntity(legalId, values),
        onSuccess: (updated) => {
            queryClient.setQueryData(['legal-entity', legalId], updated);
            messageApi.success('Данные сохранены');
            setEditing(false);
        },
        onError: () => { messageApi.error('Ошибка при сохранении'); },
    });

    const startEdit = () => {
        if (!entity) return;
        form.setFieldsValue({
            fullName: entity.fullName,
            director: entity.director,
            directorTitle: entity.directorTitle ?? '',
            basisOfAuthority: entity.basisOfAuthority ?? '',
            office: entity.office ?? '',
            phone: entity.phone,
            legalCity: entity.legalCity,
            legalStreet: entity.legalStreet,
            legalBuilding: entity.legalBuilding,
            legalPostalCode: entity.legalPostalCode ?? '',
        });
        setEditing(true);
    };

    if (isLoading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', paddingTop: 60 }}>
                <Spin size="large" />
            </div>
        );
    }

    if (!entity) return null;

    const address = [entity.legalCity, entity.legalStreet, entity.legalBuilding, entity.office, entity.legalPostalCode]
        .filter(Boolean).join(', ');

    const statusBadge = entity.verificationStatus === 'VERIFIED'
        ? { bg: 'var(--brand-green-soft)', color: 'var(--brand-green)', label: 'Верифицирована' }
        : { bg: 'var(--warn-tint)', color: 'var(--warn)', label: 'На проверке' };

    const editBtn = (
        <button onClick={startEdit} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 30, padding: '0 12px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
            <EditOutlined style={{ fontSize: 12 }} /> Редактировать
        </button>
    );

    if (editing) {
        return (
            <div style={{ maxWidth: 700, margin: '0 auto', paddingTop: 20, paddingBottom: 60 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
                    <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', margin: 0 }}>
                        Профиль организации
                    </h1>
                    <div style={{ display: 'flex', gap: 8 }}>
                        <button onClick={() => setEditing(false)} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 36, padding: '0 14px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                            <CloseOutlined style={{ fontSize: 11 }} /> Отмена
                        </button>
                        <button onClick={() => form.submit()} disabled={updateMutation.isPending} style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 36, padding: '0 16px', border: 'none', background: 'var(--brand-navy)', color: '#fff', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: updateMutation.isPending ? 'not-allowed' : 'pointer', fontFamily: 'var(--font-body)', opacity: updateMutation.isPending ? 0.7 : 1 }}>
                            <SaveOutlined style={{ fontSize: 12 }} /> {updateMutation.isPending ? 'Сохранение...' : 'Сохранить'}
                        </button>
                    </div>
                </div>

                <Form form={form} layout="vertical" onFinish={(v) => updateMutation.mutate(v)}>
                    {/* Организация */}
                    <div style={sectionStyle}>
                        <div style={headerStyle}>
                            <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Организация</span>
                        </div>
                        <div style={{ padding: 20 }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px' }}>
                                <div style={{ gridColumn: '1 / -1', marginBottom: 14 }}>
                                    <label style={labelStyle}>Полное наименование</label>
                                    <Form.Item name="fullName" noStyle rules={[{ required: true, message: 'Обязательное поле' }]}>
                                        <Input style={inputStyle} />
                                    </Form.Item>
                                </div>
                                <div style={{ marginBottom: 14 }}>
                                    <label style={labelStyle}>Руководитель (ФИО)</label>
                                    <Form.Item name="director" noStyle rules={[{ required: true, message: 'Обязательное поле' }]}>
                                        <Input style={inputStyle} placeholder="Иванов Иван Иванович" />
                                    </Form.Item>
                                </div>
                                <div style={{ marginBottom: 14 }}>
                                    <label style={labelStyle}>Должность руководителя</label>
                                    <Form.Item name="directorTitle" noStyle>
                                        <Input style={inputStyle} placeholder="Генеральный директор" />
                                    </Form.Item>
                                </div>
                                <div style={{ gridColumn: '1 / -1', marginBottom: 14 }}>
                                    <label style={labelStyle}>Действует на основании</label>
                                    <Form.Item name="basisOfAuthority" noStyle>
                                        <Input style={inputStyle} placeholder="Устава, доверенности №... от..." />
                                    </Form.Item>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Юридический адрес */}
                    <div style={sectionStyle}>
                        <div style={headerStyle}>
                            <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Юридический адрес</span>
                        </div>
                        <div style={{ padding: 20 }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px' }}>
                                <div style={{ gridColumn: '1 / -1', marginBottom: 14 }}>
                                    <label style={labelStyle}>Город</label>
                                    <Form.Item name="legalCity" noStyle rules={[{ required: true, message: 'Обязательное поле' }]}>
                                        <Input style={inputStyle} />
                                    </Form.Item>
                                </div>
                                <div style={{ marginBottom: 14 }}>
                                    <label style={labelStyle}>Улица</label>
                                    <Form.Item name="legalStreet" noStyle rules={[{ required: true, message: 'Обязательное поле' }]}>
                                        <Input style={inputStyle} />
                                    </Form.Item>
                                </div>
                                <div style={{ marginBottom: 14 }}>
                                    <label style={labelStyle}>Дом / корпус</label>
                                    <Form.Item name="legalBuilding" noStyle rules={[{ required: true, message: 'Обязательное поле' }]}>
                                        <Input style={inputStyle} />
                                    </Form.Item>
                                </div>
                                <div style={{ marginBottom: 14 }}>
                                    <label style={labelStyle}>Офис / помещение</label>
                                    <Form.Item name="office" noStyle>
                                        <Input style={inputStyle} placeholder="офис 101" />
                                    </Form.Item>
                                </div>
                                <div style={{ marginBottom: 0 }}>
                                    <label style={labelStyle}>Индекс</label>
                                    <Form.Item name="legalPostalCode" noStyle>
                                        <Input style={inputStyle} placeholder="123456" />
                                    </Form.Item>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Контакты */}
                    <div style={sectionStyle}>
                        <div style={headerStyle}>
                            <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Контакты</span>
                        </div>
                        <div style={{ padding: 20 }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 16px' }}>
                                <div>
                                    <label style={labelStyle}>Email</label>
                                    <Input style={{ ...inputStyle, background: 'var(--surface-2)', color: 'var(--ink-3)' }} value={entity.email} disabled />
                                </div>
                                <div>
                                    <label style={labelStyle}>Телефон</label>
                                    <Form.Item name="phone" noStyle rules={[{ required: true, message: 'Обязательное поле' }]}>
                                        <Input style={inputStyle} placeholder="+79001234567" />
                                    </Form.Item>
                                </div>
                            </div>
                        </div>
                    </div>
                </Form>
            </div>
        );
    }

    return (
        <div style={{ maxWidth: 700, margin: '0 auto', paddingTop: 20, paddingBottom: 60 }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
                <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', margin: 0 }}>
                    Профиль организации
                </h1>
                {editBtn}
            </div>

            {/* Основные данные */}
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Организация</span>
                    <span style={{
                        display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px',
                        borderRadius: 11, fontSize: 12, fontWeight: 500,
                        background: statusBadge.bg, color: statusBadge.color,
                    }}>
                        {statusBadge.label}
                    </span>
                </div>
                <div style={{ padding: 20 }}>
                    <Row label="Полное наименование" value={entity.fullName} />
                    <Row label="ИНН" value={entity.inn} />
                    <Row label="ОГРН" value={entity.ogrn} />
                    <Row label="Руководитель" value={entity.director} />
                    <Row label="Должность" value={entity.directorTitle} />
                    <Row label="Действует на основании" value={entity.basisOfAuthority} last />
                </div>
            </div>

            {/* Юридический адрес */}
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Юридический адрес</span>
                </div>
                <div style={{ padding: 20 }}>
                    <Row label="Адрес" value={address} last />
                </div>
            </div>

            {/* Контакты */}
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Контакты</span>
                </div>
                <div style={{ padding: 20 }}>
                    <Row label="Email" value={entity.email} />
                    <Row label="Телефон" value={entity.phone} last />
                </div>
            </div>

            {/* Аккаунт */}
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Аккаунт</span>
                </div>
                <div style={{ padding: 20 }}>
                    <Row label="Дата регистрации" value={formatDate(entity.createdAt)} />
                    <Row label="Верифицирован" value={formatDate(entity.verifiedAt)} last />
                </div>
            </div>
        </div>
    );
};

// ─── Секция организации (для физлиц) ─────────────────────────
const OrganizationSection = ({ userId, onRegistered }: { userId: number; onRegistered: () => Promise<void> }) => {
    const [showForm, setShowForm] = useState(false);
    const [form] = Form.useForm<RegisterLegalEntityRequest>();
    const { message: messageApi } = App.useApp();

    const { data: linkStatus, isLoading: linkLoading } = useQuery({
        queryKey: ['link-status', userId],
        queryFn: () => getLinkStatus(userId),
    });

    const { data: legalEntity, isLoading: legalLoading } = useQuery({
        queryKey: ['legal-entity', linkStatus?.legalEntityId],
        queryFn: () => getLegalEntity(linkStatus!.legalEntityId!),
        enabled: !!linkStatus?.legalEntityId,
    });

    const registerMutation = useMutation({
        mutationFn: (values: RegisterLegalEntityRequest) => registerLegalEntity(values),
        onSuccess: async () => {
            messageApi.success('Заявка подана. Ожидайте верификации.');
            setShowForm(false);
            form.resetFields();
            await onRegistered();
        },
        onError: () => { messageApi.error('Ошибка при подаче заявки'); },
    });

    if (linkLoading || legalLoading) {
        return (
            <div style={sectionStyle}>
                <div style={headerStyle}><span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span></div>
                <div style={{ padding: 20, color: 'var(--ink-3)' }}>Загрузка...</div>
            </div>
        );
    }

    if (!linkStatus?.linked) {
        return (
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span>
                    {!showForm && (
                        <button onClick={() => setShowForm(true)} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 32, padding: '0 12px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                            Подать заявку
                        </button>
                    )}
                </div>
                <div style={{ padding: 20 }}>
                    {!showForm ? (
                        <div style={{ fontSize: 14, color: 'var(--ink-3)', lineHeight: 1.6 }}>
                            Работаете от юридического лица? Подключите организацию для доступа к оптовым ценам и B2B-документообороту.
                        </div>
                    ) : (
                        <Form<RegisterLegalEntityRequest> form={form} layout="vertical" onFinish={(v) => registerMutation.mutate(v)}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
                                <div>
                                    <label style={labelStyle}>Наименование организации</label>
                                    <Form.Item name="fullName" noStyle rules={[{ required: true, message: 'Введите наименование' }]}>
                                        <Input style={inputStyle} placeholder='ООО "Ромашка"' />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>ИНН</label>
                                    <Form.Item name="inn" noStyle rules={[{ required: true, message: 'Введите ИНН' }, { len: 10, message: 'ИНН — 10 цифр' }]}>
                                        <Input style={inputStyle} placeholder="1234567890" maxLength={10} />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>Email организации</label>
                                    <Form.Item name="email" noStyle rules={[{ required: true, type: 'email', message: 'Введите email' }]}>
                                        <Input style={inputStyle} placeholder="org@company.ru" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>Пароль для B2B аккаунта</label>
                                    <Form.Item name="password" noStyle rules={[{ required: true, min: 6, message: 'Минимум 6 символов' }]}>
                                        <Input.Password style={inputStyle} placeholder="Пароль" />
                                    </Form.Item>
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button type="submit" disabled={registerMutation.isPending} style={{ height: 40, padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    {registerMutation.isPending ? 'Отправка...' : 'Подать заявку'}
                                </button>
                                <button type="button" onClick={() => { setShowForm(false); form.resetFields(); }} style={{ height: 40, padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    Отмена
                                </button>
                            </div>
                        </Form>
                    )}
                </div>
            </div>
        );
    }

    if (!linkStatus.confirmed || legalEntity?.verificationStatus !== 'VERIFIED') {
        return (
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span>
                    <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px', borderRadius: 11, fontSize: 12, fontWeight: 500, background: 'var(--warn-tint)', color: 'var(--warn)' }}>На проверке</span>
                </div>
                <div style={{ padding: 20 }}>
                    <Row label="Организация" value={legalEntity?.fullName} />
                    <Row label="ИНН" value={legalEntity?.inn} last />
                </div>
            </div>
        );
    }

    return (
        <div style={sectionStyle}>
            <div style={headerStyle}>
                <span style={{ fontSize: 14, fontWeight: 600 }}>Организация</span>
                <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px', borderRadius: 11, fontSize: 12, fontWeight: 500, background: 'var(--brand-green-soft)', color: 'var(--brand-green)' }}>Верифицирована</span>
            </div>
            <div style={{ padding: 20 }}>
                <Row label="Организация" value={legalEntity?.fullName} />
                <Row label="ИНН" value={legalEntity?.inn} />
                <Row label="Email" value={legalEntity?.email} last />
            </div>
        </div>
    );
};

// ─── Профиль физлица ──────────────────────────────────────────
const B2CProfilePage = () => {
    const [editing, setEditing] = useState(false);
    const [form] = Form.useForm<UpdateProfileRequest>();
    const { message: messageApi } = App.useApp();
    const user = useAuthStore((state) => state.user)!;
    const restoreSession = useAuthStore((state) => state.restoreSession);

    const updateMutation = useMutation({
        mutationFn: (values: UpdateProfileRequest) => updateProfile(user.id, values),
        onSuccess: async () => {
            messageApi.success('Профиль обновлён');
            setEditing(false);
            await restoreSession();
        },
        onError: (error: AxiosError<{ message?: string }>) => {
            messageApi.error(error.response?.data?.message || 'Ошибка при обновлении профиля');
        },
    });

    const handleEdit = () => {
        form.setFieldsValue({ firstname: user.firstname, lastname: user.lastname, surname: user.surname || '', phone: user.phone || '' });
        setEditing(true);
    };

    return (
        <div style={{ maxWidth: 700, margin: '0 auto', paddingTop: 20, paddingBottom: 60 }}>
            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', marginBottom: 24 }}>
                Профиль
            </h1>

            {/* Личные данные */}
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Личные данные</span>
                    {!editing && (
                        <button onClick={handleEdit} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 32, padding: '0 12px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                            <EditOutlined /> Редактировать
                        </button>
                    )}
                </div>
                <div style={{ padding: 20 }}>
                    {editing ? (
                        <Form<UpdateProfileRequest> form={form} layout="vertical" onFinish={(v) => updateMutation.mutate(v)}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
                                <div><label style={labelStyle}>Фамилия</label>
                                    <Form.Item name="lastname" noStyle rules={[{ required: true, message: 'Введите фамилию' }, { min: 2, message: 'Минимум 2 символа' }]}>
                                        <Input style={inputStyle} placeholder="Фамилия" />
                                    </Form.Item>
                                </div>
                                <div><label style={labelStyle}>Имя</label>
                                    <Form.Item name="firstname" noStyle rules={[{ required: true, message: 'Введите имя' }, { min: 2, message: 'Минимум 2 символа' }]}>
                                        <Input style={inputStyle} placeholder="Имя" />
                                    </Form.Item>
                                </div>
                                <div><label style={labelStyle}>Отчество</label>
                                    <Form.Item name="surname" noStyle>
                                        <Input style={inputStyle} placeholder="Необязательно" />
                                    </Form.Item>
                                </div>
                                <div><label style={labelStyle}>Телефон</label>
                                    <Form.Item name="phone" noStyle rules={[{ required: true, message: 'Введите телефон' }, { pattern: /^\+?[0-9]{11}$/, message: '11 цифр' }]}>
                                        <Input style={inputStyle} placeholder="+79001234567" />
                                    </Form.Item>
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button type="submit" disabled={updateMutation.isPending} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    <SaveOutlined /> {updateMutation.isPending ? 'Сохранение...' : 'Сохранить'}
                                </button>
                                <button type="button" onClick={() => { setEditing(false); form.resetFields(); }} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    <CloseOutlined /> Отмена
                                </button>
                            </div>
                        </Form>
                    ) : (
                        <div>
                            <Row label="Фамилия" value={user.lastname} />
                            <Row label="Имя" value={user.firstname} />
                            <Row label="Отчество" value={user.surname || '—'} />
                            <Row label="Телефон" value={user.phone || '—'} last />
                        </div>
                    )}
                </div>
            </div>

            <OrganizationSection userId={user.id} onRegistered={restoreSession} />

            {/* Аккаунт */}
            <div style={sectionStyle}>
                <div style={headerStyle}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Аккаунт</span>
                </div>
                <div style={{ padding: 20 }}>
                    <Row label="Email" value={
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <span>{user.email}</span>
                            <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px', borderRadius: 11, fontSize: 12, fontWeight: 500, background: user.emailVerified ? 'var(--brand-green-soft)' : 'var(--warn-tint)', color: user.emailVerified ? 'var(--brand-green)' : 'var(--warn)' }}>
                                {user.emailVerified ? 'Подтверждён' : 'Не подтверждён'}
                            </span>
                        </div>
                    } />
                    <Row label="Дата регистрации" value={formatDate(user.createdAt)} last />
                </div>
            </div>
        </div>
    );
};

// ─── Точка входа ──────────────────────────────────────────────
const ProfilePage = () => {
    const user = useAuthStore((state) => state.user);
    if (!user) return null;

    if (user.clientType === 'B2B') {
        return <B2BProfilePage legalId={user.id} />;
    }

    return <B2CProfilePage />;
};

export default ProfilePage;
