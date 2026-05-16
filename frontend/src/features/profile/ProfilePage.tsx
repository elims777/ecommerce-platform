import { useState } from 'react';
import { Form, Input, App } from 'antd';
import { EditOutlined, SaveOutlined, CloseOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { updateProfile } from '@/api/profile';
import type { UpdateProfileRequest } from '@/api/profile';
import type { AxiosError } from 'axios';

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

            {/* Аккаунт */}
            <div style={{ border: '1px solid var(--line-1)', borderRadius: 8, background: 'var(--surface)', overflow: 'hidden' }}>
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
