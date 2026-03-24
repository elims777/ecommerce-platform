import { useState } from 'react';
import { Card, Form, Input, Button, Typography, App, Divider, Row, Col, Checkbox } from 'antd';
import {
    MailOutlined,
    LockOutlined,
    UserOutlined,
} from '@ant-design/icons';
import { useNavigate, Link } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import type { RegisterRequest } from '@/types/auth';
import type { AxiosError } from 'axios';

const { Title, Text } = Typography;

/** Форма расширяет RegisterRequest полем подтверждения пароля */
interface RegisterFormValues extends RegisterRequest {
    confirmPassword: string;
    privacyPolicy: boolean;
    personalData: boolean;
}

const RegisterPage = () => {
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const register = useAuthStore((state) => state.register);
    const { message: messageApi } = App.useApp();

    const handleSubmit = async (values: RegisterFormValues) => {
        setLoading(true);
        try {
            // confirmPassword не отправляется на бэкенд
            const { confirmPassword: _, privacyPolicy: __, personalData: ___, ...request } = values;
            await register(request);
            messageApi.success('Регистрация прошла успешно!');
            navigate('/', { replace: true });
        } catch (error) {
            const axiosError = error as AxiosError<{ message?: string }>;
            const errorMessage =
                axiosError.response?.data?.message ||
                'Ошибка при регистрации. Попробуйте позже.';
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
            <Card style={{ width: 460, boxShadow: '0 2px 8px rgba(0,0,0,0.09)' }}>
                <div style={{ textAlign: 'center', marginBottom: 24 }}>
                    <img
                        src="/logo.png"
                        alt="РФснаб"
                        style={{ height: 48, marginBottom: 16 }}
                    />
                    <Title level={3} style={{ marginBottom: 4 }}>
                        Регистрация
                    </Title>
                    <Text type="secondary">Создайте аккаунт для оформления заказов</Text>
                </div>

                <Form<RegisterFormValues>
                    layout="vertical"
                    onFinish={handleSubmit}
                    autoComplete="off"
                    size="large"
                >
                    <Row gutter={12}>
                        <Col span={12}>
                            <Form.Item
                                name="firstName"
                                rules={[
                                    { required: true, message: 'Введите имя' },
                                    { min: 2, message: 'Минимум 2 символа' },
                                ]}
                            >
                                <Input prefix={<UserOutlined />} placeholder="Имя" />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="lastName"
                                rules={[
                                    { required: true, message: 'Введите фамилию' },
                                    { min: 2, message: 'Минимум 2 символа' },
                                ]}
                            >
                                <Input prefix={<UserOutlined />} placeholder="Фамилия" />
                            </Form.Item>
                        </Col>
                    </Row>

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
                            { min: 6, message: 'Минимум 6 символов' },
                        ]}
                    >
                        <Input.Password prefix={<LockOutlined />} placeholder="Пароль" />
                    </Form.Item>

                    <Form.Item
                        name="confirmPassword"
                        dependencies={['password']}
                        rules={[
                            { required: true, message: 'Подтвердите пароль' },
                            ({ getFieldValue }) => ({
                                validator(_, value) {
                                    if (!value || getFieldValue('password') === value) {
                                        return Promise.resolve();
                                    }
                                    return Promise.reject(new Error('Пароли не совпадают'));
                                },
                            }),
                        ]}
                    >
                        <Input.Password
                            prefix={<LockOutlined />}
                            placeholder="Подтвердите пароль"
                        />
                    </Form.Item>

                    <Form.Item
                        name="privacyPolicy"
                        valuePropName="checked"
                        rules={[
                            {
                                validator: (_, value) =>
                                    value
                                        ? Promise.resolve()
                                        : Promise.reject(new Error('Необходимо принять политику конфиденциальности')),
                            },
                        ]}
                    >
                        <Checkbox>
                            Я ознакомлен(а) с{' '}
                            <a href="/privacy-policy" target="_blank">
                                Политикой конфиденциальности
                            </a>
                        </Checkbox>
                    </Form.Item>

                    <Form.Item
                        name="personalData"
                        valuePropName="checked"
                        rules={[
                            {
                                validator: (_, value) =>
                                    value
                                        ? Promise.resolve()
                                        : Promise.reject(new Error('Необходимо дать согласие на обработку персональных данных')),
                            },
                        ]}
                    >
                        <Checkbox>
                            Я даю согласие на{' '}
                            <a href="/personal-data" target="_blank">
                                обработку персональных данных
                            </a>
                        </Checkbox>
                    </Form.Item>

                    <Form.Item>
                        <Button type="primary" htmlType="submit" loading={loading} block>
                            Зарегистрироваться
                        </Button>
                    </Form.Item>
                </Form>

                <Divider />

                <div style={{ textAlign: 'center' }}>
                    <Text>Уже есть аккаунт? </Text>
                    <Link to="/login">Войти</Link>
                </div>
            </Card>
        </div>
    );
};

export default RegisterPage;