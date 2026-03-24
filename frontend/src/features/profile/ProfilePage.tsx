import { useState } from 'react';
import {
    Card,
    Form,
    Input,
    Button,
    Typography,
    Spin,
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
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getProfile, updateProfile } from '@/api/profile';
import type { UpdateProfileRequest } from '@/api/profile';
import type { AxiosError } from 'axios';

const { Title, Text } = Typography;

const ProfilePage = () => {
    const [editing, setEditing] = useState(false);
    const [form] = Form.useForm<UpdateProfileRequest>();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    // Загрузка профиля
    const { data: profile, isLoading } = useQuery({
        queryKey: ['profile'],
        queryFn: getProfile,
    });

    // Мутация для обновления профиля
    const updateMutation = useMutation({
        mutationFn: (values: UpdateProfileRequest) =>
            updateProfile(profile!.id, values),
        onSuccess: () => {
            messageApi.success('Профиль обновлён');
            queryClient.invalidateQueries({ queryKey: ['profile'] });
            setEditing(false);
        },
        onError: (error: AxiosError<{ message?: string }>) => {
            const errorMessage =
                error.response?.data?.message || 'Ошибка при обновлении профиля';
            messageApi.error(errorMessage);
        },
    });

    const handleEdit = () => {
        if (profile) {
            form.setFieldsValue({
                firstname: profile.firstname,
                lastname: profile.lastname,
                surname: profile.surname || '',
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

    /** Форматирует дату */
    const formatDate = (dateStr: string): string =>
        new Date(dateStr).toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
        });

    if (isLoading) {
        return (
            <div style={{ textAlign: 'center', padding: 120 }}>
                <Spin size="large" tip="Загрузка профиля..." />
            </div>
        );
    }

    if (!profile) return null;

    return (
        <div style={{ maxWidth: 700, margin: '0 auto' }}>
            <Title level={2} style={{ marginBottom: 24 }}>
                Профиль
            </Title>

            {/* Основная информация */}
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
                            {profile.lastname}
                        </Descriptions.Item>
                        <Descriptions.Item label="Имя">
                            {profile.firstname}
                        </Descriptions.Item>
                        <Descriptions.Item label="Отчество">
                            {profile.surname || '—'}
                        </Descriptions.Item>
                    </Descriptions>
                )}
            </Card>

            {/* Email и аккаунт */}
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
                            <Text>{profile.email}</Text>
                            {profile.emailVerified ? (
                                <Tag color="green">Подтверждён</Tag>
                            ) : (
                                <Tag color="orange">Не подтверждён</Tag>
                            )}
                        </Space>
                    </Descriptions.Item>
                    <Descriptions.Item label="Дата регистрации">
                        {formatDate(profile.createdAt)}
                    </Descriptions.Item>
                </Descriptions>
            </Card>
        </div>
    );
};

export default ProfilePage;