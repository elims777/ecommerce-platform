import { useState } from 'react';
import { Form, Input, Checkbox, App } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { RegisterRequest } from '@/types/auth';
import type { AxiosError } from 'axios';
import { register } from '@/api/auth';

interface RegisterFormValues extends RegisterRequest {
    confirmPassword: string;
    privacyPolicy: boolean;
    personalData: boolean;
}

const RegisterPage = () => {
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();

    const handleSubmit = async (values: RegisterFormValues) => {
        setLoading(true);
        try {
            const { confirmPassword: _, ...request } = values;
            await register(request);
            messageApi.success('Регистрация прошла успешно! Проверьте почту для подтверждения аккаунта.');
            navigate('/login', { replace: true });
        } catch (error) {
            const axiosError = error as AxiosError<{ message?: string; error?: string }>;
            const errorMessage =
                axiosError.response?.data?.message ||
                axiosError.response?.data?.error ||
                'Ошибка при регистрации. Попробуйте позже.';
            messageApi.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    const inputStyle: React.CSSProperties = {
        height: 44,
        border: '1px solid var(--line-2)',
        borderRadius: 6,
        fontFamily: 'var(--font-body)',
        fontSize: 14,
    };

    const labelStyle: React.CSSProperties = {
        display: 'block',
        fontSize: 12,
        fontWeight: 500,
        color: 'var(--ink-2)',
        marginBottom: 6,
    };

    return (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', height: '100vh', margin: '0 -48px' }}>
            {/* Левая колонка — брендовая */}
            <div
                style={{
                    background: 'linear-gradient(135deg, #1E3A5F 0%, #122943 100%)',
                    color: '#fff',
                    padding: 48,
                    position: 'relative',
                    overflow: 'hidden',
                    display: 'flex',
                    flexDirection: 'column',
                }}
            >
                <svg width="360" height="320" viewBox="0 0 360 320" style={{ position: 'absolute', right: -60, bottom: -80, opacity: 0.22, pointerEvents: 'none' }}>
                    <polygon points="180,10 180,220 40,260" fill="#C0272D" />
                    <polygon points="180,10 180,220 320,260" fill="#0F2845" />
                    <polygon points="40,260 320,260 180,220" fill="#1A6B3A" />
                </svg>

                <div style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
                    <img src="/logo.png" alt="РФснаб" style={{ height: 38, filter: 'brightness(0) invert(1)' }} />
                    <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 20, color: '#fff', letterSpacing: '-0.01em' }}>РФснаб</span>
                </div>

                <div style={{ marginTop: 'auto', position: 'relative', zIndex: 1 }}>
                    <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 36, fontWeight: 600, color: '#fff', letterSpacing: '-0.022em', lineHeight: 1.1, maxWidth: 400, marginBottom: 16 }}>
                        Комплексное снабжение предприятий — в одном кабинете.
                    </h1>
                    <p style={{ fontSize: 14.5, color: 'rgba(255,255,255,.7)', maxWidth: 380, lineHeight: 1.6, marginBottom: 32 }}>
                        СИЗ, спецодежда, противопожарное оборудование и 12 000+ позиций со счёт-фактурой и ЭДО.
                    </p>
                    <div style={{ display: 'flex', gap: 28, fontSize: 12.5 }}>
                        {[['18 200', 'организаций'], ['12 480', 'товаров'], ['84', 'региона']].map(([n, l]) => (
                            <div key={l}>
                                <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 22, color: '#fff', letterSpacing: '-0.01em' }}>{n}</div>
                                <div style={{ fontSize: 11, color: 'rgba(255,255,255,.6)', marginTop: 2 }}>{l}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Правая колонка — форма */}
            <div style={{ background: '#fff', padding: 48, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: 13, color: 'var(--ink-3)' }}>
                    Уже зарегистрированы?{' '}
                    <span
                        onClick={() => navigate('/login')}
                        style={{ color: 'var(--brand-red)', fontWeight: 600, marginLeft: 6, cursor: 'pointer' }}
                    >
                        Войти
                    </span>
                </div>

                <div style={{ maxWidth: 460, margin: '40px auto auto', width: '100%' }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.018em', color: 'var(--ink-1)', marginBottom: 6 }}>
                        Создать аккаунт
                    </h2>
                    <p style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 24 }}>Заполните данные для регистрации</p>

                    <Form<RegisterFormValues> layout="vertical" onFinish={handleSubmit} autoComplete="off">
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                            <div>
                                <label style={labelStyle}>Имя</label>
                                <Form.Item name="firstname" noStyle rules={[{ required: true, message: 'Введите имя' }, { min: 2, message: 'Минимум 2 символа' }]}>
                                    <Input style={inputStyle} placeholder="Иван" />
                                </Form.Item>
                            </div>
                            <div>
                                <label style={labelStyle}>Фамилия</label>
                                <Form.Item name="lastname" noStyle rules={[{ required: true, message: 'Введите фамилию' }, { min: 2, message: 'Минимум 2 символа' }]}>
                                    <Input style={inputStyle} placeholder="Иванов" />
                                </Form.Item>
                            </div>
                        </div>

                        <div style={{ marginTop: 14 }}>
                            <label style={labelStyle}>Телефон</label>
                            <Form.Item name="phone" noStyle rules={[{ required: true, message: 'Введите телефон' }, { pattern: /^\+?[0-9]{11}$/, message: 'Телефон должен содержать 11 цифр' }]}>
                                <Input style={inputStyle} placeholder="+79001234567" />
                            </Form.Item>
                        </div>

                        <div style={{ marginTop: 14 }}>
                            <label style={labelStyle}>E-mail</label>
                            <Form.Item name="email" noStyle rules={[{ required: true, message: 'Введите email' }, { type: 'email', message: 'Некорректный формат email' }]}>
                                <Input style={inputStyle} placeholder="ivan@company.ru" />
                            </Form.Item>
                        </div>

                        <div style={{ marginTop: 14 }}>
                            <label style={labelStyle}>Пароль</label>
                            <Form.Item name="password" noStyle rules={[{ required: true, message: 'Введите пароль' }, { min: 8, message: 'Минимум 8 символов' }]}>
                                <Input.Password style={inputStyle} placeholder="Минимум 8 символов" />
                            </Form.Item>
                        </div>

                        <div style={{ marginTop: 14 }}>
                            <label style={labelStyle}>Подтверждение пароля</label>
                            <Form.Item
                                name="confirmPassword"
                                noStyle
                                dependencies={['password']}
                                rules={[
                                    { required: true, message: 'Подтвердите пароль' },
                                    ({ getFieldValue }) => ({
                                        validator(_, value) {
                                            if (!value || getFieldValue('password') === value) return Promise.resolve();
                                            return Promise.reject(new Error('Пароли не совпадают'));
                                        },
                                    }),
                                ]}
                            >
                                <Input.Password style={inputStyle} placeholder="Повторите пароль" />
                            </Form.Item>
                        </div>

                        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
                            <Form.Item
                                name="privacyPolicy"
                                valuePropName="checked"
                                noStyle
                                rules={[{ validator: (_, v) => v ? Promise.resolve() : Promise.reject(new Error('Необходимо принять политику конфиденциальности')) }]}
                            >
                                <Checkbox style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                                    Я ознакомлен(а) с{' '}
                                    <a href="/privacy-policy" target="_blank" style={{ color: 'var(--brand-navy)' }}>Политикой конфиденциальности</a>
                                </Checkbox>
                            </Form.Item>
                            <Form.Item
                                name="personalData"
                                valuePropName="checked"
                                noStyle
                                rules={[{ validator: (_, v) => v ? Promise.resolve() : Promise.reject(new Error('Необходимо дать согласие на обработку персональных данных')) }]}
                            >
                                <Checkbox style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                                    Я даю согласие на{' '}
                                    <a href="/personal-data" target="_blank" style={{ color: 'var(--brand-navy)' }}>обработку персональных данных</a>
                                </Checkbox>
                            </Form.Item>
                        </div>

                        <Form.Item style={{ marginTop: 22 }}>
                            <button
                                type="submit"
                                disabled={loading}
                                style={{
                                    width: '100%',
                                    height: 48,
                                    background: loading ? 'var(--surface-3)' : 'var(--brand-red)',
                                    color: loading ? 'var(--ink-3)' : '#fff',
                                    border: 'none',
                                    borderRadius: 6,
                                    fontSize: 15,
                                    fontWeight: 500,
                                    cursor: loading ? 'not-allowed' : 'pointer',
                                    fontFamily: 'var(--font-body)',
                                    transition: 'background 0.12s',
                                }}
                                onMouseEnter={(e) => { if (!loading) e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                                onMouseLeave={(e) => { if (!loading) e.currentTarget.style.background = 'var(--brand-red)'; }}
                            >
                                {loading ? 'Регистрация...' : 'Зарегистрироваться'}
                            </button>
                        </Form.Item>
                    </Form>

                    <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '16px 0', fontSize: 12, color: 'var(--ink-3)' }}>
                        <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                        <span>или</span>
                        <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                    </div>

                    <button
                        onClick={() => window.location.href = 'http://localhost:8080/auth/oauth2/register/yandex'}
                        style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            gap: 8,
                            width: '100%',
                            height: 44,
                            border: '1px solid var(--line-2)',
                            background: '#fff',
                            color: 'var(--ink-1)',
                            borderRadius: 6,
                            fontSize: 14,
                            fontWeight: 500,
                            cursor: 'pointer',
                            fontFamily: 'var(--font-body)',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-2)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = '#fff'; }}
                    >
                        <svg width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="8" r="8" fill="#FC3F1D" /><path d="M9.2 4.2v7.6h-1V8.4L6.3 12H5l2-3.8c-1-.3-1.6-.9-1.6-2.2 0-1.4 1-2.4 2.3-2.4z" fill="#fff" /></svg>
                        Зарегистрироваться через Яндекс
                    </button>
                </div>
            </div>
        </div>
    );
};

export default RegisterPage;
