import { useState } from 'react';
import {
    Row,
    Col,
    Card,
    Input,
    Pagination,
    Typography,
    Spin,
    Empty,
    Space,
    Breadcrumb,
    App,
} from 'antd';
import {
    SearchOutlined,
    HomeOutlined,
    AppstoreOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getProducts, searchProducts } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import { useCartStore } from '@/store/cartStore';
import type { CategoryTree } from '@/types/product';
import CategoryTreeMenu from './CategoryTreeMenu';
import ProductCard from './ProductCard';

const { Title, Text } = Typography;
const { Search } = Input;

/** Находит категорию по ID в дереве (рекурсивный поиск) */
const findCategoryInTree = (
    tree: CategoryTree[],
    id: number,
): CategoryTree | null => {
    for (const node of tree) {
        if (node.id === id) return node;
        const found = findCategoryInTree(node.children, id);
        if (found) return found;
    }
    return null;
};

/** Строит цепочку от корня до выбранной категории (для хлебных крошек) */
const buildBreadcrumb = (
    tree: CategoryTree[],
    targetId: number,
): CategoryTree[] => {
    const path: CategoryTree[] = [];

    const find = (nodes: CategoryTree[], trail: CategoryTree[]): boolean => {
        for (const node of nodes) {
            const currentTrail = [...trail, node];
            if (node.id === targetId) {
                path.push(...currentTrail);
                return true;
            }
            if (find(node.children, currentTrail)) return true;
        }
        return false;
    };

    find(tree, []);
    return path;
};

