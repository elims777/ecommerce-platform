import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { App } from 'antd';
import { getProducts, searchProducts } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import type { CategoryTree } from '@/types/product';
import CategoryTreeMenu from './CategoryTreeMenu';
import ProductCard from './ProductCard';

// ── Icons ────────────────────────────────────────────────────
const SearchIcon = () => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>
    </svg>
);
const ChevRight = () => (
    <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m9 18 6-6-6-6"/>
    </svg>
);
const GridIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
    </svg>
);
const ListIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
        <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
    </svg>
);
const ChevDown = () => (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="m6 9 6 6 6-6"/>
    </svg>
);
const XIcon = () => (
    <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 6 6 18M6 6l12 12"/>
    </svg>
);

// ── Helpers ───────────────────────────────────────────────────
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

// ── Skeleton card ─────────────────────────────────────────────
const SkeletonCard = () => (
    <div style={{ width: 'var(--product-card-w)', background: '#fff', borderRadius: 8, border: '1px solid var(--line-1)', overflow: 'hidden' }}>
        <div style={{ height: 200, background: 'var(--surface-2)', animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
        <div style={{ padding: '12px 14px' }}>
            <div style={{ height: 11, width: '40%', background: 'var(--surface-3)', borderRadius: 4, marginBottom: 8, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
            <div style={{ height: 13, width: '90%', background: 'var(--surface-3)', borderRadius: 4, marginBottom: 6, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
            <div style={{ height: 13, width: '70%', background: 'var(--surface-3)', borderRadius: 4, marginBottom: 14, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
            <div style={{ height: 20, width: '50%', background: 'var(--surface-3)', borderRadius: 4, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
        </div>
    </div>
);

// ── CatalogPage ───────────────────────────────────────────────
const CatalogPage = () => {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { message: messageApi } = App.useApp();
    const addItem = useCartStore((state) => state.addItem);
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

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
    const currentCategory = categoryId ? findCategoryInTree(categoryTree, categoryId) : null;
    const pageTitle = categoryId
        ? (currentCategory?.name ?? 'Товары')
        : searchQuery
            ? `Результаты: «${searchQuery}»`
            : 'Все товары';

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
        if (!isAuthenticated) { navigate('/login'); return; }
        try {
            await addItem(productId, 1);
            messageApi.success('Товар добавлен в корзину');
        } catch {
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    const totalPages = productsPage?.totalPages ?? 0;

    // Pagination pages array
    const buildPages = (): (number | '…')[] => {
        if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1);
        const pages: (number | '…')[] = [1];
        if (currentPage > 3) pages.push('…');
        for (let p = Math.max(2, currentPage - 1); p <= Math.min(totalPages - 1, currentPage + 1); p++) pages.push(p);
        if (currentPage < totalPages - 2) pages.push('…');
        pages.push(totalPages);
        return pages;
    };

    return (
        <div style={{ paddingTop: 20, paddingBottom: 60 }}>
            {/* Breadcrumbs */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12.5, color: 'var(--ink-3)', marginBottom: 12 }}>
                <span onClick={handleClearFilters} style={{ cursor: 'pointer' }}>Главная</span>
                <ChevRight />
                <span onClick={handleClearFilters} style={{ cursor: 'pointer' }}>Каталог</span>
                {breadcrumbItems.map((cat, i) => (
                    <span key={cat.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <ChevRight />
                        <span
                            onClick={() => { const p = new URLSearchParams(); p.set('category', String(cat.id)); setSearchParams(p); }}
                            style={{ cursor: 'pointer', color: i === breadcrumbItems.length - 1 ? 'var(--ink-1)' : undefined, fontWeight: i === breadcrumbItems.length - 1 ? 500 : 400 }}
                        >
                            {cat.name}
                        </span>
                    </span>
                ))}
                {searchQuery && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                        <ChevRight />
                        <span style={{ color: 'var(--ink-1)', fontWeight: 500 }}>Поиск: «{searchQuery}»</span>
                    </span>
                )}
            </div>

            {/* Title row */}
            <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', marginBottom: 20 }}>
                <div>
                    <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', margin: 0 }}>
                        {pageTitle}
                    </h1>
                    {productsPage && !productsLoading && (
                        <p style={{ fontSize: 14, color: 'var(--ink-3)', marginTop: 6, marginBottom: 0 }}>
                            {productsPage.totalElements.toLocaleString('ru-RU')} товаров
                        </p>
                    )}
                </div>
                {/* View toggle + sort */}
                <div style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                    <div style={{ display: 'inline-flex', border: '1px solid var(--line-1)', borderRadius: 6, padding: 2, background: '#fff' }}>
                        <button style={{ width: 30, height: 28, border: 0, background: 'var(--surface-3)', borderRadius: 4, color: 'var(--ink-1)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <GridIcon />
                        </button>
                        <button style={{ width: 30, height: 28, border: 0, background: 'transparent', color: 'var(--ink-3)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <ListIcon />
                        </button>
                    </div>
                    <button style={{
                        display: 'inline-flex', alignItems: 'center', gap: 6,
                        height: 32, padding: '0 12px',
                        background: 'var(--surface-2)', border: '1px solid var(--line-1)',
                        borderRadius: 6, fontSize: 13, color: 'var(--ink-1)', cursor: 'pointer', fontFamily: 'var(--font-body)',
                    }}>
                        Сначала популярные <ChevDown />
                    </button>
                </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '260px 1fr', gap: 20 }}>
                {/* Sidebar */}
                <aside>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 6, background: '#fff', overflow: 'hidden', marginBottom: 16 }}>
                        <div style={{ padding: '10px 14px 8px', fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em' }}>
                            Каталог
                        </div>
                        {categoriesLoading ? (
                            <div style={{ padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                                {Array.from({ length: 6 }).map((_, i) => (
                                    <div key={i} style={{ height: 14, background: 'var(--surface-3)', borderRadius: 4, width: `${70 + (i % 3) * 10}%` }}/>
                                ))}
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

                {/* Right column */}
                <div>
                    {/* Search bar */}
                    <div style={{ position: 'relative', marginBottom: 20 }}>
                        <span style={{ position: 'absolute', left: 12, top: 12, color: 'var(--ink-3)', pointerEvents: 'none' }}>
                            <SearchIcon />
                        </span>
                        <input
                            value={searchInput}
                            onChange={(e) => setSearchInput(e.target.value)}
                            onKeyDown={(e) => { if (e.key === 'Enter') handleSearch(searchInput); }}
                            placeholder="Артикул, бренд или название товара..."
                            style={{
                                width: '100%', height: 40, padding: '0 110px 0 38px',
                                border: '1px solid var(--line-2)', borderRadius: 6,
                                fontSize: 14, fontFamily: 'var(--font-body)', outline: 'none',
                                background: '#fff', color: 'var(--ink-1)',
                                transition: 'border-color .12s', boxSizing: 'border-box',
                            }}
                            onFocus={(e) => { e.currentTarget.style.borderColor = 'var(--brand-navy)'; e.currentTarget.style.boxShadow = '0 0 0 3px oklch(0.92 0.04 250)'; }}
                            onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--line-2)'; e.currentTarget.style.boxShadow = 'none'; }}
                        />
                        {searchInput && (
                            <button
                                onClick={() => { setSearchInput(''); handleSearch(''); }}
                                style={{ position: 'absolute', right: 72, top: 12, background: 'transparent', border: 0, cursor: 'pointer', color: 'var(--ink-3)', display: 'flex', alignItems: 'center' }}
                            >
                                <XIcon />
                            </button>
                        )}
                        <button
                            onClick={() => handleSearch(searchInput)}
                            style={{
                                position: 'absolute', right: 4, top: 4, height: 32, padding: '0 16px',
                                background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 5,
                                fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--brand-red)'; }}
                        >
                            Найти
                        </button>
                    </div>

                    {/* Active filters */}
                    {(categoryId || searchQuery) && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginBottom: 16, fontSize: 13 }}>
                            <span style={{ color: 'var(--ink-3)' }}>Фильтры:</span>
                            {currentCategory && (
                                <span style={{
                                    display: 'inline-flex', alignItems: 'center', gap: 5,
                                    padding: '5px 8px 5px 10px', borderRadius: 6,
                                    background: 'var(--navy-tint)', color: 'var(--brand-navy)',
                                    fontSize: 12.5, fontWeight: 500,
                                }}>
                                    {currentCategory.name}
                                    <button onClick={handleClearFilters} style={{ background: 'transparent', border: 0, cursor: 'pointer', color: 'var(--brand-navy)', display: 'flex', alignItems: 'center', padding: 0, opacity: .6 }}>
                                        <XIcon />
                                    </button>
                                </span>
                            )}
                            {searchQuery && (
                                <span style={{
                                    display: 'inline-flex', alignItems: 'center', gap: 5,
                                    padding: '5px 8px 5px 10px', borderRadius: 6,
                                    background: 'var(--navy-tint)', color: 'var(--brand-navy)',
                                    fontSize: 12.5, fontWeight: 500,
                                }}>
                                    «{searchQuery}»
                                    <button onClick={handleClearFilters} style={{ background: 'transparent', border: 0, cursor: 'pointer', color: 'var(--brand-navy)', display: 'flex', alignItems: 'center', padding: 0, opacity: .6 }}>
                                        <XIcon />
                                    </button>
                                </span>
                            )}
                            <span
                                onClick={handleClearFilters}
                                style={{ color: 'var(--brand-red)', fontWeight: 500, marginLeft: 6, cursor: 'pointer', fontSize: 13 }}
                            >
                                Сбросить все
                            </span>
                            {productsPage && (
                                <span style={{ marginLeft: 'auto', color: 'var(--ink-3)' }}>
                                    Найдено <strong style={{ color: 'var(--ink-1)' }}>{productsPage.totalElements.toLocaleString('ru-RU')}</strong> товаров
                                </span>
                            )}
                        </div>
                    )}

                    {/* Loading skeletons */}
                    {productsLoading && (
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14 }}>
                            {Array.from({ length: 8 }).map((_, i) => <SkeletonCard key={i} />)}
                        </div>
                    )}

                    {/* Error */}
                    {isError && (
                        <div style={{ textAlign: 'center', padding: '60px 0' }}>
                            <div style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 16 }}>
                                Не удалось загрузить товары. Проверьте подключение к серверу.
                            </div>
                            <button
                                onClick={() => window.location.reload()}
                                style={{ height: 40, padding: '0 20px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                            >
                                Попробовать снова
                            </button>
                        </div>
                    )}

                    {/* Empty state */}
                    {productsPage?.empty && !productsLoading && (
                        <div style={{ textAlign: 'center', padding: '60px 0' }}>
                            <div style={{ fontFamily: 'var(--font-head)', fontSize: 18, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>
                                {searchQuery ? `По запросу «${searchQuery}» ничего не найдено` : 'В этой категории пока нет товаров'}
                            </div>
                            <div style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 20 }}>
                                {searchQuery ? 'Попробуйте изменить запрос или выбрать другую категорию' : 'Скоро здесь появятся новинки — загляните в другие разделы'}
                            </div>
                            <button
                                onClick={handleClearFilters}
                                style={{ height: 40, padding: '0 20px', border: '1px solid var(--brand-navy)', color: 'var(--brand-navy)', background: 'transparent', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                            >
                                Все товары
                            </button>
                        </div>
                    )}

                    {/* Products grid */}
                    {productsPage && !productsPage.empty && !productsLoading && (
                        <>
                            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14 }}>
                                {productsPage.content.map((product) => (
                                    <ProductCard
                                        key={product.id}
                                        product={product}
                                        onAddToCart={handleAddToCart}
                                    />
                                ))}
                            </div>

                            {/* Pagination */}
                            {totalPages > 1 && (
                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 24, fontSize: 13 }}>
                                    <span style={{ color: 'var(--ink-3)' }}>
                                        Показано {productsPage.content.length} из {productsPage.totalElements.toLocaleString('ru-RU')}
                                    </span>
                                    <div style={{ display: 'flex', gap: 4 }}>
                                        {buildPages().map((n, i) => (
                                            <button
                                                key={i}
                                                disabled={n === '…'}
                                                onClick={() => typeof n === 'number' && handlePageChange(n)}
                                                style={{
                                                    width: 32, height: 32, borderRadius: 6,
                                                    border: n === currentPage ? '1px solid var(--brand-red)' : '1px solid var(--line-1)',
                                                    background: n === currentPage ? 'var(--brand-red)' : '#fff',
                                                    color: n === currentPage ? '#fff' : n === '…' ? 'var(--ink-3)' : 'var(--ink-1)',
                                                    fontFamily: 'var(--font-body)', fontSize: 13, fontWeight: 500,
                                                    cursor: n === '…' ? 'default' : 'pointer',
                                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                }}
                                            >
                                                {n}
                                            </button>
                                        ))}
                                        {currentPage < totalPages && (
                                            <button
                                                onClick={() => handlePageChange(currentPage + 1)}
                                                style={{
                                                    display: 'inline-flex', alignItems: 'center', gap: 4,
                                                    height: 32, padding: '0 12px', marginLeft: 4,
                                                    background: 'var(--surface-2)', border: '1px solid var(--line-1)',
                                                    borderRadius: 6, fontSize: 13, color: 'var(--ink-1)', cursor: 'pointer', fontFamily: 'var(--font-body)',
                                                }}
                                            >
                                                Далее <ChevRight />
                                            </button>
                                        )}
                                    </div>
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
