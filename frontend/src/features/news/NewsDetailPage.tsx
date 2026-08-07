import { useQuery } from '@tanstack/react-query';
import { Typography, Spin, Alert, Breadcrumb } from 'antd';
import { Link, useParams } from 'react-router-dom';
import { getNewsBySlug } from '@/api/news';
import NewsHtml from '@/components/news/NewsHtml';
import VideoEmbed from '@/components/news/VideoEmbed';

const { Title, Text } = Typography;

const formatDate = (iso: string | null): string =>
    iso ? new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }) : '';

const NewsDetailPage = () => {
    const { slug } = useParams<{ slug: string }>();

    const { data: news, isLoading, isError } = useQuery({
        queryKey: ['news', slug],
        queryFn: () => getNewsBySlug(slug!),
        enabled: !!slug,
    });

    if (isLoading) {
        return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
    }

    if (isError || !news) {
        return <Alert type="error" message="Новость не найдена" showIcon />;
    }

    return (
        <div style={{ maxWidth: 800, margin: '0 auto' }}>
            <Breadcrumb
                style={{ marginBottom: 16 }}
                items={[
                    { title: <Link to="/">Главная</Link> },
                    { title: <Link to="/news">Новости</Link> },
                    { title: news.title },
                ]}
            />

            <Title level={2} style={{ marginBottom: 4 }}>{news.title}</Title>
            <Text type="secondary" style={{ fontSize: 13 }}>{formatDate(news.publishedAt)}</Text>

            {news.coverImageUrl && (
                <img
                    src={news.coverImageUrl}
                    alt=""
                    style={{
                        width: '100%', maxHeight: 420, objectFit: 'cover',
                        borderRadius: 'var(--r-4)', margin: '20px 0',
                    }}
                />
            )}

            {(news.videoEmbedUrl || news.videoUrl) && (
                <div style={{ margin: '20px 0' }}>
                    <VideoEmbed embedUrl={news.videoEmbedUrl} videoUrl={news.videoUrl} />
                </div>
            )}

            <div style={{ marginTop: 20 }}>
                <NewsHtml html={news.contentHtml} />
            </div>
        </div>
    );
};

export default NewsDetailPage;
