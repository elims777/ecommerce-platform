import { useQuery } from '@tanstack/react-query';
import { Typography, Card, Empty, Pagination, Spin, Alert } from 'antd';
import { Link, useSearchParams } from 'react-router-dom';
import { getNews } from '@/api/news';

const { Title, Text } = Typography;

const PAGE_SIZE = 10;

const formatDate = (iso: string | null): string =>
    iso ? new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }) : '';

const NewsListPage = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const currentPage = Number(searchParams.get('page')) || 1;

    const { data, isLoading, isError } = useQuery({
        queryKey: ['news', currentPage],
        queryFn: () => getNews(currentPage - 1, PAGE_SIZE),
    });

    if (isLoading) {
        return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
    }

    if (isError) {
        return <Alert type="error" message="Не удалось загрузить новости" showIcon />;
    }

    const news = data?.content ?? [];

    return (
        <div style={{ maxWidth: 900, margin: '0 auto' }}>
            <Title level={2}>Новости</Title>

            {news.length === 0 ? (
                <Empty description="Новостей пока нет" />
            ) : (
                <>
                    {news.map((item) => (
                        <Card key={item.id} style={{ marginBottom: 16 }} hoverable>
                            <Link to={`/news/${item.slug}`} style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
                                {item.coverImageUrl && (
                                    <img
                                        src={item.coverImageUrl}
                                        alt=""
                                        width={160}
                                        height={110}
                                        loading="lazy"
                                        style={{ objectFit: 'cover', borderRadius: 'var(--r-3)', flex: '0 0 auto' }}
                                    />
                                )}
                                <div style={{ minWidth: 0 }}>
                                    <Title level={4} style={{ margin: 0, color: 'var(--ink-1)' }}>{item.title}</Title>
                                    <Text type="secondary" style={{ fontSize: 13 }}>{formatDate(item.publishedAt)}</Text>
                                </div>
                            </Link>
                        </Card>
                    ))}

                    {(data?.totalElements ?? 0) > PAGE_SIZE && (
                        <div style={{ textAlign: 'center', marginTop: 24 }}>
                            <Pagination
                                current={currentPage}
                                pageSize={PAGE_SIZE}
                                total={data?.totalElements ?? 0}
                                showSizeChanger={false}
                                onChange={(page) => setSearchParams({ page: String(page) })}
                            />
                        </div>
                    )}
                </>
            )}
        </div>
    );
};

export default NewsListPage;
