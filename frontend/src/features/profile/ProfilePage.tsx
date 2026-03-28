import { useState } from 'react';
import {
    Card,
    Form,
    Input,
    Button,
    Typography,
    Descriptions,
    Space,
    Tag,
    App,
} from 'antd';
import {
    UserOutlined,
    MailOutlined,
    EditOutlined,
    SaveOutlined,
    CloseOutlined,
} from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import { updateProfile } from '@/api/profile';
import type { UpdateProfileRequest } from '@/api/profile';
import type { AxiosError } from 'axios';

const { Title, Text } = Typography;

const ProfilePage = () => {
    const [editing, setEditing] = useState(false);
    const [form] = Form.useForm<UpdateProfileRequest>();
    const { message: messageApi } = App.useApp();
    const user = useAuthStore((state) => state.user);

    const updateMutation = useMutation({
        mutationFn: (values: UpdateProfileRequest) =>
            updateProfile(user!.id, values),
        onSuccess: () => {
            messageApi.success('Профиль обновлён');
            setEditing(false);
            // TODO: обновить user в authStore после редактирования
        },
        onError: (error: AxiosError<{ message?: string }>) => {
            const errorMessage =
                error.response?.data?.message || 'Ошибка при обновлении профиля';
            messageApi.error(errorMessage);
        },
    });

    const handleEdit = () => {
        if (user) {
            form.setFieldsValue({
                firstname: user.firstname,
                lastname: user.lastname,
                surname: user.surname || '',
            });
        }
        setEditing(true);
    };

    const handleSave = (values: UpdateProfileRequest) => {
        updateMutation.mutate(values);
    };

    const handleCancel = () => {
        setEditing(false);
        form.resetFields();
    };

    const formatDate = (dateStr: string): string => {
        if (!dateStr) return '—';
        return new Date(dateStr).toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
        });
    };

    if (!user) return null;

    return (
        <div style={{ maxWidth: 700, margin: '0 auto' }}>
            <Title level={2} style={{ marginBottom: 24 }}>
                Профиль
            </Title>

            <Card
                title={
                    <Space>
                        <UserOutlined />
                        <span>Личные данные</span>
                    </Space>
                }
                extra={
                    !editing && (
                        <Button icon={<EditOutlined />} onClick={handleEdit}>
                            Редактировать
                        </Button>
                    )
                }
                style={{ marginBottom: 16 }}
            >
                {editing ? (
                    <Form<UpdateProfileRequest>
                        form={form}
                        layout="vertical"
                        onFinish={handleSave}
                    >
                        <Form.Item
                            name="lastname"
                            label="Фамилия"
                            rules={[
                                { required: true, message: 'Введите фамилию' },
                                { min: 2, message: 'Минимум 2 символа' },
                            ]}
                        >
                            <Input placeholder="Фамилия" />
                        </Form.Item>

                        <Form.Item
                            name="firstname"
                            label="Имя"
                            rules={[
                                { required: true, message: 'Введите имя' },
                                { min: 2, message: 'Минимум 2 символа' },
                            ]}
                        >
                            <Input placeholder="Имя" />
                        </Form.Item>

                        <Form.Item name="surname" label="Отчество">
                            <Input placeholder="Отчество (необязательно)" />
                        </Form.Item>

                        <Space>
                            <Button
                                type="primary"
                                htmlType="submit"
                                icon={<SaveOutlined />}
                                loading={updateMutation.isPending}
                            >
                                Сохранить
                            </Button>
                            <Button icon={<CloseOutlined />} onClick={handleCancel}>
                                Отмена
                            </Button>
                        </Space>
                    </Form>
                ) : (
                    <Descriptions column={1} labelStyle={{ width: 150 }}>
                        <Descriptions.Item label="Фамилия">
                            {user.lastname}
                        </Descriptions.Item>
                        <Descriptions.Item label="Имя">
                            {user.firstname}
                        </Descriptions.Item>
                        <Descriptions.Item label="Отчество">
                            {user.surname || '—'}
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Card>

            <Card
                title={
                    <Space>
                        <MailOutlined />
                        <span>Аккаунт</span>
                    </Space>
                }
            >
                <Descriptions column={1} labelStyle={{ width: 150 }}>
                    <Descriptions.Item label="Email">
                        <Space>
                            <Text>{user.email}</Text>
                            {user.emailVerified ? (
                                <Tag color="green">Подтверждён</Tag>
                            ) : (
                                <Tag color="orange">Не подтверждён</Tag>
                            )}
                        </Space>
                    </Descriptions.Item>
                    <Descriptions.Item label="Дата регистрации">
                        {formatDate(user.createdAt)}
                    </Descriptions.Item>
                </Descriptions>
            </Card>
        </div>
    );
};

export default ProfilePage;