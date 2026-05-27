import { useState } from 'react';
import { Input, Pagination, Spin, Empty, App, Skeleton } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { getProducts, searchProducts } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import { useCartStore } from '@/store/cartStore';
import type { CategoryTree } from '@/types/product';
import CategoryTreeMenu from './CategoryTreeMenu';
import ProductCard from './ProductCard';

const { Search } = Input;

const findCategoryInTree = (tree: CategoryTree[], id: number): CategoryTree | null => {
    for (const node of tree) {
        if (node.id === id) return node;
        const found = findCategoryInTree(node.children, id);
        if (found) return found;
    }
    return null;
};

const buildBreadcrumb = (tree: CategoryTree[], targetId: number): CategoryTree[] => {
    const path: CategoryTree[] = [];
    const find = (nodes: CategoryTree[], trail: CategoryTree[]): boolean => {
        for (const node of nodes) {
            const currentTrail = [...trail, node];
            if (node.id === targetId) { path.push(...currentTrail); return true; }
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
    const categoryId = searchParams.get('category') ? Number(searchParams.get('category')) : undefined;
    const searchQuery = searchParams.get('q') || '';
    const [searchInput, setSearchInput] = useState(searchQuery);

    const { data: categoryTree = [], isLoading: categoriesLoading } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    const { data: productsPage, isLoading: productsLoading, isError } = useQuery({
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

    const breadcrumbItems = categoryId ? buildBreadcrumb(categoryTree, categoryId) : [];

    const handleCategorySelect = (selectedKeys: React.Key[]) => {
        const newParams = new URLSearchParams();
        if (selectedKeys.length > 0) newParams.set('category', String(selectedKeys[0]));
        setSearchParams(newParams);
        setSearchInput('');
    };

    const handleSearch = (value: string) => {
        const trimmed = value.trim();
        const newParams = new URLSearchParams();
        if (trimmed) newParams.set('q', trimmed);
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

    const currentCategory = categoryId ? findCategoryInTree(categoryTree, categoryId) : null;
    const pageTitle = categoryId
        ? (currentCategory?.name ?? 'Товары')
        : searchQuery
            ? `Результаты поиска: «${searchQuery}»`
            : 'Все товары';

    return (
        <div style={{ paddingTop: 20, paddingBottom: 60 }}>
            {/* Хлебные крошки */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12.5, color: 'var(--ink-3)', marginBottom: 16 }}>
                <span onClick={handleClearFilters} style={{ cursor: 'pointer' }}>Каталог</span>
                {breadcrumbItems.map((cat) => (
                    <span key={cat.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ opacity: 0.5 }}>›</span>
                        <span
                            onClick={() => {
                                const params = new URLSearchParams();
                                params.set('category', String(cat.id));
                                setSearchParams(params);
                            }}
                            style={{ cursor: 'pointer', color: 'var(--ink-2)', fontWeight: 500 }}
                        >
                            {cat.name}
                        </span>
                    </span>
                ))}
                {searchQuery && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <span style={{ opacity: 0.5 }}>›</span>
                        <span>Поиск: «{searchQuery}»</span>
                    </span>
                )}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 20 }}>
                {/* Сайдбар */}
                <aside>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 6, background: 'var(--surface)', overflow: 'hidden', marginBottom: 16 }}>
                        <div style={{ padding: '10px 14px 8px', fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                            Каталог
                        </div>
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
                            <div style={{ padding: '8px 14px 12px', borderTop: '1px solid var(--line-1)' }}>
                                <span
                                    onClick={handleClearFilters}
                                    style={{ fontSize: 12.5, color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}
                                >
                                    Показать все товары
                                </span>
                            </div>
                        )}
                    </div>
                </aside>

                {/* Правая колонка */}
                <div>
                    <Search
                        placeholder="Поиск товаров..."
                        allowClear
                        enterButton={<SearchOutlined />}
                        size="large"
                        value={searchInput}
                        onChange={(e) => setSearchInput(e.target.value)}
                        onSearch={handleSearch}
                        style={{ marginBottom: 20 }}
                    />

                    {productsPage && !productsLoading && (
                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 16 }}>
                            <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 28, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', margin: 0 }}>
                                {pageTitle}
                            </h1>
                            <span style={{ fontSize: 14, color: 'var(--ink-3)' }}>
                                {productsPage.totalElements} товаров
                            </span>
                        </div>
                    )}

                    {productsLoading && (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 16, padding: '16px 0' }}>
                            {Array.from({ length: 12 }).map((_, i) => (
                                <div key={i} style={{ background: '#fff', borderRadius: 10, padding: 16, border: '1px solid var(--line-1)' }}>
                                    <Skeleton.Image active style={{ width: '100%', height: 180, borderRadius: 6 }} />
                                    <div style={{ marginTop: 12 }}>
                                        <Skeleton active paragraph={{ rows: 2 }} title={{ width: '60%' }} />
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {isError && (
                        <Empty description="Не удалось загрузить товары. Проверьте подключение к серверу." style={{ padding: 80 }} />
                    )}

                    {productsPage?.empty && !productsLoading && (
                        <div style={{ textAlign: 'center', padding: '60px 0' }}>
                            <div style={{ fontSize: 32, marginBottom: 12, color: 'var(--ink-4)' }}>🔍</div>
                            <div style={{ fontFamily: 'var(--font-head)', fontSize: 18, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>
                                {searchQuery ? `По запросу «${searchQuery}» ничего не найдено` : 'В этой категории пока нет товаров'}
                            </div>
                            <div style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 20 }}>
                                {searchQuery ? 'Попробуйте изменить запрос или выбрать категорию' : 'Скоро здесь появятся новинки'}
                            </div>
                            <button
                                onClick={handleClearFilters}
                                style={{ display: 'inline-flex', alignItems: 'center', height: 40, padding: '0 16px', border: '1px solid var(--brand-navy)', color: 'var(--brand-navy)', background: 'transparent', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                            >
                                Все товары
                            </button>
                        </div>
                    )}

                    {productsPage && !productsPage.empty && !productsLoading && (
                        <>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14 }}>
                                {productsPage.content.map((product) => (
                                    <ProductCard
                                        key={product.id}
                                        product={product}
                                        onClick={() => navigate(`/products/${product.id}`)}
                                        onAddToCart={handleAddToCart}
                                    />
                                ))}
                            </div>

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
                </div>
            </div>
        </div>
    );
};

export default CatalogPage;
