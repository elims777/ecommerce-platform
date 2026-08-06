import { Card, Form, Input, Button, Switch, Typography, Space, App, Spin, Upload } from 'antd';
import { ArrowLeftOutlined, UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { getAdminNewsById, createNews, updateNews, uploadNewsImage } from '@/api/news';
import RichTextEditor from '@/components/editor/RichTextEditor';
import type { NewsRequest } from '@/types/news';

const { Title, Text } = Typography;

const AdminNewsEditPage = () => {
    const { id } = useParams<{ id: string }>();
    const isNew = id === 'new';
    const newsId = isNew ? null : Number(id);

    const { message: messageApi } = App.useApp();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [form] = Form.useForm();

    const [contentHtml, setContentHtml] = useState('');
    const [coverImageUrl, setCoverImageUrl] = useState<string | null>(null);
    const [uploadingCover, setUploadingCover] = useState(false);

    const { data: news, isLoading } = useQuery({
        queryKey: ['adminNews', 'detail', newsId],
        queryFn: () => getAdminNewsById(newsId!),
        enabled: newsId !== null,
    });

    useEffect(() => {
        if (news) {
            form.setFieldsValue({
                title: news.title,
                videoUrl: news.videoUrl,
                isPublished: news.isPublished,
            });
            setContentHtml(news.contentHtml);
            setCoverImageUrl(news.coverImageUrl);
        }
    }, [news, form]);

    const saveMutation = useMutation({
        mutationFn: (request: NewsRequest) =>
            newsId === null ? createNews(request) : updateNews(newsId, request),
        onSuccess: () => {
            messageApi.success(isNew ? 'Новость создана' : 'Новость сохранена');
            queryClient.invalidateQueries({ queryKey: ['adminNews'] });
            // Публичная часть: /news, /news/:slug и блок на главной — иначе там висит старая версия
            queryClient.invalidateQueries({ queryKey: ['news'] });
            navigate('/admin/news');
        },
        onError: () => messageApi.error('Не удалось сохранить новость'),
    });

    const handleSubmit = (values: { title: string; videoUrl?: string; isPublished?: boolean }) => {
        if (!contentHtml.trim() || contentHtml === '<p></p>') {
            messageApi.error('Текст новости не может быть пустым');
            return;
        }

        saveMutation.mutate({
            title: values.title,
            videoUrl: values.videoUrl?.trim() || null,
            coverImageUrl,
            contentHtml,
            isPublished: values.isPublished ?? false,
        });
    };

    const handleCoverUpload = async (file: File) => {
        setUploadingCover(true);
        try {
            setCoverImageUrl(await uploadNewsImage(file));
        } catch {
            messageApi.error('Не удалось загрузить обложку');
        } finally {
            setUploadingCover(false);
        }
        return false;
    };

    if (newsId !== null && isLoading) {
        return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
    }

    return (
        <div style={{ maxWidth: 900 }}>
            <Space style={{ marginBottom: 16 }}>
                <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/news')}>Назад</Button>
                <Title level={3} style={{ margin: 0 }}>{isNew ? 'Новая новость' : 'Редактирование новости'}</Title>
            </Space>

            <Form form={form} layout="vertical" onFinish={handleSubmit} initialValues={{ isPublished: false }}>
                <Card style={{ marginBottom: 16 }}>
                    <Form.Item
                        name="title"
                        label="Заголовок"
                        rules={[
                            { required: true, message: 'Введите заголовок' },
                            { min: 3, max: 255, message: 'От 3 до 255 символов' },
                        ]}
                    >
                        <Input placeholder="Например: Новое поступление спецодежды" size="large" />
                    </Form.Item>

                    <Form.Item label="Обложка">
                        {coverImageUrl ? (
                            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12 }}>
                                <img
                                    src={coverImageUrl}
                                    alt=""
                                    width={200}
                                    style={{ borderRadius: 8, objectFit: 'cover' }}
                                />
                                <Button danger icon={<DeleteOutlined />} onClick={() => setCoverImageUrl(null)}>
                                    Убрать
                                </Button>
                            </div>
                        ) : (
                            <Upload beforeUpload={handleCoverUpload} showUploadList={false} accept="image/*">
                                <Button icon={<UploadOutlined />} loading={uploadingCover}>Загрузить обложку</Button>
                            </Upload>
                        )}
                    </Form.Item>

                    <Form.Item
                        name="videoUrl"
                        label="Ссылка на видео"
                        extra="YouTube, VK или RuTube. Видео будет проигрываться прямо на странице новости."
                    >
                        <Input placeholder="https://vk.com/video-123_456" />
                    </Form.Item>
                </Card>

                <Card title="Текст новости" style={{ marginBottom: 16 }}>
                    <RichTextEditor value={contentHtml} onChange={setContentHtml} />
                    <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
                        В тексте можно вставлять ссылки и картинки — картинки загружаются в хранилище.
                    </Text>
                </Card>

                <Card>
                    <Form.Item name="isPublished" label="Опубликовать" valuePropName="checked">
                        <Switch checkedChildren="Да" unCheckedChildren="Нет" />
                    </Form.Item>

                    <Space>
                        <Button type="primary" htmlType="submit" loading={saveMutation.isPending} size="large">
                            Сохранить
                        </Button>
                        <Button onClick={() => navigate('/admin/news')} size="large">Отмена</Button>
                    </Space>
                </Card>
            </Form>
        </div>
    );
};

export default AdminNewsEditPage;
