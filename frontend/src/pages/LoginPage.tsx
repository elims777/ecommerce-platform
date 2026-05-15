import { useState } from 'react';
import { Form, Input, App } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { LoginRequest } from '@/types/auth';
import { AxiosError } from 'axios';

const BrandPanel = () => (
    <div style={{
        background: 'linear-gradient(135deg, #1E3A5F 0%, #122943 100%)',
        color: '#fff', padding: 48, position: 'relative', overflow: 'hidden',
        display: 'flex', flexDirection: 'column',
    }}>
        <svg width="360" height="320" viewBox="0 0 360 320" style={{ position: 'absolute', right: -60, bottom: -80, opacity: 0.22, pointerEvents: 'none' }}>
            <polygon points="180,10 180,220 40,260" fill="#C0272D" />
            <polygon points="180,10 180,220 320,260" fill="#0F2845" />
            <polygon points="40,260 320,260 180,220" fill="#1A6B3A" />
        </svg>

        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10, position: 'relative', zIndex: 1 }}>
            <svg width="32" height="30" viewBox="0 0 40 38">
                <polygon points="20,2 20,28 4,32" fill="#C0272D" />
                <polygon points="20,2 20,28 36,32" fill="#1E3A5F" />
                <polygon points="4,32 36,32 20,28" fill="#1A6B3A" />
            </svg>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1 }}>
                <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 20, color: '#fff', letterSpacing: '-0.02em' }}>РФснаб</span>
                <span style={{ fontSize: 9, color: 'rgba(255,255,255,.55)', marginTop: 3, letterSpacing: '0.06em', textTransform: 'uppercase', fontWeight: 500 }}>комплексное снабжение</span>
            </div>
        </div>

        <div style={{ marginTop: 'auto', position: 'relative', zIndex: 1 }}>
            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 36, fontWeight: 600, color: '#fff', letterSpacing: '-0.022em', lineHeight: 1.1, maxWidth: 400, marginBottom: 16 }}>
                Один кабинет для всех заявок предприятия.
            </h1>
            <p style={{ fontSize: 14.5, color: 'rgba(255,255,255,.7)', maxWidth: 380, lineHeight: 1.6, marginBottom: 32 }}>
                Заявки, документы, согласования, ЭДО, история закупок — в одном защищённом аккаунте.
            </p>
            <div style={{ display: 'flex', gap: 28 }}>
                {[['18 200', 'организаций'], ['12 480', 'товаров'], ['4.9 ★', 'рейтинг']].map(([n, l]) => (
                    <div key={l}>
                        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 22, color: '#fff', letterSpacing: '-0.01em' }}>{n}</div>
                        <div style={{ fontSize: 11, color: 'rgba(255,255,255,.6)', marginTop: 2 }}>{l}</div>
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
    const { message: messageApi } = App.useApp();

    const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

    const handleSubmit = async (values: LoginRequest) => {
        setLoading(true);
        try {
            await login(values);
            messageApi.success('Вы успешно вошли в систему');
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
        height: 44, border: '1px solid var(--line-2)', borderRadius: 6,
        fontFamily: 'var(--font-body)', fontSize: 14,
    };

    const labelStyle: React.CSSProperties = {
        display: 'block', fontSize: 12, fontWeight: 500, color: 'var(--ink-2)', marginBottom: 6,
    };

    const tabBtn = (active: boolean): React.CSSProperties => ({
        flex: 1, padding: '9px 0', fontWeight: active ? 600 : 500, fontSize: 13,
        background: active ? '#fff' : 'transparent',
        borderRadius: 5, border: 0,
        boxShadow: active ? '0 1px 2px rgba(0,0,0,.06)' : 'none',
        color: active ? 'var(--brand-navy)' : 'var(--ink-3)',
        fontFamily: 'var(--font-body)', cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
    });

    return (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', minHeight: '100vh' }}>
            <BrandPanel />

            <div style={{ background: '#fff', padding: 48, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: 13, color: 'var(--ink-3)' }}>
                    Ещё нет аккаунта?{' '}
                    <span onClick={() => navigate('/register')} style={{ color: 'var(--brand-red)', fontWeight: 600, marginLeft: 6, cursor: 'pointer' }}>
                        Регистрация
                    </span>
                </div>

                <div style={{ maxWidth: 380, margin: '60px auto auto', width: '100%' }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.018em', color: 'var(--ink-1)', marginBottom: 6 }}>
                        Вход в личный кабинет
                    </h2>
                    <p style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 22 }}>Войдите по e-mail и паролю</p>

                    {/* Юр / Физ toggle */}
                    <div style={{ display: 'flex', background: 'var(--surface-2)', borderRadius: 8, padding: 4, border: '1px solid var(--line-1)', marginBottom: 22 }}>
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
                                <a style={{ fontSize: 12, color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}>Забыли пароль?</a>
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
                                    width: '100%', height: 48,
                                    background: loading ? 'var(--surface-3)' : 'var(--brand-red)',
                                    color: loading ? 'var(--ink-3)' : '#fff',
                                    border: 'none', borderRadius: 6, fontSize: 15, fontWeight: 500,
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

                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '4px 0 16px', fontSize: 12, color: 'var(--ink-3)' }}>
                        <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                        <span>или</span>
                        <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                    </div>

                    <button
                        onClick={() => window.location.href = 'http://localhost:8080/oauth2/authorization/yandex'}
                        style={{
                            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                            width: '100%', height: 44, border: '1px solid var(--line-2)', background: '#fff',
                            color: 'var(--ink-1)', borderRadius: 6, fontSize: 14, fontWeight: 500,
                            cursor: 'pointer', fontFamily: 'var(--font-body)', transition: 'background 0.12s',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-2)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; }}
                    >
                        <svg width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="8" r="8" fill="#FC3F1D" /><path d="M9.2 4.2v7.6h-1V8.4L6.3 12H5l2-3.8c-1-.3-1.6-.9-1.6-2.2 0-1.4 1-2.4 2.3-2.4z" fill="#fff" /></svg>
                        Войти через Яндекс
                    </button>

                    <div style={{ marginTop: 24, padding: 14, background: 'var(--navy-tint)', borderRadius: 8, fontSize: 12.5, color: 'var(--brand-navy)', display: 'flex', gap: 10 }}>
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
