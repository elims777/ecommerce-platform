import { useState, useMemo, useRef, useCallback } from 'react';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams, Link } from 'react-router-dom';
import { NavLink } from '@/components/navigation';
import { getAdminProducts, searchProducts } from '@/api/products';
import {
    getCategoryTree,
    createCategory,
    updateCategory,
    deleteCategory,
    getCategoryById,
    setCategoryParent,
    reorderCategories,
} from '@/api/adminCategories';
import { batchUpdateActive, changeProductCategory, bulkDeleteProducts, setParentProduct, searchProducts as searchProductsApi, reorderProducts } from '@/api/adminProducts';
import apiClient from '@/api/client';
import type { CategoryRequest } from '@/api/adminCategories';
import type { Product, CategoryTree } from '@/types/product';
import {
    DndContext, DragOverlay, PointerSensor, useSensor, useSensors,
    type DragEndEvent, type DragStartEvent,
} from '@dnd-kit/core';
import { SortableContext, verticalListSortingStrategy, useSortable, arrayMove } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

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

// ── Category tree (sortable) ─────────────────────────────────────────────────

const hasSelectedDescendant = (cats: CategoryTree[], selectedId: number | undefined): boolean => {
    if (!selectedId) return false;
    for (const cat of cats) {
        if (cat.id === selectedId) return true;
        if (cat.children.length > 0 && hasSelectedDescendant(cat.children, selectedId)) return true;
    }
    return false;
};

interface SortableCatItemProps {
    cat: CategoryTree;
    selectedId: number | undefined;
    onSelect: (id: number) => void;
}

const SortableCatItem = ({ cat, selectedId, onSelect }: SortableCatItemProps) => {
    const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id: cat.id });
    const isSelected = selectedId === cat.id;
    const hasChildren = cat.children.length > 0;
    const isExpanded = hasChildren && (isSelected || hasSelectedDescendant(cat.children, selectedId));

    return (
        <li ref={setNodeRef} style={{ listStyle: 'none', transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1 }}>
            <div
                onClick={() => !isDragging && onSelect(cat.id)}
                style={{
                    display: 'flex', alignItems: 'center', gap: 4,
                    padding: '6px 10px', borderRadius: 'var(--r-3)', cursor: 'pointer',
                    fontSize: 'var(--text-base)', fontWeight: isSelected ? 600 : 400,
                    background: isSelected ? 'var(--red-tint)' : 'transparent',
                    color: isSelected ? 'var(--brand-red)' : 'var(--ink-1)',
                    transition: 'background 0.1s, color 0.1s', userSelect: 'none',
                }}
            >
                <span
                    ref={setActivatorNodeRef}
                    {...listeners}
                    {...attributes}
                    onClick={(e) => e.stopPropagation()}
                    style={{ cursor: 'grab', display: 'inline-flex', flexShrink: 0, color: '#999', touchAction: 'none', padding: '0 2px' }}
                >
                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                        <circle cx="4" cy="2.5" r="1" fill="currentColor"/>
                        <circle cx="8" cy="2.5" r="1" fill="currentColor"/>
                        <circle cx="4" cy="6" r="1" fill="currentColor"/>
                        <circle cx="8" cy="6" r="1" fill="currentColor"/>
                        <circle cx="4" cy="9.5" r="1" fill="currentColor"/>
                        <circle cx="8" cy="9.5" r="1" fill="currentColor"/>
                    </svg>
                </span>
                {hasChildren ? (
                    <svg width="10" height="10" viewBox="0 0 8 8" fill="none" style={{ flexShrink: 0, opacity: 0.55, transform: isExpanded ? 'rotate(90deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}>
                        <path d="M2 1.5l3 3-3 3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
                    </svg>
                ) : (
                    <svg width="13" height="13" viewBox="0 0 16 16" fill="none" style={{ flexShrink: 0, opacity: 0.55 }}>
                        <path d="M2 4h5l2 2h5v8H2z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" fill="none" />
                    </svg>
                )}
                <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{cat.name}</span>
                {!cat.isActive && (
                    <span style={{ fontSize: 10, padding: '1px 5px', borderRadius: 'var(--r-2)', background: 'var(--surface-3)', color: 'var(--ink-3)', flexShrink: 0 }}>скрыта</span>
                )}
            </div>
            {hasChildren && isExpanded && (
                <SortableCatLevel categories={cat.children} selectedId={selectedId} onSelect={onSelect} paddingLeft={14} />
            )}
        </li>
    );
};

interface SortableCatLevelProps {
    categories: CategoryTree[];
    selectedId: number | undefined;
    onSelect: (id: number) => void;
    paddingLeft?: number;
}

const SortableCatLevel = ({ categories, selectedId, onSelect, paddingLeft = 0 }: SortableCatLevelProps) => {
    const sorted = [...categories].sort((a, b) => a.displayOrder - b.displayOrder);
    return (
        <SortableContext items={sorted.map((c) => c.id)} strategy={verticalListSortingStrategy}>
            <ul style={{ listStyle: 'none', margin: 0, padding: 0, paddingLeft }}>
                {sorted.map((cat) => (
                    <SortableCatItem key={cat.id} cat={cat} selectedId={selectedId} onSelect={onSelect} />
                ))}
            </ul>
        </SortableContext>
    );
};

// ── Inline category form state ───────────────────────────────────────────────

interface CatFormState {
    name: string;
    description: string;
    parentId: number | '';
}

const EMPTY_CAT_FORM: CatFormState = { name: '', description: '', parentId: '' };

// ── Sortable строка товара ────────────────────────────────────────────────────

interface SortableRowProps {
    id: number;
    disabled: boolean;
    children: (dragHandleProps: {
        trRef: (node: HTMLElement | null) => void;
        handleRef: (node: HTMLElement | null) => void;
        listeners: Record<string, unknown> | undefined;
        attributes: Record<string, unknown> | undefined;
        isDragging: boolean;
        style: React.CSSProperties;
    }) => React.ReactNode;
}

const SortableRow = ({ id, disabled, children }: SortableRowProps) => {
    const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id, disabled });
    const style: React.CSSProperties = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
    };
    return <>{children({ trRef: setNodeRef, handleRef: setActivatorNodeRef, listeners: listeners as unknown as Record<string, unknown>, attributes: attributes as unknown as Record<string, unknown>, isDragging, style })}</>;
};

