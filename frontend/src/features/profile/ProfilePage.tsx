import { useState } from 'react';
import { Form, Input, App } from 'antd';
import { EditOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { updateProfile } from '@/api/profile';
import type { UpdateProfileRequest } from '@/api/profile';
import type { AxiosError } from 'axios';
import {
    getLinkStatus,
    getLegalEntity,
    registerLegalEntity,
    type RegisterLegalEntityRequest,
} from '@/api/legalEntity';

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

    const sectionStyle: React.CSSProperties = { border: '1px solid var(--line-1)', borderRadius: 8, background: 'var(--surface)', overflow: 'hidden', marginTop: 16 };
    const headerStyle: React.CSSProperties = { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 20px', borderBottom: '1px solid var(--line-1)', background: 'var(--surface-2)' };
    const inputStyle: React.CSSProperties = { height: 40, border: '1px solid var(--line-2)', borderRadius: 6, fontFamily: 'var(--font-body)', fontSize: 14 };
    const rowStyle: React.CSSProperties = { display: 'flex', fontSize: 14, padding: '10px 0', borderBottom: '1px solid var(--line-1)' };

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
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>Наименование организации</label>
                                    <Form.Item name="fullName" noStyle rules={[{ required: true, message: 'Введите наименование' }]}>
                                        <Input style={inputStyle} placeholder="ООО Ромашка" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>ИНН</label>
                                    <Form.Item name="inn" noStyle rules={[{ required: true, message: 'Введите ИНН' }, { len: 10, message: 'ИНН — 10 цифр' }]}>
                                        <Input style={inputStyle} placeholder="1234567890" maxLength={10} />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>Email организации</label>
                                    <Form.Item name="email" noStyle rules={[{ required: true, type: 'email', message: 'Введите email' }]}>
                                        <Input style={inputStyle} placeholder="org@company.ru" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={{ fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 4, display: 'block' }}>Пароль для B2B аккаунта</label>
                                    <Form.Item name="password" noStyle rules={[{ required: true, min: 6, message: 'Минимум 6 символов' }]}>
                                        <Input.Password style={inputStyle} placeholder="Пароль" />
                                    </Form.Item>
                                </div>
                            </div>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button type="submit" disabled={registerMutation.isPending} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    {registerMutation.isPending ? 'Отправка...' : 'Подать заявку'}
                                </button>
                                <button type="button" onClick={() => { setShowForm(false); form.resetFields(); }} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
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
                    <div style={rowStyle}><span style={{ color: 'var(--ink-3)', width: 160 }}>Организация</span><span style={{ fontWeight: 500 }}>{legalEntity?.fullName}</span></div>
                    <div style={{ ...rowStyle, borderBottom: 'none' }}><span style={{ color: 'var(--ink-3)', width: 160 }}>ИНН</span><span style={{ fontWeight: 500 }}>{legalEntity?.inn}</span></div>
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
                {[['Организация', legalEntity?.fullName], ['ИНН', legalEntity?.inn], ['Email', legalEntity?.email]].map(([label, value]) => (
                    <div key={label} style={rowStyle}>
                        <span style={{ color: 'var(--ink-3)', width: 160, flexShrink: 0 }}>{label}</span>
                        <span style={{ fontWeight: 500 }}>{value || '—'}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};

const ProfilePage = () => {
    const [editing, setEditing] = useState(false);
    const [form] = Form.useForm<UpdateProfileRequest>();
    const { message: messageApi } = App.useApp();
    const user = useAuthStore((state) => state.user);
    const restoreSession = useAuthStore((state) => state.restoreSession);

    const updateMutation = useMutation({
        mutationFn: (values: UpdateProfileRequest) => updateProfile(user!.id, values),
        onSuccess: async () => {
            messageApi.success('Профиль обновлён');
            setEditing(false);
            await restoreSession();
        },
        onError: (error: AxiosError<{ message?: string }>) => {
            const errorMessage = error.response?.data?.message || 'Ошибка при обновлении профиля';
            messageApi.error(errorMessage);
        },
    });

    const handleEdit = () => {
        if (user) {
            form.setFieldsValue({ firstname: user.firstname, lastname: user.lastname, surname: user.surname || '', phone: user.phone || '' });
        }
        setEditing(true);
    };

    const handleSave = (values: UpdateProfileRequest) => { updateMutation.mutate(values); };
    const handleCancel = () => { setEditing(false); form.resetFields(); };

    const formatDate = (dateStr: string): string => {
        if (!dateStr) return '—';
        return new Date(dateStr).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
    };

    if (!user) return null;

    const inputStyle: React.CSSProperties = {
        height: 40,
        border: '1px solid var(--line-2)',
        borderRadius: 6,
        fontFamily: 'var(--font-body)',
        fontSize: 14,
    };

    const labelStyle: React.CSSProperties = {
        fontSize: 12,
        fontWeight: 500,
        color: 'var(--ink-2)',
        marginBottom: 4,
        display: 'block',
    };

    const rowStyle: React.CSSProperties = {
        display: 'flex',
        fontSize: 14,
        padding: '10px 0',
        borderBottom: '1px solid var(--line-1)',
    };

    return (
        <div style={{ maxWidth: 700, margin: '0 auto', paddingTop: 20, paddingBottom: 60 }}>
            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', marginBottom: 24 }}>
                Профиль
            </h1>

            {/* Личные данные */}
            <div style={{ border: '1px solid var(--line-1)', borderRadius: 8, background: 'var(--surface)', marginBottom: 16, overflow: 'hidden' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 20px', borderBottom: '1px solid var(--line-1)', background: 'var(--surface-2)' }}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Личные данные</span>
                    {!editing && (
                        <button
                            onClick={handleEdit}
                            style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 32, padding: '0 12px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                        >
                            <EditOutlined /> Редактировать
                        </button>
                    )}
                </div>

                <div style={{ padding: 20 }}>
                    {editing ? (
                        <Form<UpdateProfileRequest> form={form} layout="vertical" onFinish={handleSave}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
                                <div>
                                    <label style={labelStyle}>Фамилия</label>
                                    <Form.Item name="lastname" noStyle rules={[{ required: true, message: 'Введите фамилию' }, { min: 2, message: 'Минимум 2 символа' }]}>
                                        <Input style={inputStyle} placeholder="Фамилия" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>Имя</label>
                                    <Form.Item name="firstname" noStyle rules={[{ required: true, message: 'Введите имя' }, { min: 2, message: 'Минимум 2 символа' }]}>
                                        <Input style={inputStyle} placeholder="Имя" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>Отчество</label>
                                    <Form.Item name="surname" noStyle>
                                        <Input style={inputStyle} placeholder="Отчество (необязательно)" />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>Телефон</label>
                                    <Form.Item name="phone" noStyle rules={[{ required: true, message: 'Введите телефон' }, { pattern: /^\+?[0-9]{11}$/, message: '11 цифр' }]}>
                                        <Input style={inputStyle} placeholder="+79001234567" />
                                    </Form.Item>
                                </div>
                            </div>

                            <div style={{ display: 'flex', gap: 8 }}>
                                <button
                                    type="submit"
                                    disabled={updateMutation.isPending}
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                                >
                                    <SaveOutlined /> {updateMutation.isPending ? 'Сохранение...' : 'Сохранить'}
                                </button>
                                <button
                                    type="button"
                                    onClick={handleCancel}
                                    style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 40, padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                                >
                                    <CloseOutlined /> Отмена
                                </button>
                            </div>
                        </Form>
                    ) : (
                        <div>
                            {[['Фамилия', user.lastname], ['Имя', user.firstname], ['Отчество', user.surname || '—'], ['Телефон', user.phone || '—']].map(([label, value]) => (
                                <div key={label} style={rowStyle}>
                                    <span style={{ color: 'var(--ink-3)', width: 160, flexShrink: 0 }}>{label}</span>
                                    <span style={{ fontWeight: 500 }}>{value}</span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            <OrganizationSection userId={user.id} onRegistered={restoreSession} />

            {/* Аккаунт */}
            <div style={{ border: '1px solid var(--line-1)', borderRadius: 8, background: 'var(--surface)', overflow: 'hidden', marginTop: 16 }}>
                <div style={{ padding: '14px 20px', borderBottom: '1px solid var(--line-1)', background: 'var(--surface-2)' }}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-1)' }}>Аккаунт</span>
                </div>
                <div style={{ padding: 20 }}>
                    <div style={rowStyle}>
                        <span style={{ color: 'var(--ink-3)', width: 160, flexShrink: 0 }}>Email</span>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <span style={{ fontWeight: 500 }}>{user.email}</span>
                            <span style={{
                                display: 'inline-flex', alignItems: 'center', gap: 5,
                                height: 22, padding: '0 8px', borderRadius: 11,
                                fontSize: 12, fontWeight: 500,
                                background: user.emailVerified ? 'var(--brand-green-soft)' : 'var(--warn-tint)',
                                color: user.emailVerified ? 'var(--brand-green)' : 'var(--warn)',
                            }}>
                                {user.emailVerified ? 'Подтверждён' : 'Не подтверждён'}
                            </span>
                        </div>
                    </div>
                    <div style={{ ...rowStyle, borderBottom: 'none' }}>
                        <span style={{ color: 'var(--ink-3)', width: 160, flexShrink: 0 }}>Дата регистрации</span>
                        <span style={{ fontWeight: 500 }}>{formatDate(user.createdAt)}</span>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ProfilePage;
