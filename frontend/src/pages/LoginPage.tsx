import { useState, useEffect } from 'react';
import { Form, Input, App } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { NavLink } from '@/components/navigation';
import { useAuthStore } from '@/store/authStore';
import { useCartStore } from '@/store/cartStore';
import { consumePendingAddToCart } from '@/utils/pendingCart';
import type { LoginRequest } from '@/types/auth';
import { AxiosError } from 'axios';

const BrandPanel = () => (
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
            <div style={{ display: 'flex', gap: 28 }}>
                {[['18 200', 'организаций'], ['12 480', 'товаров'], ['4.9 ★', 'рейтинг']].map(([n, l]) => (
                    <div key={l}>
                        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-3xl)', color: '#fff', letterSpacing: '-0.01em' }}>{n}</div>
                        <div style={{ fontSize: 'var(--text-xs)', color: 'var(--overlay-white-60)', marginTop: 2 }}>{l}</div>
                    </div>
                ))}
            </div>
        </div>
    </div>
);

const LoginPage = () => {
    const [loading, setLoading] = useState(false);
    const [accountType, setAccountType] = useState<'legal' | 'personal'>('legal');
    const navigate = useNavigate();
    const location = useLocation();
    const login = useAuthStore((state) => state.login);
    const loginLegal = useAuthStore((state) => state.loginLegal);
    const addCartItem = useCartStore((state) => state.addItem);
    const { message: messageApi } = App.useApp();

    const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

    useEffect(() => {
        const params = new URLSearchParams(location.search);
        const confirmed = params.get('legal_confirmed');
        if (confirmed === 'true') {
            messageApi.success('Email подтверждён. Ожидайте верификации от менеджера — это займёт до 1 рабочего дня.');
        } else if (confirmed === 'error') {
            messageApi.error('Ссылка подтверждения недействительна или уже была использована.');
        }
    }, []);

    const handleSubmit = async (values: LoginRequest & { login?: string }) => {
        setLoading(true);
        try {
            if (accountType === 'legal') {
                await loginLegal(values.email, values.password);
            } else {
                await login(values);
            }
            const { added, failed } = await consumePendingAddToCart(addCartItem);
            if (added > 0 && failed === 0) {
                messageApi.success('Вы вошли в систему. Товары добавлены в корзину');
            } else if (added > 0 && failed > 0) {
                messageApi.success('Вы вошли в систему');
                messageApi.warning('Часть товаров не удалось добавить в корзину');
            } else if (failed > 0) {
                messageApi.success('Вы вошли в систему');
                messageApi.warning('Не удалось добавить товары в корзину');
            } else {
                messageApi.success('Вы успешно вошли в систему');
            }
            navigate(from, { replace: true });
        } catch (error) {
            const axiosError = error as AxiosError<{ message?: string }>;
            const errorMessage = axiosError.response?.data?.message || 'Неверный email или пароль';
            messageApi.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    const inputStyle: React.CSSProperties = {
        height: 'var(--input-h-lg)', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)',
        fontFamily: 'var(--font-body)', fontSize: 'var(--text-md)',
    };

    const labelStyle: React.CSSProperties = {
        display: 'block', fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--ink-2)', marginBottom: 6,
    };

    const tabBtn = (active: boolean): React.CSSProperties => ({
        flex: 1, padding: '9px 0', fontWeight: active ? 600 : 500, fontSize: 'var(--text-base)',
        background: active ? '#fff' : 'transparent',
        borderRadius: 'var(--r-3)', border: 0,
        boxShadow: active ? '0 1px 2px rgba(0,0,0,.06)' : 'none',
        color: active ? 'var(--brand-navy)' : 'var(--ink-3)',
        fontFamily: 'var(--font-body)', cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
    });

    return (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', minHeight: '100vh' }}>
            <BrandPanel />

            <div style={{ background: 'var(--surface)', padding: 48, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: 'var(--text-base)', color: 'var(--ink-3)' }}>
                    Ещё нет аккаунта?{' '}
                    <NavLink to="/register" style={{ color: 'var(--brand-red)', fontWeight: 600, marginLeft: 6, cursor: 'pointer' }}>
                        Регистрация
                    </NavLink>
                </div>

                <div style={{ maxWidth: 380, margin: '60px auto auto', width: '100%' }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-5xl)', fontWeight: 600, letterSpacing: '-0.018em', color: 'var(--ink-1)', marginBottom: 6 }}>
                        Вход в личный кабинет
                    </h2>
                    <p style={{ fontSize: 'var(--text-md)', color: 'var(--ink-3)', marginBottom: 22 }}>Войдите по e-mail и паролю</p>

                    {/* Юр / Физ toggle */}
                    <div style={{ display: 'flex', background: 'var(--surface-2)', borderRadius: 'var(--r-4)', padding: 4, border: '1px solid var(--line-1)', marginBottom: 22 }}>
                        <button style={tabBtn(accountType === 'legal')} onClick={() => setAccountType('legal')}>
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                                <rect x="4" y="4" width="16" height="16" rx="1"/><path d="M9 8h2M13 8h2M9 12h2M13 12h2M9 16h6"/>
                            </svg>
                            Юридическое лицо
                        </button>
                        <button style={tabBtn(accountType === 'personal')} onClick={() => setAccountType('personal')}>
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/>
                            </svg>
                            Физическое лицо
                        </button>
                    </div>

                    <Form<LoginRequest> layout="vertical" onFinish={handleSubmit} autoComplete="off">
                        <div style={{ marginBottom: 14 }}>
                            <label style={labelStyle}>E-mail{accountType === 'legal' ? ' или ИНН' : ''}</label>
                            <Form.Item name="email" noStyle rules={[{ required: true, message: 'Введите email' }, { type: 'email', message: 'Некорректный формат email' }]}>
                                <Input style={inputStyle} placeholder={accountType === 'legal' ? 'buyer@company.ru' : 'ivan@mail.ru'} />
                            </Form.Item>
                        </div>

                        <div style={{ marginBottom: 14 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                                <label style={{ ...labelStyle, marginBottom: 0 }}>Пароль</label>
                                <a style={{ fontSize: 'var(--text-sm)', color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}>Забыли пароль?</a>
                            </div>
                            <Form.Item name="password" noStyle rules={[{ required: true, message: 'Введите пароль' }, { min: 8, message: 'Минимум 8 символов' }]}>
                                <Input.Password style={inputStyle} placeholder="••••••••" />
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
                                {loading ? 'Вход...' : 'Войти'}
                            </button>
                        </Form.Item>
                    </Form>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '4px 0 16px', fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                        <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                        <span>или</span>
                        <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                    </div>

                    <button
                        onClick={() => window.location.href = '/oauth2/authorization/yandex'}
                        style={{
                            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                            width: '100%', height: 'var(--btn-h-lg)', border: '1px solid var(--line-2)', background: 'var(--surface)',
                            color: 'var(--ink-1)', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500,
                            cursor: 'pointer', fontFamily: 'var(--font-body)', transition: 'background 0.12s',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-2)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--surface)'; }}
                    >
                        <svg width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="8" r="8" fill="#FC3F1D" /><path d="M9.2 4.2v7.6h-1V8.4L6.3 12H5l2-3.8c-1-.3-1.6-.9-1.6-2.2 0-1.4 1-2.4 2.3-2.4z" fill="#fff" /></svg>
                        Войти через Яндекс
                    </button>

                    <div style={{ marginTop: 24, padding: 14, background: 'var(--navy-tint)', borderRadius: 'var(--r-4)', fontSize: 'var(--text-sm)', color: 'var(--brand-navy)', display: 'flex', gap: 10 }}>
                        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, marginTop: 1 }}>
                            <path d="M12 3 4 6v6c0 5 3.5 8.5 8 10 4.5-1.5 8-5 8-10V6l-8-3z"/>
                        </svg>
                        <div>Защищённый вход в соответствии с 152-ФЗ. Данные передаются по зашифрованному каналу.</div>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default LoginPage;