// ── Main component ────────────────────────────────────────────────────────────

const AdminCatalogPage = () => {
    const [searchParams, setSearchParams] = useSearchParams();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();

    // selectedCategoryId хранится в URL (?category=N) — сохраняется при F5
    const selectedCategoryId = searchParams.get('category') ? Number(searchParams.get('category')) : undefined;
    const setSelectedCategoryId = (id: number | undefined) => {
        setSearchParams(id ? { category: String(id) } : {}, { replace: true });
    };

    // State
    const [searchQuery, setSearchQuery] = useState('');
    const [activeLetter, setActiveLetter] = useState<string | null>(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [showAllProducts, setShowAllProducts] = useState(!selectedCategoryId);
    const [draggingProductId, setDraggingProductId] = useState<number | null>(null);
    const [activeFilter, setActiveFilter] = useState<'all' | 'active' | 'inactive'>('all');

    const sortOrder = selectedCategoryId ? 'displayOrder,asc' : 'name,asc';
    const dndEnabled = !!selectedCategoryId && !searchQuery;

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

    // Batch set parent
    const batchParentDialogRef = useRef<HTMLDialogElement>(null);
    const [parentSearch, setParentSearch] = useState('');
    const [parentSearchResults, setParentSearchResults] = useState<{ id: number; name: string }[]>([]);
    const [selectedParentId, setSelectedParentId] = useState<number | null>(null);
    const [selectedParentName, setSelectedParentName] = useState('');

    const [collapsedParents, setCollapsedParents] = useState<Set<number>>(new Set());

    const toggleCollapse = (parentId: number) => {
        setCollapsedParents(prev => {
            const next = new Set(prev);
            if (next.has(parentId)) next.delete(parentId);
            else next.add(parentId);
            return next;
        });
    };

    const [pageSize, setPageSize] = useState(() => {
        const saved = localStorage.getItem('adminCatalogPageSize');
        return saved ? Number(saved) : 20;
    });

    const handlePageSizeChange = (size: number) => {
        setPageSize(size);
        localStorage.setItem('adminCatalogPageSize', String(size));
        setCurrentPage(1);
    };

    // ── Queries ──────────────────────────────────────────────────────────────

    const { data: categoryTree = [], isLoading: treeLoading } = useQuery({
        queryKey: ['adminCategoryTree'],
        queryFn: getCategoryTree,
    });

    const isActiveParam = activeFilter === 'all' ? undefined : activeFilter === 'active';

    const {
        data: productsPage,
        isLoading: productsLoading,
        refetch: refetchProducts,
    } = useQuery({
        queryKey: ['adminCatalogProducts', { page: currentPage, category: selectedCategoryId, size: pageSize, sort: sortOrder, isActive: isActiveParam }],
        queryFn: () =>
            getAdminProducts({
                page: currentPage - 1,
                size: pageSize,
                categoryId: selectedCategoryId,
                sort: sortOrder,
                isActive: isActiveParam,
            }),
    });

    const { data: searchResults } = useQuery({
        queryKey: ['adminProductSearch', searchQuery],
        queryFn: () => searchProducts(searchQuery),
        enabled: searchQuery.length >= 2,
    });

    const { data: inactiveCountPage } = useQuery({
        queryKey: ['adminCatalogInactiveCount', { category: selectedCategoryId }],
        queryFn: () => getAdminProducts({ page: 0, size: 1, categoryId: selectedCategoryId, isActive: false }),
    });
    const inactiveCount = inactiveCountPage?.totalElements ?? 0;

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

    // Grouped rows: parents first, children inserted right after their parent
    const groupedRows = useMemo(() => {
        const childrenByParent = new Map<number, Product[]>();
        const roots: Product[] = [];
        for (const p of displayProducts) {
            if (p.isVariantChild && p.parentProductId != null) {
                const arr = childrenByParent.get(p.parentProductId) ?? [];
                arr.push(p);
                childrenByParent.set(p.parentProductId, arr);
            } else {
                roots.push(p);
            }
        }
        const result: { product: Product; isChild: boolean; childCount: number }[] = [];
        for (const root of roots) {
            const children = childrenByParent.get(root.id) ?? [];
            result.push({ product: root, isChild: false, childCount: children.length });
            for (const child of children) {
                result.push({ product: child, isChild: true, childCount: 0 });
            }
        }
        return result;
    }, [displayProducts]);

    const invalidateAll = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
        queryClient.invalidateQueries({ queryKey: ['adminCatalogInactiveCount'] });
        queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
        queryClient.invalidateQueries({ queryKey: ['categories'] });
    }, [queryClient]);

    // ── Category DnD ─────────────────────────────────────────────────────────

    const catDndSensors = useSensors(
        useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    );

    const [draggingCatId, setDraggingCatId] = useState<number | null>(null);

    const reorderCatMutation = useMutation({
        mutationFn: reorderCategories,
        onSuccess: () => invalidateAll(),
        onError: () => messageApi.error('Ошибка при сохранении порядка категорий'),
    });

    const findCatSiblings = (activeId: number): CategoryTree[] => {
        if (categoryTree.some((c) => c.id === activeId))
            return [...categoryTree].sort((a, b) => a.displayOrder - b.displayOrder);
        const find = (nodes: CategoryTree[]): CategoryTree[] | null => {
            for (const node of nodes) {
                if (node.children.some((c) => c.id === activeId))
                    return [...node.children].sort((a, b) => a.displayOrder - b.displayOrder);
                const found = find(node.children);
                if (found) return found;
            }
            return null;
        };
        return find(categoryTree) ?? [];
    };

    const handleCatDragEnd = ({ active, over }: DragEndEvent) => {
        setDraggingCatId(null);
        if (!over || active.id === over.id) return;
        const activeId = active.id as number;
        const overId = over.id as number;
        const siblings = findCatSiblings(activeId);
        const oldIndex = siblings.findIndex((c) => c.id === activeId);
        const newIndex = siblings.findIndex((c) => c.id === overId);
        if (oldIndex === -1 || newIndex === -1) return;
        const reordered = arrayMove(siblings, oldIndex, newIndex);
        queryClient.setQueryData(['adminCategoryTree'], (old: CategoryTree[] | undefined) => {
            if (!old) return old;
            const apply = (nodes: CategoryTree[]): CategoryTree[] =>
                nodes.map((node) => {
                    if (node.children.some((c) => c.id === activeId))
                        return { ...node, children: reordered.map((c, i) => ({ ...c, displayOrder: i * 10 })) };
                    return { ...node, children: apply(node.children) };
                });
            if (categoryTree.some((c) => c.id === activeId))
                return reordered.map((c, i) => ({ ...c, displayOrder: i * 10 }));
            return apply(old);
        });
        const orders: Record<number, number> = {};
        reordered.forEach((c, i) => { orders[c.id] = i * 10; });
        reorderCatMutation.mutate(orders);
    };

    // ── Product DnD ──────────────────────────────────────────────────────────

    const dndSensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }));

    const reorderMutation = useMutation({
        mutationFn: reorderProducts,
        onError: () => { messageApi.error('Ошибка при сохранении порядка'); invalidateAll(); },
    });

    const handleProductDragStart = ({ active }: DragStartEvent) => {
        setDraggingProductId(active.id as number);
    };

    const handleProductDragEnd = ({ active, over }: DragEndEvent) => {
        setDraggingProductId(null);
        if (!over || active.id === over.id) return;

        const activeId = active.id as number;
        const overId = over.id as number;
        const roots = groupedRows.filter((r) => !r.isChild).map((r) => r.product);
        const oldIndex = roots.findIndex((p) => p.id === activeId);
        const newIndex = roots.findIndex((p) => p.id === overId);
        if (oldIndex === -1 || newIndex === -1) return;

        const reordered = arrayMove(roots, oldIndex, newIndex);

        // Оптимистичное обновление кэша
        queryClient.setQueryData<{ content: Product[]; totalElements: number }>(
            ['adminCatalogProducts', { page: currentPage, category: selectedCategoryId, size: pageSize, sort: sortOrder }],
            (old) => {
                if (!old) return old;
                const children = old.content.filter((p) => p.isVariantChild);
                const newContent = [
                    ...reordered.map((p, i) => ({ ...p, displayOrder: i * 10 })),
                    ...children,
                ];
                return { ...old, content: newContent };
            },
        );

        // Один запрос на бэк
        const orders: Record<number, number> = {};
        reordered.forEach((p, i) => { orders[p.id] = i * 10; });
        reorderMutation.mutate(orders);
    };

    const draggingProduct = draggingProductId
        ? groupedRows.find((r) => r.product.id === draggingProductId)?.product
        : null;

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

    const batchSetParentMutation = useMutation({
        mutationFn: (parentId: number) =>
            Promise.all(selectedRowKeys.map(id => setParentProduct(id, parentId))),
        onSuccess: () => {
            messageApi.success(`${selectedRowKeys.length} товаров назначены дочерними`);
            setSelectedRowKeys([]);
            batchParentDialogRef.current?.close();
            setSelectedParentId(null);
            setSelectedParentName('');
            setParentSearch('');
            setParentSearchResults([]);
            invalidateAll();
        },
        onError: () => messageApi.error('Ошибка при назначении родителя'),
    });

    const handleParentSearch = async (query: string) => {
        setParentSearch(query);
        setSelectedParentId(null);
        setSelectedParentName('');
        if (query.trim().length < 2) { setParentSearchResults([]); return; }
        try {
            const results = await searchProductsApi(query);
            setParentSearchResults(results.map(p => ({ id: p.id, name: p.name })));
        } catch {
            setParentSearchResults([]);
        }
    };

    const toggleProductMutation = useMutation({
        mutationFn: async ({ id, isActive }: { id: number; isActive: boolean }) => {
            await apiClient.put(`/v1/products/${id}/${isActive ? 'deactivate' : 'activate'}`);
        },
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['adminCatalogProducts'] });
            queryClient.invalidateQueries({ queryKey: ['adminCatalogInactiveCount'] });
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
                    <NavLink
                        to="/admin/products/new"
                        className="rf-btn rf-btn-sm rf-btn-primary"
                    >
                        <svg width="12" height="12" viewBox="0 0 16 16" fill="none">
                            <path d="M8 3v10M3 8h10" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                        </svg>
                        Товар
                    </NavLink>
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
                                <DndContext
                                    sensors={catDndSensors}
                                    onDragStart={({ active }) => setDraggingCatId(active.id as number)}
                                    onDragEnd={handleCatDragEnd}
                                >
                                    <SortableCatLevel
                                        categories={categoryTree}
                                        selectedId={selectedCategoryId}
                                        onSelect={handleCategorySelect}
                                    />
                                    <DragOverlay>
                                        {draggingCatId && (
                                            <div style={{ padding: '4px 10px', background: 'var(--surface)', border: '1.5px solid var(--brand-red)', borderRadius: 4, fontSize: 13, fontWeight: 500, boxShadow: '0 4px 12px rgba(0,0,0,0.12)' }}>
                                                {findCatName(categoryTree, draggingCatId)}
                                            </div>
                                        )}
                                    </DragOverlay>
                                </DndContext>
                            )}
                        </div>
                    </div>
                </div>

                {/* Right: products panel */}
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div className="rf-card">
                        <div className="rf-card-header" style={{ justifyContent: 'space-between' }}>
                            <h3>{pageTitle}</h3>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <div style={{ display: 'inline-flex', border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)', overflow: 'hidden', height: 26 }}>
                                {(['all', 'active', 'inactive'] as const).map((f) => (
                                    <button
                                        key={f}
                                        onClick={() => { setActiveFilter(f); setCurrentPage(1); setSelectedRowKeys([]); }}
                                        style={{
                                            padding: '0 10px',
                                            fontSize: 'var(--text-sm)',
                                            fontFamily: 'var(--font-body)',
                                            border: 'none',
                                            background: activeFilter === f ? 'var(--brand-navy)' : 'var(--surface)',
                                            color: activeFilter === f ? '#fff' : 'var(--ink-2)',
                                            cursor: 'pointer',
                                            borderLeft: f === 'all' ? 'none' : '1px solid var(--line-2)',
                                            fontWeight: activeFilter === f ? 600 : 400,
                                        }}
                                    >
                                        {f === 'all' ? 'Все' : f === 'active' ? 'Активные' : `Неактивные${inactiveCount > 0 ? ` (${inactiveCount})` : ''}`}
                                    </button>
                                ))}
                            </div>
                            {isPaginated && totalPages > 1 && (
                                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                    <select
                                        value={pageSize}
                                        onChange={(e) => handlePageSizeChange(Number(e.target.value))}
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
                                    <div className="rf-admin-pagination-pages" style={{ margin: 0 }}>
                                        <button
                                            className="rf-admin-page-btn"
                                            disabled={currentPage === 1}
                                            onClick={() => { setCurrentPage((p) => p - 1); setSelectedRowKeys([]); }}
                                        >‹</button>
                                        {renderPageButtons()}
                                        <button
                                            className="rf-admin-page-btn"
                                            disabled={currentPage === totalPages}
                                            onClick={() => { setCurrentPage((p) => p + 1); setSelectedRowKeys([]); }}
                                        >›</button>
                                    </div>
                                </div>
                            )}
                            </div>
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
                                <DndContext
                                    sensors={dndSensors}
                                    onDragStart={handleProductDragStart}
                                    onDragEnd={handleProductDragEnd}
                                >
                                <SortableContext
                                    items={groupedRows.filter((r) => !r.isChild).map((r) => r.product.id)}
                                    strategy={verticalListSortingStrategy}
                                >
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
                                            <th style={{ width: 20 }}></th>
                                            {dndEnabled && <th style={{ width: 24 }}></th>}
                                            <th style={{ width: 44 }}></th>
                                            <th>Название</th>
                                            <th style={{ width: 160 }}>Категория</th>
                                            <th style={{ width: 100 }}>Артикул</th>
                                            <th style={{ width: 130 }}>Цена</th>
                                            <th style={{ width: 55 }}>Ост.</th>
                                            <th style={{ width: 55 }}>Акт.</th>
                                            <th style={{ width: 40 }}></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {groupedRows.map(({ product, isChild, childCount }) => {
                                            if (isChild && product.parentProductId != null && collapsedParents.has(product.parentProductId)) return null;
                                            const imageUrl = getPrimaryImageUrl(product);
                                            const isMovingThis = moveProductId === product.id;
                                            const isCollapsed = !isChild && collapsedParents.has(product.id);
                                            return (
                                                <SortableRow key={product.id} id={product.id} disabled={!dndEnabled || isChild}>
                                                    {({ trRef, handleRef, listeners: rowListeners, attributes: rowAttributes, isDragging: rowDragging, style: rowStyle }) => (
                                                        <tr
                                                            ref={trRef as React.Ref<HTMLTableRowElement>}
                                                            style={{ ...(isChild ? { background: 'var(--surface-2)' } : {}), ...rowStyle }}
                                                        >
                                                    {/* Checkbox */}
                                                    <td style={{ paddingLeft: 16 }}>
                                                        <input
                                                            type="checkbox"
                                                            checked={selectedRowKeys.includes(product.id)}
                                                            onChange={() => toggleRow(product.id)}
                                                            style={{ cursor: 'pointer' }}
                                                        />
                                                    </td>

                                                    {/* Collapse toggle */}
                                                    <td style={{ width: 20, padding: 0, textAlign: 'center' }}>
                                                        {childCount > 0 && (
                                                            <button
                                                                onClick={(e) => { e.stopPropagation(); toggleCollapse(product.id); }}
                                                                style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: 18, height: 18, border: 'none', background: 'none', cursor: 'pointer', color: 'var(--ink-3)', padding: 0 }}
                                                                title={isCollapsed ? 'Развернуть варианты' : 'Свернуть варианты'}
                                                            >
                                                                <svg width="10" height="10" viewBox="0 0 8 8" fill="none" style={{ transform: isCollapsed ? 'rotate(-90deg)' : 'rotate(0deg)', transition: 'transform 0.15s' }}>
                                                                    <path d="M1 2.5l3 3 3-3" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round"/>
                                                                </svg>
                                                            </button>
                                                        )}
                                                    </td>

                                                    {/* Drag handle — только в режиме сортировки */}
                                                    {dndEnabled && (
                                                        <td style={{ width: 24, padding: '0 4px', textAlign: 'center' }}>
                                                            {!isChild && (
                                                                <span
                                                                    ref={handleRef as React.Ref<HTMLSpanElement>}
                                                                    {...rowListeners}
                                                                    {...rowAttributes}
                                                                    style={{ cursor: rowDragging ? 'grabbing' : 'grab', color: 'var(--ink-2)', display: 'inline-flex', touchAction: 'none' }}
                                                                    title="Перетащить"
                                                                >
                                                                    <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                                                                        <circle cx="4" cy="2.5" r="1" fill="currentColor"/>
                                                                        <circle cx="8" cy="2.5" r="1" fill="currentColor"/>
                                                                        <circle cx="4" cy="6" r="1" fill="currentColor"/>
                                                                        <circle cx="8" cy="6" r="1" fill="currentColor"/>
                                                                        <circle cx="4" cy="9.5" r="1" fill="currentColor"/>
                                                                        <circle cx="8" cy="9.5" r="1" fill="currentColor"/>
                                                                    </svg>
                                                                </span>
                                                            )}
                                                        </td>
                                                    )}

                                                    {/* Image */}
                                                    <td style={{ paddingLeft: isChild ? 4 : 0 }}>
                                                        {imageUrl ? (
                                                            <img
                                                                src={imageUrl}
                                                                width={isChild ? 28 : 36}
                                                                height={isChild ? 28 : 36}
                                                                style={{ objectFit: 'contain', borderRadius: 'var(--r-2)', display: 'block', opacity: isChild ? 0.8 : 1 }}
                                                                alt=""
                                                            />
                                                        ) : (
                                                            <div style={{
                                                                width: isChild ? 28 : 36, height: isChild ? 28 : 36, borderRadius: 'var(--r-2)',
                                                                background: isChild ? 'var(--surface-3)' : 'var(--surface-2)',
                                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                                            }}>
                                                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none">
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
                                                            {isChild && (
                                                                <span style={{ width: 2, height: 18, background: 'var(--line-2)', borderRadius: 2, flexShrink: 0 }} />
                                                            )}
                                                            <Link
                                                                to={`/admin/products/${product.id}/edit`}
                                                                style={{ color: isChild ? 'var(--ink-2)' : 'var(--brand-navy)', textDecoration: 'none', fontSize: 'var(--text-base)' }}
                                                            >
                                                                {product.name}
                                                            </Link>
                                                            {childCount > 0 && (
                                                                <span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 'var(--r-2)', background: 'var(--brand-navy)', color: '#fff', fontWeight: 600, flexShrink: 0 }}>
                                                                    {childCount} вар.
                                                                </span>
                                                            )}
                                                            {isChild && (
                                                                <span style={{ fontSize: 10, padding: '1px 6px', borderRadius: 'var(--r-2)', background: 'var(--surface-3)', color: 'var(--ink-3)', flexShrink: 0 }}>
                                                                    вариант
                                                                </span>
                                                            )}
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
                                                        {formatPrice(product.wholesalePrice ?? product.price)}
                                                        {product.price != null && product.price !== product.wholesalePrice && (
                                                            <div style={{ fontSize: 11, fontWeight: 400, color: 'var(--text-secondary, #888)', marginTop: 1 }}>
                                                                {formatPrice(product.price)} опт
                                                            </div>
                                                        )}
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
                                            )}
                                            </SortableRow>
                                            );
                                        })}
                                    </tbody>
                                </table>
                                </SortableContext>
                                <DragOverlay>
                                    {draggingProduct && (
                                        <table className="rf-admin-table" style={{ background: 'var(--surface)', boxShadow: '0 4px 16px rgba(0,0,0,0.12)', borderRadius: 4, opacity: 0.95 }}>
                                            <tbody>
                                                <tr>
                                                    <td colSpan={10} style={{ padding: '6px 12px', fontSize: 13, fontWeight: 500, color: 'var(--ink-1)', whiteSpace: 'nowrap' }}>
                                                        {draggingProduct.name}
                                                    </td>
                                                </tr>
                                            </tbody>
                                        </table>
                                    )}
                                </DragOverlay>
                                </DndContext>
                            )}
                        </div>

                        {/* Pagination */}
                        {isPaginated && totalPages > 1 && (
                            <div className="rf-admin-pagination">
                                <span>Всего {productsPage?.totalElements ?? 0}</span>
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
                    <button
                        className="rf-bulk-btn"
                        onClick={() => { setSelectedParentId(null); setSelectedParentName(''); setParentSearch(''); setParentSearchResults([]); batchParentDialogRef.current?.showModal(); }}
                    >
                        Назначить родителя
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

            {/* Batch set parent dialog */}
            <dialog
                ref={batchParentDialogRef}
                style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', background: 'var(--surface)', color: 'var(--ink-1)', padding: 0, width: 420, maxWidth: '90vw', boxShadow: 'var(--shadow-pop)', margin: 'auto', overflow: 'visible' }}
                onKeyDown={(e) => { if (e.key === 'Escape') { batchParentDialogRef.current?.close(); } }}
            >
                <div style={{ padding: '16px 22px', borderBottom: '1px solid var(--line-1)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-lg)' }}>
                        Назначить родителя для {selectedRowKeys.length} товаров
                    </span>
                    <button
                        className="rf-btn rf-btn-ghost"
                        style={{ height: 28, width: 28, padding: 0, fontSize: 'var(--text-xl)', color: 'var(--ink-3)' }}
                        onClick={() => batchParentDialogRef.current?.close()}
                    >✕</button>
                </div>
                <div style={{ padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 12, overflow: 'visible' }}>
                    <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>
                        Найдите родительский товар — выбранные товары станут его вариантами
                    </div>
                    <div style={{ position: 'relative' }}>
                        <input
                            type="text"
                            placeholder="Начните вводить название..."
                            value={parentSearch}
                            onChange={(e) => handleParentSearch(e.target.value)}
                            style={{ width: '100%', height: 34, padding: '0 10px', boxSizing: 'border-box', borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)', fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)', outline: 'none' }}
                        />
                        {parentSearchResults.length > 0 && !selectedParentId && (
                            <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 9999, background: 'var(--surface)', border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)', boxShadow: '0 4px 12px rgba(0,0,0,.08)', maxHeight: 200, overflowY: 'auto' }}>
                                {parentSearchResults.map(p => (
                                    <div
                                        key={p.id}
                                        onClick={() => { setSelectedParentId(p.id); setSelectedParentName(p.name); setParentSearch(p.name); setParentSearchResults([]); }}
                                        style={{ padding: '8px 12px', fontSize: 13, cursor: 'pointer', borderBottom: '1px solid var(--line-1)', color: 'var(--ink-1)' }}
                                        onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--surface-2)')}
                                        onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--surface)')}
                                    >
                                        {p.name}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                    {selectedParentId && (
                        <div style={{ padding: '8px 12px', background: 'var(--brand-green-soft)', borderRadius: 'var(--r-2)', fontSize: 13, color: 'var(--brand-green)', fontWeight: 500 }}>
                            Родитель: {selectedParentName}
                        </div>
                    )}
                </div>
                <div style={{ padding: '14px 22px', borderTop: '1px solid var(--line-1)', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={() => batchParentDialogRef.current?.close()}>Отмена</button>
                    <button
                        className="rf-btn rf-btn-sm rf-btn-primary"
                        disabled={!selectedParentId || batchSetParentMutation.isPending}
                        onClick={() => { if (selectedParentId) batchSetParentMutation.mutate(selectedParentId); }}
                    >
                        {batchSetParentMutation.isPending ? 'Назначение…' : 'Назначить'}
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