import { useState } from 'react';
import { Card, Form, Input, Button, Typography, App, Divider } from 'antd';
import { MailOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { LoginRequest } from '@/types/auth';
import { AxiosError } from 'axios';

const { Title, Text } = Typography;

const LoginPage = () => {
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const login = useAuthStore((state) => state.login);
    const { message: messageApi } = App.useApp();

    // Откуда пользователь пришёл — туда и вернём после логина
    const from = (location.state as { from?: { pathname: string } })?.from?.pathname || '/';

    const handleSubmit = async (values: LoginRequest) => {
        setLoading(true);
        try {
            await login(values);
            messageApi.success('Вы успешно вошли в систему');
            navigate(from, { replace: true });
        } catch (error) {
            const axiosError = error as AxiosError<{ message?: string }>;
            const errorMessage =
                axiosError.response?.data?.message || 'Неверный email или пароль';
            messageApi.error(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div
            style={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                minHeight: '60vh',
            }}
        >
            <Card style={{ width: 420, boxShadow: '0 2px 8px rgba(0,0,0,0.09)' }}>
                <div style={{ textAlign: 'center', marginBottom: 24 }}>
                    <img src="/logo.png" alt="РФснаб" style={{ height: 48, marginBottom: 16 }} />
                    <Title level={3} style={{ marginBottom: 4 }}>Вход в систему</Title>
                    <Text type="secondary">Введите ваш email и пароль</Text>
                </div>

                <Form<LoginRequest>
                    layout="vertical"
                    onFinish={handleSubmit}
                    autoComplete="off"
                    size="large"
                >
                    <Form.Item
                        name="email"
                        rules={[
                            { required: true, message: 'Введите email' },
                            { type: 'email', message: 'Некорректный формат email' },
                        ]}
                    >
                        <Input prefix={<MailOutlined />} placeholder="Email" />
                    </Form.Item>

                    <Form.Item
                        name="password"
                        rules={[
                            { required: true, message: 'Введите пароль' },
                            { min: 8, message: 'Минимум 8 символов' },
                        ]}
                    >
                        <Input.Password prefix={<LockOutlined />} placeholder="Пароль" />
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" loading={loading} block>
                            Войти
                        </Button>
                    </Form.Item>
                </Form>


                <Divider plain>или</Divider>

                <Button
                    block
                    size="large"
                    style={{ marginBottom: 16 }}
                    onClick={() => window.location.href = '/oauth2/authorization/yandex'}
                >
                    Войти через Яндекс
                </Button>
                <div style={{ textAlign: 'center' }}>
                    <Text>Нет аккаунта? </Text>
                    <Link to="/register">Зарегистрироваться</Link>
                </div>
            </Card>
        </div>
    );
};

export default LoginPage;