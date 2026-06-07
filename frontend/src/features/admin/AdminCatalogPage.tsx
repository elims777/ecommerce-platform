import { useState, useMemo, useRef, useCallback } from 'react';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getAdminProducts, searchProducts } from '@/api/products';
import {
    getCategoryTree,
    createCategory,
    updateCategory,
    deleteCategory,
    getCategoryById,
    setCategoryParent,
} from '@/api/adminCategories';
import { batchUpdateActive, changeProductCategory, bulkDeleteProducts } from '@/api/adminProducts';
import apiClient from '@/api/client';
import type { CategoryRequest } from '@/api/adminCategories';
import type { Product, CategoryTree } from '@/types/product';

const ALPHABET = 'АБВГДЕЖЗИКЛМНОПРСТУФХЦЧШЩЭЮЯ'.split('');

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(price);

const getPrimaryImageUrl = (product: Product): string | null => {
    if (!product.images || product.images.length === 0) return null;
    const primary = product.images.find((img) => img.isPrimary);
    return primary?.fileUrl ?? product.images[0].fileUrl;
};

const findCatName = (tree: CategoryTree[], id: number): string => {
    for (const cat of tree) {
        if (cat.id === id) return cat.name;
        const found = findCatName(cat.children, id);
        if (found) return found;
    }
    return '';
};

const flattenForSelect = (
    categories: CategoryTree[],
    excludeId?: number,
    prefix = '',
): { value: number; label: string }[] => {
    const result: { value: number; label: string }[] = [];
    for (const cat of categories) {
        if (cat.id !== excludeId) {
            result.push({ value: cat.id, label: prefix + cat.name });
            if (cat.children.length > 0) {
                result.push(...flattenForSelect(cat.children, excludeId, prefix + '— '));
            }
        }
    }
    return result;
};

// ── Category tree node (recursive) ──────────────────────────────────────────

interface CategoryTreeNodeProps {
    categories: CategoryTree[];
    selectedId: number | undefined;
    onSelect: (id: number) => void;
    depth?: number;
}

const CategoryTreeNode = ({ categories, selectedId, onSelect, depth = 0 }: CategoryTreeNodeProps) => {
    const sorted = [...categories].sort((a, b) => a.displayOrder - b.displayOrder);
    return (
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, paddingLeft: depth > 0 ? 14 : 0 }}>
            {sorted.map((cat) => (
                <li key={cat.id}>
                    <div
                        onClick={() => onSelect(cat.id)}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: 6,
                            padding: '6px 10px',
                            borderRadius: 'var(--r-3)',
                            cursor: 'pointer',
                            fontSize: 'var(--text-base)',
                            fontWeight: selectedId === cat.id ? 600 : 400,
                            background: selectedId === cat.id ? 'var(--red-tint)' : 'transparent',
                            color: selectedId === cat.id ? 'var(--brand-red)' : 'var(--ink-1)',
                            transition: 'background 0.1s, color 0.1s',
                            userSelect: 'none',
                        }}
                    >
                        <svg width="13" height="13" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, opacity: 0.55 }}>
                            <path d="M2 4h5l2 2h5v8H2z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" fill="none" />
                        </svg>
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                            {cat.name}
                        </span>
                        {!cat.isActive && (
                            <span style={{
                                fontSize: 10,
                                padding: '1px 5px',
                                borderRadius: 'var(--r-2)',
                                background: 'var(--surface-3)',
                                color: 'var(--ink-3)',
                                flexShrink: 0,
                            }}>
                                скрыта
                            </span>
                        )}
                    </div>
                    {cat.children.length > 0 && (
                        <CategoryTreeNode
                            categories={cat.children}
                            selectedId={selectedId}
                            onSelect={onSelect}
                            depth={depth + 1}
                        />
                    )}
                </li>
            ))}
        </ul>
    );
};

// ── Inline category form state ───────────────────────────────────────────────

interface CatFormState {
    name: string;
    description: string;
    parentId: number | '';
}

const EMPTY_CAT_FORM: CatFormState = { name: '', description: '', parentId: '' };

// ── Main component ────────────────────────────────────────────────────────────

const AdminCatalogPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    // State
    const [selectedCategoryId, setSelectedCategoryId] = useState<number | undefined>();
    const [searchQuery, setSearchQuery] = useState('');
    const [activeLetter, setActiveLetter] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [showAllProducts, setShowAllProducts] = useState(true);

    // Category modal
    const catDialogRef = useRef<HTMLDialogElement>(null);
    const [editingCatId, setEditingCatId] = useState<number | null>(null);
    const [catForm, setCatForm] = useState<CatFormState>(EMPTY_CAT_FORM);
    const [catFormErrors, setCatFormErrors] = useState<{ name?: string }>({});

    // Single product move popover
    const [moveProductId, setMoveProductId] = useState<number | null>(null);
    const [moveProductTarget, setMoveProductTarget] = useState<number | ''>('');

    // Bulk selection & batch move
    const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
    const batchMoveDialogRef = useRef<HTMLDialogElement>(null);
    const [batchTargetCategory, setBatchTargetCategory] = useState<number | ''>('');

    const [pageSize, setPageSize] = useState(20);

    // ── Queries ──────────────────────────────────────────────────────────────

    const { data: categoryTree = [], isLoading: treeLoading } = useQuery({
        queryKey: ['adminCategoryTree'],
        queryFn: getCategoryTree,
    });

    const {
        data: productsPage,
        isLoading: productsLoading,
        refetch: refetchProducts,
    } = useQuery({
        queryKey: ['adminCatalogProducts', { page: currentPage, category: selectedCategoryId, size: pageSize }],
        queryFn: () =>
            getAdminProducts({
                page: currentPage - 1,
                size: pageSize,
                categoryId: selectedCategoryId,
                sort: 'name,asc',
            }),
    });

    const { data: searchResults } = useQuery({
        queryKey: ['adminProductSearch', searchQuery],
        queryFn: () => searchProducts(searchQuery),
        enabled: searchQuery.length >= 2,
    });

    const displayProducts = useMemo(() => {
        let items: Product[] = [];
        if (searchQuery.length >= 2 && searchResults) {
            items = searchResults;
        } else if (productsPage) {
            items = productsPage.content;
        }
        if (activeLetter) {
            items = items.filter((p) => p.name.toUpperCase().startsWith(activeLetter));
        }
        return items;
    }, [searchQuery, searchResults, productsPage, activeLetter]);

    const invalidateAll = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
        queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
        queryClient.invalidateQueries({ queryKey: ['categories'] });
    }, [queryClient]);

    // ── Category mutations ────────────────────────────────────────────────────

    const moveCategoryMutation = useMutation({
        mutationFn: ({ catId, newParentId }: { catId: number; newParentId: number }) =>
            setCategoryParent(catId, newParentId),
        onSuccess: (_, variables) => {
            const catName = findCatName(categoryTree, variables.catId);
            const parentName = findCatName(categoryTree, variables.newParentId);
            messageApi.success(`«${catName}» перемещена в «${parentName}»`);
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при перемещении категории'),
    });

    const createCatMutation = useMutation({
        mutationFn: createCategory,
        onSuccess: () => {
            messageApi.success('Категория создана');
            invalidateAll();
            catDialogRef.current?.close();
            setCatForm(EMPTY_CAT_FORM);
        },
        onError: () => messageApi.error('Ошибка при создании категории'),
    });

    const updateCatMutation = useMutation({
        mutationFn: ({ id, request }: { id: number; request: CategoryRequest }) =>
            updateCategory(id, request),
        onSuccess: () => {
            messageApi.success('Категория обновлена');
            invalidateAll();
            catDialogRef.current?.close();
            setEditingCatId(null);
            setCatForm(EMPTY_CAT_FORM);
        },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const deleteCatMutation = useMutation({
        mutationFn: deleteCategory,
        onSuccess: () => {
            messageApi.success('Категория удалена');
            invalidateAll();
            setSelectedCategoryId(undefined);
            setShowAllProducts(true);
        },
        onError: () => messageApi.error('Ошибка. Возможно есть товары или подкатегории.'),
    });

    // ── Product mutations ─────────────────────────────────────────────────────

    const moveProductMutation = useMutation({
        mutationFn: ({ productId, categoryId }: { productId: number; categoryId: number }) =>
            changeProductCategory(productId, categoryId),
        onSuccess: (_, variables) => {
            const catName = findCatName(categoryTree, variables.categoryId);
            messageApi.success(`Товар перемещён в «${catName}»`);
            setMoveProductId(null);
            setMoveProductTarget('');
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при перемещении товара'),
    });

    const batchMoveMutation = useMutation({
        mutationFn: async ({ productIds, categoryId }: { productIds: number[]; categoryId: number }) => {
            await apiClient.put('/v1/products/batch/category', productIds, {
                params: { categoryId },
            });
        },
        onSuccess: (_, variables) => {
            const catName = findCatName(categoryTree, variables.categoryId);
            messageApi.success(`${variables.productIds.length} товаров перемещено в «${catName}»`);
            setSelectedRowKeys([]);
            batchMoveDialogRef.current?.close();
            setBatchTargetCategory('');
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при массовом перемещении'),
    });

    const batchActivateMutation = useMutation({
        mutationFn: (isActive: boolean) => batchUpdateActive(selectedRowKeys, isActive),
        onSuccess: (_, isActive) => {
            messageApi.success(isActive ? 'Товары активированы' : 'Товары деактивированы');
            setSelectedRowKeys([]);
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const batchDeleteMutation = useMutation({
        mutationFn: () => bulkDeleteProducts(selectedRowKeys),
        onSuccess: () => {
            messageApi.success(`${selectedRowKeys.length} товаров удалено`);
            setSelectedRowKeys([]);
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при удалении'),
    });

    const toggleProductMutation = useMutation({
        mutationFn: async ({ id, isActive }: { id: number; isActive: boolean }) => {
            await apiClient.put(`/v1/products/${id}/${isActive ? 'deactivate' : 'activate'}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
            messageApi.success('Статус товара обновлён');
        },
    });

    const deleteProductMutation = useMutation({
        mutationFn: async (id: number) => {
            await apiClient.delete(`/v1/products/${id}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
            messageApi.success('Товар удалён');
        },
    });

    // ── Handlers ─────────────────────────────────────────────────────────────

    const handleCategorySelect = (id: number) => {
        setSelectedCategoryId(id);
        setShowAllProducts(false);
        setCurrentPage(1);
        setSearchQuery('');
        setActiveLetter(null);
        setSelectedRowKeys([]);
    };

    const handleShowAll = () => {
        setSelectedCategoryId(undefined);
        setShowAllProducts(true);
        setCurrentPage(1);
        setSearchQuery('');
        setActiveLetter(null);
        setSelectedRowKeys([]);
    };

    const openCatModal = (initialForm: CatFormState, editId: number | null = null) => {
        setEditingCatId(editId);
        setCatForm(initialForm);
        setCatFormErrors({});
        catDialogRef.current?.showModal();
    };

    const handleAddCategory = () => {
        openCatModal(EMPTY_CAT_FORM, null);
    };

    const handleAddSubcategory = (parentId: number) => {
        openCatModal({ ...EMPTY_CAT_FORM, parentId }, null);
    };

    const handleEditCategory = async (catId: number) => {
        try {
            const cat = await getCategoryById(catId);
            openCatModal(
                {
                    name: cat.name,
                    description: cat.description || '',
                    parentId: cat.parentId ?? '',
                },
                catId,
            );
        } catch {
            messageApi.error('Ошибка загрузки категории');
        }
    };

    const handleCatSubmit = () => {
        if (!catForm.name || catForm.name.length < 2) {
            setCatFormErrors({ name: 'Минимум 2 символа' });
            return;
        }
        setCatFormErrors({});
        const request: CategoryRequest = {
            name: catForm.name,
            description: catForm.description || undefined,
            parentId: catForm.parentId !== '' ? catForm.parentId : undefined,
        };
        if (editingCatId) {
            updateCatMutation.mutate({ id: editingCatId, request });
        } else {
            createCatMutation.mutate(request);
        }
    };

    const handleBatchMove = () => {
        if (!batchTargetCategory || selectedRowKeys.length === 0) return;
        batchMoveMutation.mutate({
            productIds: selectedRowKeys,
            categoryId: batchTargetCategory as number,
        });
    };

    const openBatchMoveDialog = () => {
        setBatchTargetCategory('');
        batchMoveDialogRef.current?.showModal();
    };

    const handleBatchDelete = () => {
        if (window.confirm(`Удалить ${selectedRowKeys.length} товаров? Это действие нельзя отменить.`)) {
            batchDeleteMutation.mutate();
        }
    };

    // ── Derived data ──────────────────────────────────────────────────────────

    const parentOptions = flattenForSelect(categoryTree, editingCatId || undefined);
    const flatCategoryOptions = flattenForSelect(categoryTree);

    const totalPages = productsPage ? Math.ceil(productsPage.totalElements / pageSize) : 0;
    const isPaginated = searchQuery.length < 2 && !activeLetter;

    const pageTitle = selectedCategoryId
        ? findCatName(categoryTree, selectedCategoryId) || 'Товары'
        : activeLetter
            ? `Товары на «${activeLetter}»`
            : searchQuery
                ? `Поиск: «${searchQuery}»`
                : 'Все товары';

    const allPageSelected =
        displayProducts.length > 0 &&
        displayProducts.every((p) => selectedRowKeys.includes(p.id));

    const toggleSelectAll = () => {
        if (allPageSelected) {
            setSelectedRowKeys((prev) =>
                prev.filter((k) => !displayProducts.some((p) => p.id === k)),
            );
        } else {
            const ids = displayProducts.map((p) => p.id);
            setSelectedRowKeys((prev) => Array.from(new Set([...prev, ...ids])));
        }
    };

    const toggleRow = (id: number) => {
        setSelectedRowKeys((prev) =>
            prev.includes(id) ? prev.filter((k) => k !== id) : [...prev, id],
        );
    };

    // ── Pagination helpers ────────────────────────────────────────────────────

    const renderPageButtons = () => {
        if (totalPages <= 1) return null;
        const pages: (number | '…')[] = [];
        if (totalPages <= 7) {
            for (let i = 1; i <= totalPages; i++) pages.push(i);
        } else {
            pages.push(1);
            if (currentPage > 3) pages.push('…');
            for (let i = Math.max(2, currentPage - 1); i <= Math.min(totalPages - 1, currentPage + 1); i++) {
                pages.push(i);
            }
            if (currentPage < totalPages - 2) pages.push('…');
            pages.push(totalPages);
        }
        return pages.map((p, i) =>
            p === '…' ? (
                <span key={`ellipsis-${i}`} style={{ padding: '0 4px', color: 'var(--ink-3)', lineHeight: '30px' }}>…</span>
            ) : (
                <button
                    key={p}
                    className={`rf-admin-page-btn${p === currentPage ? ' active' : ''}`}
                    onClick={() => { setCurrentPage(p as number); setSelectedRowKeys([]); }}
                >
                    {p}
                </button>
            ),
        );
    };

    // Suppress unused variable warning for moveCategoryMutation (used via handleTreeDrop which is removed,
    // but the mutation itself is retained for potential future use and was part of original business logic)
    void moveCategoryMutation;

    // ── Render ────────────────────────────────────────────────────────────────

    return (
        <div>
            {/* Page header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
                <h2 style={{ margin: 0, fontFamily: 'var(--font-head)', fontSize: 'var(--text-3xl)', fontWeight: 600, color: 'var(--ink-1)', letterSpacing: '-0.01em' }}>
                    Каталог
                </h2>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-quiet"
                        onClick={() => { refetchProducts(); invalidateAll(); }}
                    >
                        <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                            <path d="M13.6 2.4A7 7 0 1 0 15 8" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
                            <path d="M11 2h3v3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                        Обновить
                    </button>
                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={handleAddCategory}>
                        <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                            <path d="M8 3v10M3 8h10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                        </svg>
                        Категория
                    </button>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-primary"
                        onClick={() => navigate('/admin/products/new')}
                    >
                        <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                            <path d="M8 3v10M3 8h10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                        </svg>
                        Товар
                    </button>
                </div>
            </div>

            {/* Search bar */}
            <div className="rf-card" style={{ marginBottom: 10 }}>
                <div style={{ padding: '10px 14px' }}>
                    <div className="rf-admin-search" style={{ width: '100%' }}>
                        <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                            <circle cx="6.5" cy="6.5" r="4.5" stroke="currentColor" strokeWidth="1.5" />
                            <path d="M10 10l3.5 3.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                        </svg>
                        <input
                            type="text"
                            placeholder="Быстрый поиск по названию или артикулу..."
                            value={searchQuery}
                            onChange={(e) => {
                                setSearchQuery(e.target.value);
                                if (e.target.value) {
                                    setActiveLetter(null);
                                    setSelectedCategoryId(undefined);
                                    setShowAllProducts(true);
                                    setSelectedRowKeys([]);
                                }
                            }}
                        />
                    </div>
                </div>
            </div>

            {/* Alphabet bar */}
            <div className="rf-card" style={{ marginBottom: 14 }}>
                <div className="rf-alpha-bar">
                    <button
                        className={`rf-alpha-btn${!activeLetter ? ' active' : ''}`}
                        onClick={() => setActiveLetter(null)}
                    >
                        Все
                    </button>
                    {ALPHABET.map((letter) => (
                        <button
                            key={letter}
                            className={`rf-alpha-btn${activeLetter === letter ? ' active' : ''}`}
                            onClick={() => {
                                setActiveLetter(activeLetter === letter ? null : letter);
                                setSelectedRowKeys([]);
                            }}
                        >
                            {letter}
                        </button>
                    ))}
                </div>
            </div>

            {/* Two-column layout */}
            <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>

                {/* Left: category panel */}
                <div style={{ width: 280, flexShrink: 0 }}>
                    <div className="rf-card">
                        {/* Header */}
                        <div className="rf-card-header" style={{ justifyContent: 'space-between' }}>
                            <h3 style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                <svg width="14" height="14" viewBox="0 0 16 16" fill="none">
                                    <rect x="1" y="1" width="6" height="6" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
                                    <rect x="9" y="1" width="6" height="6" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
                                    <rect x="1" y="9" width="6" height="6" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
                                    <rect x="9" y="9" width="6" height="6" rx="1.2" stroke="currentColor" strokeWidth="1.4" />
                                </svg>
                                Категории
                            </h3>
                            <div style={{ display: 'flex', gap: 2 }}>
                                {/* Добавить корневую категорию */}
                                <button
                                    className="rf-btn rf-btn-ghost"
                                    style={{ height: 28, width: 28, padding: 0, color: 'var(--ink-2)' }}
                                    onClick={handleAddCategory}
                                    title="Новая категория"
                                >
                                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                                        <path d="M8 3v10M3 8h10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                                    </svg>
                                </button>
                                {/* Добавить подкатегорию */}
                                <button
                                    className="rf-btn rf-btn-ghost"
                                    style={{ height: 28, width: 28, padding: 0, color: 'var(--ink-2)', opacity: selectedCategoryId ? 1 : 0.35 }}
                                    disabled={!selectedCategoryId}
                                    onClick={() => selectedCategoryId && handleAddSubcategory(selectedCategoryId)}
                                    title="Добавить подкатегорию"
                                >
                                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                                        <path d="M2 4h5l2 2h5v8H2z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" fill="none" />
                                        <path d="M8 8v4M6 10h4" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
                                    </svg>
                                </button>
                                {/* Редактировать */}
                                <button
                                    className="rf-btn rf-btn-ghost"
                                    style={{ height: 28, width: 28, padding: 0, color: 'var(--ink-2)', opacity: selectedCategoryId ? 1 : 0.35 }}
                                    disabled={!selectedCategoryId}
                                    onClick={() => selectedCategoryId && handleEditCategory(selectedCategoryId)}
                                    title="Редактировать категорию"
                                >
                                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                                        <path d="M11 2l3 3-8 8H3v-3L11 2z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" fill="none" />
                                    </svg>
                                </button>
                                {/* Удалить */}
                                <button
                                    className="rf-btn rf-btn-ghost"
                                    style={{ height: 28, width: 28, padding: 0, color: selectedCategoryId ? 'var(--brand-red)' : 'var(--ink-3)', opacity: selectedCategoryId ? 1 : 0.35 }}
                                    disabled={!selectedCategoryId}
                                    onClick={() => {
                                        if (selectedCategoryId && window.confirm('Удалить категорию? Это действие нельзя отменить.')) {
                                            deleteCatMutation.mutate(selectedCategoryId);
                                        }
                                    }}
                                    title="Удалить категорию"
                                >
                                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                                        <path d="M2 4h12M6 4V2h4v2M5 4l.5 9h5l.5-9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                                    </svg>
                                </button>
                            </div>
                        </div>

                        {/* Body */}
                        <div className="rf-card-body" style={{ padding: '12px 14px' }}>
                            {/* All products button */}
                            <button
                                className={`rf-btn rf-btn-sm${showAllProducts ? ' rf-btn-primary' : ' rf-btn-quiet'}`}
                                style={{ width: '100%', marginBottom: 10 }}
                                onClick={handleShowAll}
                            >
                                Все товары ({productsPage?.totalElements ?? 0})
                            </button>

                            {treeLoading ? (
                                <div style={{ padding: '16px 0', textAlign: 'center', color: 'var(--ink-3)', fontSize: 'var(--text-base)' }}>
                                    Загрузка…
                                </div>
                            ) : (
                                <CategoryTreeNode
                                    categories={categoryTree}
                                    selectedId={selectedCategoryId}
                                    onSelect={handleCategorySelect}
                                />
                            )}
                        </div>
                    </div>
                </div>

                {/* Right: products panel */}
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="rf-card">
                        <div className="rf-card-header" style={{ justifyContent: 'space-between' }}>
                            <h3>{pageTitle}</h3>
                        </div>

                        {/* Table */}
                        <div className="rf-admin-table-wrap">
                            {productsLoading ? (
                                <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 'var(--text-base)' }}>
                                    Загрузка…
                                </div>
                            ) : displayProducts.length === 0 ? (
                                <div style={{ padding: 40, textAlign: 'center', color: 'var(--ink-3)', fontSize: 'var(--text-base)' }}>
                                    Товары не найдены
                                </div>
                            ) : (
                                <table className="rf-admin-table">
                                    <thead>
                                        <tr>
                                            <th style={{ width: 36, paddingLeft: 16 }}>
                                                <input
                                                    type="checkbox"
                                                    checked={allPageSelected}
                                                    onChange={toggleSelectAll}
                                                    style={{ cursor: 'pointer' }}
                                                />
                                            </th>
                                            <th style={{ width: 44 }}></th>
                                            <th>Название</th>
                                            <th style={{ width: 160 }}>Категория</th>
                                            <th style={{ width: 100 }}>Артикул</th>
                                            <th style={{ width: 110 }}>Цена</th>
                                            <th style={{ width: 55 }}>Ост.</th>
                                            <th style={{ width: 55 }}>Акт.</th>
                                            <th style={{ width: 40 }}></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {displayProducts.map((product) => {
                                            const imageUrl = getPrimaryImageUrl(product);
                                            const isMovingThis = moveProductId === product.id;
                                            return (
                                                <tr key={product.id}>
                                                    {/* Checkbox */}
                                                    <td style={{ paddingLeft: 16 }}>
                                                        <input
                                                            type="checkbox"
                                                            checked={selectedRowKeys.includes(product.id)}
                                                            onChange={() => toggleRow(product.id)}
                                                            style={{ cursor: 'pointer' }}
                                                        />
                                                    </td>

                                                    {/* Image */}
                                                    <td>
                                                        {imageUrl ? (
                                                            <img
                                                                src={imageUrl}
                                                                width={36}
                                                                height={36}
                                                                style={{ objectFit: 'contain', borderRadius: 'var(--r-2)', display: 'block' }}
                                                                alt=""
                                                            />
                                                        ) : (
                                                            <div style={{
                                                                width: 36, height: 36, borderRadius: 'var(--r-2)',
                                                                background: 'var(--surface-2)',
                                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                            }}>
                                                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                                                                    <path d="M20 7H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2z" stroke="var(--ink-3)" strokeWidth="1.5" />
                                                                    <path d="M16 3H8" stroke="var(--ink-3)" strokeWidth="1.5" strokeLinecap="round" />
                                                                    <circle cx="12" cy="13" r="2.5" stroke="var(--ink-3)" strokeWidth="1.5" />
                                                                </svg>
                                                            </div>
                                                        )}
                                                    </td>

                                                    {/* Name */}
                                                    <td>
                                                        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                                                            <a
                                                                onClick={() => navigate(`/admin/products/${product.id}/edit`)}
                                                                style={{ color: 'var(--brand-navy)', cursor: 'pointer', textDecoration: 'none', fontSize: 'var(--text-base)' }}
                                                            >
                                                                {product.name}
                                                            </a>
                                                            {!product.isActive && (
                                                                <span className="rf-badge rf-badge-neutral" style={{ fontSize: 10, height: 18 }}>
                                                                    неактивен
                                                                </span>
                                                            )}
                                                        </div>
                                                    </td>

                                                    {/* Category — inline move popover */}
                                                    <td style={{ position: 'relative' }}>
                                                        <button
                                                            className="rf-btn rf-btn-ghost"
                                                            style={{ height: 24, padding: '0 6px', fontSize: 'var(--text-sm)', gap: 4, color: 'var(--ink-2)' }}
                                                            onClick={() => {
                                                                setMoveProductId(isMovingThis ? null : product.id);
                                                                setMoveProductTarget('');
                                                            }}
                                                        >
                                                            <svg width="11" height="11" viewBox="0 0 16 16" fill="none">
                                                                <path d="M2 4h5l2 2h5v8H2z" stroke="currentColor" strokeWidth="1.4" strokeLinejoin="round" fill="none" />
                                                            </svg>
                                                            {product.categoryName || 'Без категории'}
                                                        </button>
                                                        {isMovingThis && (
                                                            <div style={{
                                                                position: 'absolute',
                                                                top: '100%',
                                                                left: 0,
                                                                zIndex: 50,
                                                                background: 'var(--surface)',
                                                                border: '1px solid var(--line-1)',
                                                                borderRadius: 'var(--r-3)',
                                                                padding: 12,
                                                                width: 240,
                                                                boxShadow: 'var(--shadow-pop)',
                                                            }}>
                                                                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink-2)', marginBottom: 8 }}>
                                                                    Переместить в категорию:
                                                                </div>
                                                                <select
                                                                    style={{
                                                                        width: '100%', height: 32, fontSize: 'var(--text-base)',
                                                                        border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)',
                                                                        background: 'var(--surface)', color: 'var(--ink-1)',
                                                                        padding: '0 8px', marginBottom: 10, outline: 'none',
                                                                        fontFamily: 'var(--font-body)',
                                                                    }}
                                                                    value={moveProductTarget}
                                                                    onChange={(e) => setMoveProductTarget(e.target.value ? Number(e.target.value) : '')}
                                                                >
                                                                    <option value="">Выберите категорию</option>
                                                                    {flatCategoryOptions.map((opt) => (
                                                                        <option key={opt.value} value={opt.value}>{opt.label}</option>
                                                                    ))}
                                                                </select>
                                                                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                                                                    <button
                                                                        className="rf-btn rf-btn-sm rf-btn-quiet"
                                                                        onClick={() => { setMoveProductId(null); setMoveProductTarget(''); }}
                                                                    >
                                                                        Отмена
                                                                    </button>
                                                                    <button
                                                                        className="rf-btn rf-btn-sm rf-btn-primary"
                                                                        disabled={!moveProductTarget || moveProductMutation.isPending}
                                                                        onClick={() => {
                                                                            if (moveProductTarget) {
                                                                                moveProductMutation.mutate({
                                                                                    productId: product.id,
                                                                                    categoryId: moveProductTarget as number,
                                                                                });
                                                                            }
                                                                        }}
                                                                    >
                                                                        Переместить
                                                                    </button>
                                                                </div>
                                                            </div>
                                                        )}
                                                    </td>

                                                    {/* SKU */}
                                                    <td className="rf-mono" style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                                                        {product.sku || '—'}
                                                    </td>

                                                    {/* Price */}
                                                    <td className="rf-tabular" style={{ fontWeight: 500 }}>
                                                        {formatPrice(product.price)}
                                                    </td>

                                                    {/* Stock */}
                                                    <td className="rf-tabular" style={{
                                                        color: product.stockQuantity > 0 ? 'var(--brand-green)' : 'var(--brand-red)',
                                                        fontWeight: 500,
                                                    }}>
                                                        {product.stockQuantity}
                                                    </td>

                                                    {/* Active toggle */}
                                                    <td>
                                                        <input
                                                            type="checkbox"
                                                            checked={product.isActive}
                                                            onChange={() => toggleProductMutation.mutate({ id: product.id, isActive: product.isActive })}
                                                            style={{ cursor: 'pointer', width: 16, height: 16 }}
                                                            title={product.isActive ? 'Деактивировать' : 'Активировать'}
                                                        />
                                                    </td>

                                                    {/* Delete */}
                                                    <td>
                                                        <button
                                                            className="rf-btn rf-btn-ghost"
                                                            style={{ height: 28, width: 28, padding: 0, color: 'var(--brand-red)' }}
                                                            title="Удалить товар"
                                                            onClick={() => {
                                                                if (window.confirm('Удалить товар?')) {
                                                                    deleteProductMutation.mutate(product.id);
                                                                }
                                                            }}
                                                        >
                                                            <svg width="13" height="13" viewBox="0 0 16 16" fill="none">
                                                                <path d="M2 4h12M6 4V2h4v2M5 4l.5 9h5l.5-9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                                                            </svg>
                                                        </button>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            )}
                        </div>

                        {/* Pagination */}
                        {isPaginated && totalPages > 1 && (
                            <div className="rf-admin-pagination">
                                <span>
                                    Всего {productsPage?.totalElements ?? 0}
                                    {' · '}
                                    <select
                                        value={pageSize}
                                        onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(1); }}
                                        style={{
                                            height: 26, fontSize: 'var(--text-sm)', border: '1px solid var(--line-2)',
                                            borderRadius: 'var(--r-2)', background: 'var(--surface)', color: 'var(--ink-1)',
                                            padding: '0 6px', fontFamily: 'var(--font-body)', cursor: 'pointer',
                                        }}
                                    >
                                        {[10, 20, 50, 100].map((n) => (
                                            <option key={n} value={n}>{n} / стр.</option>
                                        ))}
                                    </select>
                                </span>
                                <div className="rf-admin-pagination-pages">
                                    <button
                                        className="rf-admin-page-btn"
                                        disabled={currentPage === 1}
                                        onClick={() => { setCurrentPage((p) => p - 1); setSelectedRowKeys([]); }}
                                    >
                                        ‹
                                    </button>
                                    {renderPageButtons()}
                                    <button
                                        className="rf-admin-page-btn"
                                        disabled={currentPage === totalPages}
                                        onClick={() => { setCurrentPage((p) => p + 1); setSelectedRowKeys([]); }}
                                    >
                                        ›
                                    </button>
                                </div>
                            </div>
                        )}
                    </div>

                </div>
            </div>

            {/* Floating bulk bar */}
            {selectedRowKeys.length > 0 && (
                <div className="rf-bulk-bar">
                    <span className="rf-bulk-bar-count">Выбрано: {selectedRowKeys.length}</span>
                    <div className="rf-bulk-bar-divider" />
                    <button className="rf-bulk-btn" onClick={() => batchActivateMutation.mutate(true)}>
                        Активировать
                    </button>
                    <button className="rf-bulk-btn" onClick={() => batchActivateMutation.mutate(false)}>
                        Деактивировать
                    </button>
                    <button
                        className="rf-bulk-btn"
                        onClick={openBatchMoveDialog}
                    >
                        Переместить
                    </button>
                    <div className="rf-bulk-bar-divider" />
                    <button className="rf-bulk-btn danger" onClick={handleBatchDelete}>
                        Удалить
                    </button>
                    <div className="rf-bulk-bar-divider" />
                    <button
                        className="rf-bulk-btn"
                        style={{ opacity: 0.7 }}
                        onClick={() => setSelectedRowKeys([])}
                    >
                        ✕ Снять
                    </button>
                </div>
            )}

            {/* Batch move dialog */}
            <dialog
                ref={batchMoveDialogRef}
                style={{
                    border: '1px solid var(--line-1)',
                    borderRadius: 'var(--r-4)',
                    background: 'var(--surface)',
                    color: 'var(--ink-1)',
                    padding: 0,
                    width: 400,
                    maxWidth: '90vw',
                    boxShadow: 'var(--shadow-pop)',
                }}
                onKeyDown={(e) => {
                    if (e.key === 'Escape') {
                        batchMoveDialogRef.current?.close();
                        setBatchTargetCategory('');
                    }
                }}
            >
                <div style={{ padding: '16px 22px', borderBottom: '1px solid var(--line-1)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-lg)' }}>
                        Переместить {selectedRowKeys.length} товаров
                    </span>
                    <button
                        className="rf-btn rf-btn-ghost"
                        style={{ height: 28, width: 28, padding: 0, fontSize: 'var(--text-xl)', color: 'var(--ink-3)' }}
                        onClick={() => { batchMoveDialogRef.current?.close(); setBatchTargetCategory(''); }}
                    >✕</button>
                </div>
                <div style={{ padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                    <div>
                        <label style={{ display: 'block', fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink-2)', marginBottom: 5 }}>
                            Целевая категория <span style={{ color: 'var(--brand-red)' }}>*</span>
                        </label>
                        <select
                            style={{
                                width: '100%', height: 34, fontSize: 'var(--text-base)',
                                border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)',
                                background: 'var(--surface)', color: 'var(--ink-1)',
                                padding: '0 8px', outline: 'none', fontFamily: 'var(--font-body)', boxSizing: 'border-box',
                            }}
                            value={batchTargetCategory}
                            onChange={(e) => setBatchTargetCategory(e.target.value ? Number(e.target.value) : '')}
                        >
                            <option value="">Выберите категорию</option>
                            {flatCategoryOptions.map((opt) => (
                                <option key={opt.value} value={opt.value}>{opt.label}</option>
                            ))}
                        </select>
                    </div>
                </div>
                <div style={{ padding: '14px 22px', borderTop: '1px solid var(--line-1)', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-quiet"
                        onClick={() => { batchMoveDialogRef.current?.close(); setBatchTargetCategory(''); }}
                    >Отмена</button>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-primary"
                        disabled={!batchTargetCategory || batchMoveMutation.isPending}
                        onClick={handleBatchMove}
                    >
                        {batchMoveMutation.isPending ? 'Перемещение…' : 'Переместить'}
                    </button>
                </div>
            </dialog>

            {/* Category create/edit dialog */}
            <dialog
                ref={catDialogRef}
                style={{
                    border: '1px solid var(--line-1)',
                    borderRadius: 'var(--r-4)',
                    background: 'var(--surface)',
                    color: 'var(--ink-1)',
                    padding: 0,
                    width: 420,
                    maxWidth: '90vw',
                    boxShadow: 'var(--shadow-pop)',
                }}
                onKeyDown={(e) => {
                    if (e.key === 'Escape') {
                        catDialogRef.current?.close();
                        setEditingCatId(null);
                        setCatForm(EMPTY_CAT_FORM);
                    }
                }}
            >
                {/* Dialog header */}
                <div style={{
                    padding: '16px 22px',
                    borderBottom: '1px solid var(--line-1)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                }}>
                    <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-lg)' }}>
                        {editingCatId ? 'Редактировать категорию' : 'Новая категория'}
                    </span>
                    <button
                        className="rf-btn rf-btn-ghost"
                        style={{ height: 28, width: 28, padding: 0, fontSize: 'var(--text-xl)', color: 'var(--ink-3)' }}
                        onClick={() => {
                            catDialogRef.current?.close();
                            setEditingCatId(null);
                            setCatForm(EMPTY_CAT_FORM);
                        }}
                    >
                        ✕
                    </button>
                </div>

                {/* Dialog body */}
                <div style={{ padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                    {/* Name */}
                    <div>
                        <label style={{ display: 'block', fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink-2)', marginBottom: 5 }}>
                            Название <span style={{ color: 'var(--brand-red)' }}>*</span>
                        </label>
                        <input
                            type="text"
                            placeholder="Название категории"
                            value={catForm.name}
                            onChange={(e) => setCatForm((f) => ({ ...f, name: e.target.value }))}
                            onKeyDown={(e) => { if (e.key === 'Enter') handleCatSubmit(); }}
                            style={{
                                width: '100%', height: 34, padding: '0 10px', fontSize: 'var(--text-base)',
                                border: `1px solid ${catFormErrors.name ? 'var(--brand-red)' : 'var(--line-2)'}`,
                                borderRadius: 'var(--r-2)', background: 'var(--surface)', color: 'var(--ink-1)',
                                outline: 'none', fontFamily: 'var(--font-body)', boxSizing: 'border-box',
                            }}
                        />
                        {catFormErrors.name && (
                            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--brand-red)', marginTop: 3, display: 'block' }}>
                                {catFormErrors.name}
                            </span>
                        )}
                    </div>

                    {/* Description */}
                    <div>
                        <label style={{ display: 'block', fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink-2)', marginBottom: 5 }}>
                            Описание
                        </label>
                        <textarea
                            placeholder="Описание"
                            value={catForm.description}
                            onChange={(e) => setCatForm((f) => ({ ...f, description: e.target.value }))}
                            rows={3}
                            style={{
                                width: '100%', padding: '8px 10px', fontSize: 'var(--text-base)',
                                border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)',
                                background: 'var(--surface)', color: 'var(--ink-1)',
                                outline: 'none', fontFamily: 'var(--font-body)', resize: 'vertical',
                                boxSizing: 'border-box',
                            }}
                        />
                    </div>

                    {/* Parent category */}
                    <div>
                        <label style={{ display: 'block', fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink-2)', marginBottom: 5 }}>
                            Родительская категория
                        </label>
                        <select
                            value={catForm.parentId}
                            onChange={(e) => setCatForm((f) => ({ ...f, parentId: e.target.value ? Number(e.target.value) : '' }))}
                            style={{
                                width: '100%', height: 34, padding: '0 8px', fontSize: 'var(--text-base)',
                                border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)',
                                background: 'var(--surface)', color: 'var(--ink-1)',
                                outline: 'none', fontFamily: 'var(--font-body)', boxSizing: 'border-box',
                            }}
                        >
                            <option value="">Корневая (без родителя)</option>
                            {parentOptions.map((opt) => (
                                <option key={opt.value} value={opt.value}>{opt.label}</option>
                            ))}
                        </select>
                    </div>
                </div>

                {/* Dialog footer */}
                <div style={{
                    padding: '14px 22px',
                    borderTop: '1px solid var(--line-1)',
                    display: 'flex',
                    justifyContent: 'flex-end',
                    gap: 8,
                }}>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-quiet"
                        onClick={() => {
                            catDialogRef.current?.close();
                            setEditingCatId(null);
                            setCatForm(EMPTY_CAT_FORM);
                        }}
                    >
                        Отмена
                    </button>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-primary"
                        disabled={createCatMutation.isPending || updateCatMutation.isPending}
                        onClick={handleCatSubmit}
                    >
                        {createCatMutation.isPending || updateCatMutation.isPending
                            ? 'Сохранение…'
                            : editingCatId
                                ? 'Сохранить'
                                : 'Создать'}
                    </button>
                </div>
            </dialog>
        </div>
    );
};

export default AdminCatalogPage;