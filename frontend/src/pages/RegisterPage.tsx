import { useState } from 'react';
import { Form, Input, Checkbox, App } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { RegisterRequest } from '@/types/auth';
import type { AxiosError } from 'axios';
import { register, registerLegal } from '@/api/auth';
import type { RegisterLegalRequest } from '@/api/auth';

type AccountType = 'personal' | 'legal';

interface PersonalFormValues extends RegisterRequest {
    confirmPassword: string;
    privacyPolicy: boolean;
    personalData: boolean;
    newsletterConsent: boolean;
}

interface LegalFormValues extends RegisterLegalRequest {
    confirmPassword: string;
    privacyPolicy: boolean;
    personalData: boolean;
    newsletterConsent: boolean;
}

const inputStyle: React.CSSProperties = {
    height: 'var(--input-h-lg)',
    border: '1px solid var(--line-2)',
    borderRadius: 'var(--r-3)',
    fontFamily: 'var(--font-body)',
    fontSize: 'var(--text-md)',
};

const labelStyle: React.CSSProperties = {
    display: 'block',
    fontSize: 12,
    fontWeight: 500,
    color: 'var(--ink-2)',
    marginBottom: 6,
};

const RegisterPage = () => {
    const [loading, setLoading] = useState(false);
    const [accountType, setAccountType] = useState<AccountType>('personal');
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();

    const [personalForm] = Form.useForm<PersonalFormValues>();
    const [legalForm] = Form.useForm<LegalFormValues>();

    const handlePersonalSubmit = async (values: PersonalFormValues) => {
        setLoading(true);
        try {
            const { confirmPassword: _, privacyPolicy: __, personalData: ___, newsletterConsent: ____, ...rest } = values;
            const request = { ...rest, newsletterConsent: values.newsletterConsent ?? false };
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

    const handleLegalSubmit = async (values: LegalFormValues) => {
        setLoading(true);
        try {
            const { confirmPassword: _, privacyPolicy: __, personalData: ___, newsletterConsent: ____, ...request } = values;
            await registerLegal(request);
            messageApi.success('Заявка на регистрацию отправлена! Проверьте почту — ссылка для подтверждения.');
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

    const tabBtn = (active: boolean): React.CSSProperties => ({
        flex: 1, padding: '9px 0', fontWeight: active ? 600 : 500, fontSize: 13,
        background: active ? '#fff' : 'transparent',
        borderRadius: 5, border: 0,
        boxShadow: active ? '0 1px 2px rgba(0,0,0,.06)' : 'none',
        color: active ? 'var(--brand-navy)' : 'var(--ink-3)',
        fontFamily: 'var(--font-body)', cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
        transition: 'all 0.15s',
    });

    const SubmitButton = ({ label }: { label: string }) => (
        <button
            type="submit"
            disabled={loading}
            style={{
                width: '100%', height: 'var(--btn-h-xl)',
                background: loading ? 'var(--surface-3)' : 'var(--brand-red)',
                color: loading ? 'var(--ink-3)' : '#fff',
                border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-lg)', fontWeight: 500,
                cursor: loading ? 'not-allowed' : 'pointer',
                fontFamily: 'var(--font-body)', transition: 'background 0.12s',
            }}
            onMouseEnter={(e) => { if (!loading) e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
            onMouseLeave={(e) => { if (!loading) e.currentTarget.style.background = 'var(--brand-red)'; }}
        >
            {loading ? 'Регистрация...' : label}
        </button>
    );

    const ConsentFields = () => (
        <div style={{ marginTop: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Form.Item
                name="privacyPolicy" valuePropName="checked" noStyle
                rules={[{ validator: (_, v) => v ? Promise.resolve() : Promise.reject(new Error('Необходимо принять политику конфиденциальности')) }]}
            >
                <Checkbox style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                    Я ознакомлен(а) с{' '}
                    <a href="/privacy-policy" target="_blank" style={{ color: 'var(--brand-navy)' }}>Политикой конфиденциальности</a>
                </Checkbox>
            </Form.Item>
            <Form.Item
                name="personalData" valuePropName="checked" noStyle
                rules={[{ validator: (_, v) => v ? Promise.resolve() : Promise.reject(new Error('Необходимо дать согласие на обработку персональных данных')) }]}
            >
                <Checkbox style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                    Я даю согласие на{' '}
                    <a href="/personal-data" target="_blank" style={{ color: 'var(--brand-navy)' }}>обработку персональных данных</a>
                </Checkbox>
            </Form.Item>
            <Form.Item name="newsletterConsent" valuePropName="checked" noStyle>
                <Checkbox style={{ fontSize: 13, color: 'var(--ink-2)' }}>
                    Хочу получать новости, акции и специальные предложения
                </Checkbox>
            </Form.Item>
        </div>
    );

    return (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', minHeight: '100vh' }}>
            {/* Левая колонка */}
            <div style={{
                background: 'var(--gradient-brand-panel)',
                color: '#fff', padding: 48, position: 'relative', overflow: 'hidden',
                display: 'flex', flexDirection: 'column',
            }}>
                <img src="/logo-light.png" alt=""
                    style={{ position: 'absolute', right: -40, bottom: -30, height: 360, width: 'auto', opacity: 0.14, pointerEvents: 'none' }}
                />
                <div style={{ position: 'relative', zIndex: 1 }}>
                    <img src="/logo-light.png" alt="РФснаб" style={{ height: 'var(--logo-h-auth)', display: 'block' }} />
                </div>
                <div style={{ marginTop: 'auto', position: 'relative', zIndex: 1 }}>
                    <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-7xl)', fontWeight: 600, color: '#fff', letterSpacing: '-0.022em', lineHeight: 1.1, maxWidth: 460, marginBottom: 16 }}>
                        Один кабинет для всех заявок предприятия.
                    </h1>
                    <p style={{ fontSize: 'var(--text-md)', color: 'var(--overlay-white-70)', maxWidth: 420, lineHeight: 1.6, marginBottom: 32 }}>
                        Заявки, документы, согласования с менеджером, ЭДО, история закупок и аналитика — в одном защищённом аккаунте организации.
                    </p>
                    <div style={{ display: 'flex', gap: 26, fontSize: 'var(--text-sm)' }}>
                        {[['18 200', 'организаций'], ['12 480', 'товаров в наличии'], ['4.9 ★', 'рейтинг']].map(([n, l]) => (
                            <div key={l}>
                                <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-3xl)', color: '#fff', letterSpacing: '-0.01em' }}>{n}</div>
                                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--overlay-white-60)', marginTop: 2 }}>{l}</div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Правая колонка */}
            <div style={{ background: 'var(--surface)', padding: 48, overflow: 'auto', display: 'flex', flexDirection: 'column' }}>
                <div style={{ display: 'flex', justifyContent: 'flex-end', fontSize: 13, color: 'var(--ink-3)' }}>
                    Уже зарегистрированы?{' '}
                    <span onClick={() => navigate('/login')} style={{ color: 'var(--brand-red)', fontWeight: 600, marginLeft: 6, cursor: 'pointer' }}>
                        Войти
                    </span>
                </div>

                <div style={{ maxWidth: 460, margin: '32px auto auto', width: '100%' }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.018em', color: 'var(--ink-1)', marginBottom: 6 }}>
                        Создать аккаунт
                    </h2>
                    <p style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 20 }}>Выберите тип аккаунта и заполните данные</p>

                    {/* Переключатель */}
                    <div style={{ display: 'flex', background: 'var(--surface-2)', borderRadius: 8, padding: 4, border: '1px solid var(--line-1)', marginBottom: 24 }}>
                        <button style={tabBtn(accountType === 'personal')} onClick={() => setAccountType('personal')}>
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                                <circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/>
                            </svg>
                            Физическое лицо
                        </button>
                        <button style={tabBtn(accountType === 'legal')} onClick={() => setAccountType('legal')}>
                            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
                                <rect x="4" y="4" width="16" height="16" rx="1"/><path d="M9 8h2M13 8h2M9 12h2M13 12h2M9 16h6"/>
                            </svg>
                            Юридическое лицо
                        </button>
                    </div>

                    {/* Форма физлица */}
                    {accountType === 'personal' && (
                        <Form<PersonalFormValues> form={personalForm} layout="vertical" onFinish={handlePersonalSubmit} autoComplete="off">
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
                                <Form.Item name="confirmPassword" noStyle dependencies={['password']} rules={[
                                    { required: true, message: 'Подтвердите пароль' },
                                    ({ getFieldValue }) => ({ validator(_, value) { if (!value || getFieldValue('password') === value) return Promise.resolve(); return Promise.reject(new Error('Пароли не совпадают')); } }),
                                ]}>
                                    <Input.Password style={inputStyle} placeholder="Повторите пароль" />
                                </Form.Item>
                            </div>
                            <ConsentFields />
                            <Form.Item style={{ marginTop: 22 }}>
                                <SubmitButton label="Зарегистрироваться" />
                            </Form.Item>
                        </Form>
                    )}

                    {/* Форма юрлица */}
                    {accountType === 'legal' && (
                        <Form<LegalFormValues> form={legalForm} layout="vertical" onFinish={handleLegalSubmit} autoComplete="off">
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                <div>
                                    <label style={labelStyle}>ИНН</label>
                                    <Form.Item name="inn" noStyle rules={[
                                        { required: true, message: 'Введите ИНН' },
                                        { pattern: /^\d{10}$|^\d{12}$/, message: 'ИНН — 10 или 12 цифр' },
                                    ]}>
                                        <Input style={inputStyle} placeholder="1234567890" maxLength={12} />
                                    </Form.Item>
                                </div>
                                <div>
                                    <label style={labelStyle}>ОГРН</label>
                                    <Form.Item name="ogrn" noStyle rules={[
                                        { required: true, message: 'Введите ОГРН' },
                                        { pattern: /^\d{13}$|^\d{15}$/, message: 'ОГРН — 13 или 15 цифр' },
                                    ]}>
                                        <Input style={inputStyle} placeholder="1234567890123" maxLength={15} />
                                    </Form.Item>
                                </div>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>Полное название организации</label>
                                <Form.Item name="fullName" noStyle rules={[{ required: true, message: 'Введите название' }]}>
                                    <Input style={inputStyle} placeholder='ООО "Ромашка"' />
                                </Form.Item>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>Директор (ФИО)</label>
                                <Form.Item name="director" noStyle rules={[{ required: true, message: 'Введите ФИО директора' }]}>
                                    <Input style={inputStyle} placeholder="Иванов Иван Иванович" />
                                </Form.Item>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>Юридический адрес</label>
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 80px', gap: 8 }}>
                                    <Form.Item name="legalCity" noStyle rules={[{ required: true, message: 'Город' }]}>
                                        <Input style={inputStyle} placeholder="Город" />
                                    </Form.Item>
                                    <Form.Item name="legalStreet" noStyle rules={[{ required: true, message: 'Улица' }]}>
                                        <Input style={inputStyle} placeholder="Улица" />
                                    </Form.Item>
                                    <Form.Item name="legalBuilding" noStyle rules={[{ required: true, message: 'Дом' }]}>
                                        <Input style={inputStyle} placeholder="Дом" />
                                    </Form.Item>
                                </div>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>Телефон</label>
                                <Form.Item name="phone" noStyle rules={[
                                    { required: true, message: 'Введите телефон' },
                                    { pattern: /^\+?[0-9]{11}$/, message: 'Телефон должен содержать 11 цифр' },
                                ]}>
                                    <Input style={inputStyle} placeholder="+79001234567" />
                                </Form.Item>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>E-mail</label>
                                <Form.Item name="email" noStyle rules={[{ required: true, message: 'Введите email' }, { type: 'email', message: 'Некорректный формат email' }]}>
                                    <Input style={inputStyle} placeholder="info@company.ru" />
                                </Form.Item>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>Пароль</label>
                                <Form.Item name="password" noStyle rules={[{ required: true, message: 'Введите пароль' }, { min: 8, message: 'Минимум 8 символов' }]}>
                                    <Input.Password style={inputStyle} placeholder="Минимум 8 символов" />
                                </Form.Item>
                            </div>
                            <div style={{ marginTop: 12 }}>
                                <label style={labelStyle}>Подтверждение пароля</label>
                                <Form.Item name="confirmPassword" noStyle dependencies={['password']} rules={[
                                    { required: true, message: 'Подтвердите пароль' },
                                    ({ getFieldValue }) => ({ validator(_, value) { if (!value || getFieldValue('password') === value) return Promise.resolve(); return Promise.reject(new Error('Пароли не совпадают')); } }),
                                ]}>
                                    <Input.Password style={inputStyle} placeholder="Повторите пароль" />
                                </Form.Item>
                            </div>
                            <ConsentFields />
                            <Form.Item style={{ marginTop: 22 }}>
                                <SubmitButton label="Подать заявку на регистрацию" />
                            </Form.Item>
                        </Form>
                    )}

                    {/* Яндекс — только для физлиц */}
                    {accountType === 'personal' && (
                        <>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '4px 0 16px', fontSize: 12, color: 'var(--ink-3)' }}>
                                <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                                <span>или</span>
                                <div style={{ flex: 1, height: 1, background: 'var(--line-1)' }} />
                            </div>
                            <button
                                onClick={() => window.location.href = 'http://localhost:8080/auth/oauth2/register/yandex'}
                                style={{
                                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                                    width: '100%', height: 'var(--btn-h-lg)', border: '1px solid var(--line-2)', background: 'var(--surface)',
                                    color: 'var(--ink-1)', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                }}
                                onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-2)'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--surface)'; }}
                            >
                                <svg width="16" height="16" viewBox="0 0 16 16"><circle cx="8" cy="8" r="8" fill="#FC3F1D" /><path d="M9.2 4.2v7.6h-1V8.4L6.3 12H5l2-3.8c-1-.3-1.6-.9-1.6-2.2 0-1.4 1-2.4 2.3-2.4z" fill="#fff" /></svg>
                                Зарегистрироваться через Яндекс
                            </button>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default RegisterPage;