const CatalogPage = () => {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { message: messageApi } = App.useApp();
    const addItem = useCartStore((state) => state.addItem);

    const currentPage = Number(searchParams.get('page')) || 1;
    const categoryId = searchParams.get('category')
        ? Number(searchParams.get('category'))
        : undefined;
    const searchQuery = searchParams.get('q') || '';

    const [searchInput, setSearchInput] = useState(searchQuery);

    const { data: categoryTree = [], isLoading: categoriesLoading } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    const {
        data: productsPage,
        isLoading: productsLoading,
        isError,
    } = useQuery({
        queryKey: ['products', { page: currentPage, categoryId, q: searchQuery }],
        queryFn: async () => {
            if (searchQuery) {
                const results = await searchProducts(searchQuery);
                return {
                    content: results,
                    totalElements: results.length,
                    totalPages: 1,
                    number: 0,
                    size: results.length,
                    first: true,
                    last: true,
                    empty: results.length === 0,
                };
            }
            return getProducts({ page: currentPage - 1, categoryId });
        },
    });

    const breadcrumbItems = categoryId
        ? buildBreadcrumb(categoryTree, categoryId)
        : [];

    const handleCategorySelect = (selectedKeys: React.Key[]) => {
        const newParams = new URLSearchParams();
        if (selectedKeys.length > 0) {
            newParams.set('category', String(selectedKeys[0]));
        }
        setSearchParams(newParams);
        setSearchInput('');
    };

    const handleSearch = (value: string) => {
        const trimmed = value.trim();
        const newParams = new URLSearchParams();
        if (trimmed) {
            newParams.set('q', trimmed);
        }
        setSearchParams(newParams);
    };

    const handlePageChange = (page: number) => {
        const newParams = new URLSearchParams(searchParams);
        newParams.set('page', String(page));
        setSearchParams(newParams);
        window.scrollTo({ top: 0, behavior: 'smooth' });
    };

    const handleClearFilters = () => {
        setSearchParams({});
        setSearchInput('');
    };

    const handleAddToCart = async (productId: number) => {
        try {
            await addItem(productId, 1);
            messageApi.success('Товар добавлен в корзину');
        } catch {
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    return (
        <div>
            <Breadcrumb
                style={{ marginBottom: 16 }}
                items={[
                    {
                        title: (
                            <span onClick={handleClearFilters} style={{ cursor: 'pointer' }}>
                                <HomeOutlined /> Каталог
                            </span>
                        ),
                    },
                    ...breadcrumbItems.map((cat) => ({
                        title: (
                            <span
                                onClick={() => {
                                    const params = new URLSearchParams();
                                    params.set('category', String(cat.id));
                                    setSearchParams(params);
                                }}
                                style={{ cursor: 'pointer' }}
                            >
                                {cat.name}
                            </span>
                        ),
                    })),
                    ...(searchQuery ? [{ title: `Поиск: «${searchQuery}»` }] : []),
                ]}
            />

            <Row gutter={24}>
                {/* Левая колонка — дерево категорий */}
                <Col xs={24} md={6}>
                    <Card
                        title={
                            <Space>
                                <AppstoreOutlined />
                                <span>Категории</span>
                            </Space>
                        }
                        size="small"
                        style={{ marginBottom: 16 }}
                    >
                        {categoriesLoading ? (
                            <div style={{ textAlign: 'center', padding: 24 }}>
                                <Spin />
                            </div>
                        ) : (
                            <CategoryTreeMenu
                                categories={categoryTree}
                                selectedId={categoryId}
                                onSelect={(id) => handleCategorySelect([id])}
                            />
                        )}
                        {categoryId && (
                            <a
                                onClick={handleClearFilters}
                                style={{ display: 'block', marginTop: 12, textAlign: 'center' }}
                            >
                                Показать все товары
                            </a>
                        )}
                    </Card>
                </Col>

                {/* Правая колонка — поиск + карточки товаров */}
                <Col xs={24} md={18}>
                    <Search
                        placeholder="Поиск товаров..."
                        allowClear
                        enterButton={
                            <>
                                <SearchOutlined /> Найти
                            </>
                        }
                        size="large"
                        value={searchInput}
                        onChange={(e) => setSearchInput(e.target.value)}
                        onSearch={handleSearch}
                        style={{ marginBottom: 24 }}
                    />

                    {productsPage && !productsLoading && (
                        <div
                            style={{
                                marginBottom: 16,
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                            }}
                        >
                            <Title level={4} style={{ margin: 0 }}>
                                {categoryId
                                    ? (findCategoryInTree(categoryTree, categoryId)?.name ?? 'Товары')
                                    : searchQuery
                                        ? `Результаты поиска: «${searchQuery}»`
                                        : 'Все товары'}
                            </Title>
                            <Text type="secondary">Найдено: {productsPage.totalElements}</Text>
                        </div>
                    )}

                    {productsLoading && (
                        <div style={{ textAlign: 'center', padding: 80 }}>
                            <Spin size="large" tip="Загрузка товаров..." />
                        </div>
                    )}

                    {isError && (
                        <Empty
                            description="Не удалось загрузить товары. Проверьте подключение к серверу."
                            style={{ padding: 80 }}
                        />
                    )}

                    {productsPage?.empty && !productsLoading && (
                        <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={
                                searchQuery
                                    ? `По запросу «${searchQuery}» ничего не найдено`
                                    : 'В данной категории пока нет товаров'
                            }
                        />
                    )}

                    {productsPage && !productsPage.empty && !productsLoading && (
                        <>
                            <Row gutter={[16, 16]}>
                                {productsPage.content.map((product) => (
                                    <Col xs={24} sm={12} lg={8} xl={8} key={product.id}>
                                        <ProductCard
                                            product={product}
                                            onClick={() => navigate(`/products/${product.id}`)}
                                            onAddToCart={handleAddToCart}
                                        />
                                    </Col>
                                ))}
                            </Row>

                            {productsPage.totalPages > 1 && (
                                <div style={{ textAlign: 'center', marginTop: 32 }}>
                                    <Pagination
                                        current={currentPage}
                                        total={productsPage.totalElements}
                                        pageSize={productsPage.size}
                                        onChange={handlePageChange}
                                        showSizeChanger={false}
                                        showTotal={(total) => `Всего ${total} товаров`}
                                    />
                                </div>
                            )}
                        </>
                    )}
                </Col>
            </Row>
        </div>
    );
};

export default CatalogPage;