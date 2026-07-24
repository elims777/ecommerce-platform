import { useState } from 'react';
import { Form, Input, App } from 'antd';
import { NavLink } from '@/components/navigation';
import { forgotPassword } from '@/api/auth';

const BrandPanel = () => {
    return (
    <div style={{
        background: 'var(--gradient-brand-panel)',
        color: '#fff', padding: 48, position: 'relative', overflow: 'hidden',
        display: 'flex', flexDirection: 'column',
    }}>
        <img src="/logo-light.png" alt=""
            style={{
                position: 'absolute', right: -40, bottom: -30,
                height: 360, width: 'auto', opacity: 0.14,
                pointerEvents: 'none',
            }}
        />

        <NavLink
            to="/"
            style={{
                position: 'absolute',
                top: 48,
                left: 'var(--page-pad-x)',
                zIndex: 2,
            }}
        >
            <img
                src="/logo-light.png"
                alt="РФснаб"
                style={{
                    height: 'var(--logo-h-header)',
                    display: 'block',
                }}
            />
        </NavLink>

        <div style={{ marginTop: 'auto', position: 'relative', zIndex: 1 }}>
            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-7xl)', fontWeight: 600, color: '#fff', letterSpacing: '-0.022em', lineHeight: 1.1, maxWidth: 400, marginBottom: 16 }}>
                Один кабинет для всех заявок предприятия.
            </h1>
            <p style={{ fontSize: 'var(--text-md)', color: 'var(--overlay-white-70)', maxWidth: 380, lineHeight: 1.6, marginBottom: 32 }}>
                Заявки, документы, согласования, ЭДО, история закупок — в одном защищённом аккаунте.
            </p>
        </div>
    </div>
    );
};

const inputStyle: React.CSSProperties = {
    height: 'var(--input-h-lg)', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)',
    fontFamily: 'var(--font-body)', fontSize: 'var(--text-md)',
};

const labelStyle: React.CSSProperties = {
    display: 'block', fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--ink-2)', marginBottom: 6,
};

interface ForgotPasswordFormValues {
    email: string;
}

const ForgotPasswordPage = () => {
    const [loading, setLoading] = useState(false);
    const [sent, setSent] = useState(false);
    const { message: messageApi } = App.useApp();

    const handleSubmit = async (values: ForgotPasswordFormValues) => {
        setLoading(true);
        try {
            await forgotPassword(values.email);
            setSent(true);
        } catch {
            messageApi.error('Не удалось отправить запрос. Попробуйте позже.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', minHeight: '100vh' }}>
            <BrandPanel />

            <div style={{ background: 'var(--surface)', padding: 48, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', fontSize: 'var(--text-base)', color: 'var(--ink-3)' }}>
                    <NavLink to="/login" style={{ color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}>
                        Вернуться ко входу
                    </NavLink>
                </div>

                <div style={{ maxWidth: 380, margin: '60px auto auto', width: '100%' }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-5xl)', fontWeight: 600, letterSpacing: '-0.018em', color: 'var(--ink-1)', marginBottom: 6 }}>
                        Восстановление пароля
                    </h2>
                    <p style={{ fontSize: 'var(--text-md)', color: 'var(--ink-3)', marginBottom: 22 }}>
                        Укажите e-mail — пришлём ссылку для сброса
                    </p>

                    {sent ? (
                        <div style={{ padding: 14, background: 'var(--navy-tint)', borderRadius: 'var(--r-4)', fontSize: 'var(--text-sm)', color: 'var(--brand-navy)' }}>
                            Письмо отправлено, если такой email зарегистрирован. Проверьте почту.
                        </div>
                    ) : (
                        <Form<ForgotPasswordFormValues> layout="vertical" onFinish={handleSubmit} autoComplete="off">
                            <div style={{ marginBottom: 14 }}>
                                <label style={labelStyle}>E-mail</label>
                                <Form.Item name="email" noStyle rules={[{ required: true, message: 'Введите email' }, { type: 'email', message: 'Некорректный формат email' }]}>
                                    <Input style={inputStyle} placeholder="ivan@mail.ru" />
                                </Form.Item>
                            </div>

                            <Form.Item style={{ marginTop: 22 }}>
                                <button
                                    type="submit"
                                    disabled={loading}
                                    style={{
                                        width: '100%', height: 'var(--btn-h-xl)',
                                        background: loading ? 'var(--surface-3)' : 'var(--brand-red)',
                                        color: loading ? 'var(--ink-3)' : '#fff',
                                        border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-lg)', fontWeight: 500,
                                        cursor: loading ? 'not-allowed' : 'pointer', fontFamily: 'var(--font-body)',
                                        transition: 'background 0.12s',
                                    }}
                                    onMouseEnter={(e) => { if (!loading) e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                                    onMouseLeave={(e) => { if (!loading) e.currentTarget.style.background = 'var(--brand-red)'; }}
                                >
                                    {loading ? 'Отправка...' : 'Отправить ссылку'}
                                </button>
                            </Form.Item>
                        </Form>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ForgotPasswordPage;
