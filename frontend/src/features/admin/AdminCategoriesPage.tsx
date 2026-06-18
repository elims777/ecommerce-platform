import { useState, useRef } from 'react';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    DndContext, DragOverlay, MouseSensor, TouchSensor, useSensor, useSensors,
    type DragEndEvent, type DragStartEvent,
} from '@dnd-kit/core';
import {
    SortableContext, verticalListSortingStrategy,
    useSortable, arrayMove,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
    getCategoryTree, getCategoryById, createCategory, updateCategory,
    deleteCategory, activateCategory, deactivateCategory, reorderCategories,
} from '@/api/adminCategories';
import type { CategoryRequest } from '@/api/adminCategories';
import type { CategoryTree } from '@/types/product';

// ─── Утилиты ─────────────────────────────────────────────────────────────────

const flattenForSelect = (
    categories: CategoryTree[],
    excludeId?: number,
    prefix = '',
): { value: number; label: string }[] => {
    const result: { value: number; label: string }[] = [];
    for (const cat of categories) {
        if (cat.id !== excludeId) {
            result.push({ value: cat.id, label: prefix + cat.name });
            if (cat.children.length > 0)
                result.push(...flattenForSelect(cat.children, excludeId, prefix + '— '));
        }
    }
    return result;
};

const findInTree = (tree: CategoryTree[], id: number): CategoryTree | null => {
    for (const node of tree) {
        if (node.id === id) return node;
        const found = findInTree(node.children, id);
        if (found) return found;
    }
    return null;
};

// ─── Sortable-узел дерева ─────────────────────────────────────────────────────

interface TreeItemProps {
    cat: CategoryTree;
    selectedId: number | null;
    onSelect: (id: number) => void;
    isDraggingId: number | null;
}

const CategoryTreeItem = ({ cat, selectedId, onSelect, isDraggingId }: TreeItemProps) => {
    const { attributes, listeners, setNodeRef, setActivatorNodeRef, transform, transition, isDragging } = useSortable({ id: cat.id });

    const liStyle: React.CSSProperties = {
        listStyle: 'none',
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.4 : 1,
    };

    return (
        <li ref={setNodeRef} style={liStyle}>
            <div
                onClick={() => !isDragging && onSelect(cat.id)}
                style={{
                    padding: '5px 8px',
                    borderRadius: 4,
                    cursor: 'pointer',
                    fontSize: 13,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 4,
                    background: selectedId === cat.id ? 'var(--surface-2, #f5f5f5)' : 'transparent',
                    color: selectedId === cat.id ? 'var(--brand-red)' : 'var(--ink-1)',
                    fontWeight: selectedId === cat.id ? 600 : 400,
                    userSelect: 'none',
                }}
            >
                {/* setActivatorNodeRef — handle откуда начинается drag, setNodeRef на li — границы элемента */}
                <span
                    ref={setActivatorNodeRef}
                    {...listeners}
                    {...attributes}
                    style={{ cursor: 'grab', color: '#000', padding: '0 4px', touchAction: 'none', flexShrink: 0, display: 'inline-flex', background: 'red', minWidth: 20, minHeight: 20 }}
                    title="Перетащить"
                    onClick={(e) => e.stopPropagation()}
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

                <svg width="13" height="13" viewBox="0 0 15 15" fill="none" style={{ flexShrink: 0, color: 'var(--ink-3)' }}>
                    <path d="M1 3.5A1.5 1.5 0 0 1 2.5 2h3.879a1.5 1.5 0 0 1 1.06.44L8.5 3.5H12.5A1.5 1.5 0 0 1 14 5v6a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 11V3.5Z" stroke="currentColor" strokeWidth="1.2"/>
                </svg>
                <span style={{ flex: 1 }}>{cat.name}</span>
                {!cat.isActive && (
                    <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>(скрыта)</span>
                )}
            </div>

            {/* Дочерние категории — свой SortableContext на каждый уровень */}
            {cat.children.length > 0 && (
                <SortableCategoryLevel
                    categories={[...cat.children].sort((a, b) => a.displayOrder - b.displayOrder)}
                    selectedId={selectedId}
                    onSelect={onSelect}
                    isDraggingId={isDraggingId}
                    paddingLeft={18}
                />
            )}
        </li>
    );
};

// ─── Один уровень дерева с SortableContext ────────────────────────────────────

interface LevelProps {
    categories: CategoryTree[];
    selectedId: number | null;
    onSelect: (id: number) => void;
    isDraggingId: number | null;
    paddingLeft?: number;
}

const SortableCategoryLevel = ({ categories, selectedId, onSelect, isDraggingId, paddingLeft = 0 }: LevelProps) => (
    <SortableContext items={categories.map((c) => c.id)} strategy={verticalListSortingStrategy}>
        <ul style={{ margin: 0, padding: 0, paddingLeft }}>
            {categories.map((cat) => (
                <CategoryTreeItem
                    key={cat.id}
                    cat={cat}
                    selectedId={selectedId}
                    onSelect={onSelect}
                    isDraggingId={isDraggingId}
                />
            ))}
        </ul>
    </SortableContext>
);

// ─── Стили ────────────────────────────────────────────────────────────────────

const selectStyle: React.CSSProperties = {
    width: '100%', height: 34, padding: '0 10px',
    borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)',
    fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)',
};

const inputStyle: React.CSSProperties = {
    width: '100%', height: 34, padding: '0 10px',
    borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)',
    fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)',
    outline: 'none', boxSizing: 'border-box',
};

// ─── Страница ─────────────────────────────────────────────────────────────────

const AdminCategoriesPage = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const dialogRef = useRef<HTMLDialogElement>(null);

    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [catForm, setCatForm] = useState<CategoryRequest>({ name: '', description: '', parentId: undefined });
    const [draggingId, setDraggingId] = useState<number | null>(null);

    const { data: tree = [], isLoading, refetch } = useQuery({
        queryKey: ['adminCategoryTree'],
        queryFn: getCategoryTree,
    });

    const { data: selectedCategory } = useQuery({
        queryKey: ['category', selectedId],
        queryFn: () => getCategoryById(selectedId!),
        enabled: !!selectedId,
    });

    const invalidate = () => {
        queryClient.invalidateQueries({ queryKey: ['adminCategoryTree'] });
        queryClient.invalidateQueries({ queryKey: ['categories'] });
    };

    const createMutation = useMutation({
        mutationFn: createCategory,
        onSuccess: () => {
            messageApi.success('Категория создана');
            invalidate();
            dialogRef.current?.close();
            setCatForm({ name: '', description: '', parentId: undefined });
        },
        onError: () => messageApi.error('Ошибка при создании категории'),
    });

    const updateMutation = useMutation({
        mutationFn: ({ id, request }: { id: number; request: CategoryRequest }) => updateCategory(id, request),
        onSuccess: () => {
            messageApi.success('Категория обновлена');
            invalidate();
            queryClient.invalidateQueries({ queryKey: ['category', editingId] });
            dialogRef.current?.close();
            setEditingId(null);
            setCatForm({ name: '', description: '', parentId: undefined });
        },
        onError: () => messageApi.error('Ошибка при обновлении категории'),
    });

    const deleteMutation = useMutation({
        mutationFn: deleteCategory,
        onSuccess: () => {
            messageApi.success('Категория удалена');
            invalidate();
            setSelectedId(null);
        },
        onError: () => messageApi.error('Ошибка при удалении. Возможно есть товары или подкатегории.'),
    });

    const toggleActiveMutation = useMutation({
        mutationFn: async ({ id, isActive }: { id: number; isActive: boolean }) =>
            isActive ? deactivateCategory(id) : activateCategory(id),
        onSuccess: () => {
            invalidate();
            queryClient.invalidateQueries({ queryKey: ['category', selectedId] });
            messageApi.success('Статус обновлён');
        },
        onError: () => messageApi.error('Ошибка при обновлении статуса'),
    });

    const reorderMutation = useMutation({
        mutationFn: reorderCategories,
        onSuccess: () => invalidate(),
        onError: () => messageApi.error('Ошибка при сохранении порядка'),
    });

    // ─── DnD ────────────────────────────────────────────────────────────────

    const sensors = useSensors(
        useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
        useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 5 } }),
    );

    // Найти уровень (siblings) к которому принадлежит activeId
    const findSiblings = (activeId: number): CategoryTree[] => {
        // Корневой уровень
        if (tree.some((c) => c.id === activeId)) {
            return [...tree].sort((a, b) => a.displayOrder - b.displayOrder);
        }
        // Ищем родителя
        const findParentChildren = (nodes: CategoryTree[]): CategoryTree[] | null => {
            for (const node of nodes) {
                if (node.children.some((c) => c.id === activeId)) {
                    return [...node.children].sort((a, b) => a.displayOrder - b.displayOrder);
                }
                const found = findParentChildren(node.children);
                if (found) return found;
            }
            return null;
        };
        return findParentChildren(tree) ?? [];
    };

    const handleDragStart = ({ active }: DragStartEvent) => {
        setDraggingId(active.id as number);
    };

    const handleDragEnd = ({ active, over }: DragEndEvent) => {
        setDraggingId(null);
        if (!over || active.id === over.id) return;

        const activeId = active.id as number;
        const overId = over.id as number;

        const siblings = findSiblings(activeId);
        const oldIndex = siblings.findIndex((c) => c.id === activeId);
        const newIndex = siblings.findIndex((c) => c.id === overId);
        if (oldIndex === -1 || newIndex === -1) return;

        const reordered = arrayMove(siblings, oldIndex, newIndex);

        // Один запрос на бэк — { id: displayOrder, ... }
        const orders: Record<number, number> = {};
        reordered.forEach((cat, i) => { orders[cat.id] = i * 10; });
        reorderMutation.mutate(orders);

        // Оптимистично обновляем кэш чтобы список не прыгал обратно до ответа сервера
        queryClient.setQueryData(['adminCategoryTree'], (old: CategoryTree[] | undefined) => {
            if (!old) return old;
            const applyReorder = (nodes: CategoryTree[]): CategoryTree[] =>
                nodes.map((node) => {
                    if (node.children.some((c) => c.id === activeId)) {
                        return { ...node, children: reordered.map((c, i) => ({ ...c, displayOrder: i * 10 })) };
                    }
                    return { ...node, children: applyReorder(node.children) };
                });

            if (tree.some((c) => c.id === activeId)) {
                return reordered.map((c, i) => ({ ...c, displayOrder: i * 10 }));
            }
            return applyReorder(old);
        });
    };

    // ─── Handlers ───────────────────────────────────────────────────────────

    const handleAdd = (parentId?: number) => {
        setEditingId(null);
        setCatForm({ name: '', description: '', parentId });
        dialogRef.current?.showModal();
    };

    const handleEdit = async () => {
        if (!selectedCategory) return;
        setEditingId(selectedCategory.id);
        setCatForm({
            name: selectedCategory.name,
            description: selectedCategory.description || '',
            parentId: selectedCategory.parentId,
        });
        dialogRef.current?.showModal();
    };

    const handleSubmit = () => {
        if (!catForm.name.trim()) {
            messageApi.error('Введите название');
            return;
        }
        if (editingId) {
            updateMutation.mutate({ id: editingId, request: catForm });
        } else {
            createMutation.mutate(catForm);
        }
    };

    const draggingCat = draggingId ? findInTree(tree, draggingId) : null;
    const parentOptions = flattenForSelect(tree, editingId || undefined);
    const isPending = createMutation.isPending || updateMutation.isPending;
    const rootSorted = [...tree].sort((a, b) => a.displayOrder - b.displayOrder);

    return (
        <div>
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, margin: 0 }}>Категории</h2>
                <div style={{ display: 'flex', gap: 8 }}>
                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={() => refetch()}>
                        <svg width="13" height="13" viewBox="0 0 15 15" fill="none" style={{ marginRight: 4 }}>
                            <path d="M1.85 7.5c0-2.835 2.21-5.15 4.98-5.38l-.37.37a.5.5 0 0 0 .707.708L8.854 1.51a.5.5 0 0 0 0-.707L7.167.116a.5.5 0 1 0-.707.707l.284.284C3.28 1.39.85 4.182.85 7.5c0 3.59 2.91 6.5 6.5 6.5s6.5-2.91 6.5-6.5a.5.5 0 0 0-1 0c0 3.038-2.462 5.5-5.5 5.5S1.85 10.538 1.85 7.5Z" fill="currentColor" fillRule="evenodd" clipRule="evenodd"/>
                        </svg>
                        Обновить
                    </button>
                    <button className="rf-btn rf-btn-sm rf-btn-primary" onClick={() => handleAdd()}>
                        + Добавить категорию
                    </button>
                </div>
            </div>

            {/* Two-column layout */}
            <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
                {/* Left: tree */}
                <div className="rf-card" style={{ flex: '0 0 360px', overflow: 'hidden' }}>
                    <div className="rf-card-header">
                        <h3>Дерево категорий</h3>
                        <span style={{ fontSize: 12, color: 'var(--ink-3)', marginLeft: 8 }}>перетащите для изменения порядка</span>
                    </div>
                    <div style={{ padding: '8px 8px', maxHeight: 640, overflowY: 'auto' }}>
                        {isLoading ? (
                            <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
                        ) : tree.length === 0 ? (
                            <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>Нет категорий</div>
                        ) : (
                            <DndContext
                                sensors={sensors}
                                onDragStart={handleDragStart}
                                onDragEnd={handleDragEnd}
                            >
                                <SortableCategoryLevel
                                    categories={rootSorted}
                                    selectedId={selectedId}
                                    onSelect={setSelectedId}
                                    isDraggingId={draggingId}
                                />

                                <DragOverlay>
                                    {draggingCat && (
                                        <div style={{
                                            padding: '5px 10px',
                                            background: 'var(--surface)',
                                            border: '1.5px solid var(--brand-red)',
                                            borderRadius: 4,
                                            fontSize: 13,
                                            fontWeight: 500,
                                            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: 6,
                                            color: 'var(--ink-1)',
                                        }}>
                                            <svg width="13" height="13" viewBox="0 0 15 15" fill="none" style={{ color: 'var(--ink-3)' }}>
                                                <path d="M1 3.5A1.5 1.5 0 0 1 2.5 2h3.879a1.5 1.5 0 0 1 1.06.44L8.5 3.5H12.5A1.5 1.5 0 0 1 14 5v6a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 11V3.5Z" stroke="currentColor" strokeWidth="1.2"/>
                                            </svg>
                                            {draggingCat.name}
                                        </div>
                                    )}
                                </DragOverlay>
                            </DndContext>
                        )}
                    </div>
                </div>

                {/* Right: detail */}
                <div className="rf-card" style={{ flex: 1, overflow: 'hidden' }}>
                    {!selectedCategory ? (
                        <div style={{ padding: 60, textAlign: 'center', color: 'var(--ink-3)' }}>
                            Выберите категорию в дереве
                        </div>
                    ) : (
                        <>
                            <div className="rf-card-header">
                                <h3>{selectedCategory.name}</h3>
                                <div style={{ flex: 1 }} />
                                <div style={{ display: 'flex', gap: 6 }}>
                                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={() => handleAdd(selectedCategory.id)}>
                                        + Подкатегория
                                    </button>
                                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={handleEdit}>
                                        Редактировать
                                    </button>
                                    <button
                                        className="rf-btn rf-btn-sm rf-btn-ghost"
                                        style={{ color: 'var(--brand-red)' }}
                                        onClick={() => {
                                            if (window.confirm('Удалить категорию? Убедитесь что нет товаров и подкатегорий.')) {
                                                deleteMutation.mutate(selectedCategory.id);
                                            }
                                        }}
                                    >
                                        Удалить
                                    </button>
                                </div>
                            </div>
                            <div className="rf-detail-grid">
                                <div className="rf-detail-label">ID</div>
                                <div className="rf-detail-value rf-tabular">{selectedCategory.id}</div>

                                <div className="rf-detail-label">Slug</div>
                                <div className="rf-detail-value rf-mono" style={{ fontSize: 13 }}>{selectedCategory.slug}</div>

                                <div className="rf-detail-label">Описание</div>
                                <div className="rf-detail-value">{selectedCategory.description || '—'}</div>

                                <div className="rf-detail-label">Родитель</div>
                                <div className="rf-detail-value">{selectedCategory.parentName || 'Корневая'}</div>

                                <div className="rf-detail-label">Порядок</div>
                                <div className="rf-detail-value rf-tabular">{selectedCategory.displayOrder}</div>

                                <div className="rf-detail-label">External ID</div>
                                <div className="rf-detail-value rf-mono" style={{ fontSize: 13 }}>{selectedCategory.externalId || '—'}</div>

                                <div className="rf-detail-label">Активна</div>
                                <div className="rf-detail-value">
                                    <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer' }}>
                                        <input
                                            type="checkbox"
                                            checked={selectedCategory.isActive}
                                            onChange={() => toggleActiveMutation.mutate({ id: selectedCategory.id, isActive: selectedCategory.isActive })}
                                            disabled={toggleActiveMutation.isPending}
                                        />
                                        <span className={`rf-badge ${selectedCategory.isActive ? 'rf-badge-success' : 'rf-badge-neutral'}`}>
                                            {selectedCategory.isActive ? 'Да' : 'Нет'}
                                        </span>
                                    </label>
                                </div>
                            </div>
                        </>
                    )}
                </div>
            </div>

            {/* Category dialog */}
            <dialog
                ref={dialogRef}
                style={{ padding: 0, border: 'none', borderRadius: 'var(--r-4)', boxShadow: '0 8px 32px rgba(0,0,0,0.18)', minWidth: 420 }}
                onClick={(e) => { if (e.target === dialogRef.current) dialogRef.current?.close(); }}
            >
                <div style={{ padding: '20px 24px', borderBottom: '1px solid var(--line-1)' }}>
                    <h3 style={{ margin: 0, fontFamily: 'var(--font-head)', fontSize: 16, fontWeight: 600 }}>
                        {editingId ? 'Редактировать категорию' : 'Новая категория'}
                    </h3>
                </div>
                <div style={{ padding: '20px 24px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
                        <span style={{ color: 'var(--ink-3)', fontWeight: 500 }}>
                            Название <span style={{ color: 'var(--brand-red)' }}>*</span>
                        </span>
                        <input
                            type="text"
                            style={inputStyle}
                            placeholder="Название категории"
                            value={catForm.name}
                            onChange={(e) => setCatForm((f) => ({ ...f, name: e.target.value }))}
                        />
                    </label>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
                        <span style={{ color: 'var(--ink-3)', fontWeight: 500 }}>Описание</span>
                        <textarea
                            rows={3}
                            style={{ ...inputStyle, height: 'auto', padding: '8px 10px', resize: 'vertical' }}
                            placeholder="Описание категории"
                            value={catForm.description ?? ''}
                            onChange={(e) => setCatForm((f) => ({ ...f, description: e.target.value }))}
                        />
                    </label>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13 }}>
                        <span style={{ color: 'var(--ink-3)', fontWeight: 500 }}>Родительская категория</span>
                        <select
                            style={selectStyle}
                            value={catForm.parentId ?? ''}
                            onChange={(e) => setCatForm((f) => ({ ...f, parentId: e.target.value ? Number(e.target.value) : undefined }))}
                        >
                            <option value="">Корневая (без родителя)</option>
                            {parentOptions.map((opt) => (
                                <option key={opt.value} value={opt.value}>{opt.label}</option>
                            ))}
                        </select>
                    </label>
                </div>
                <div style={{ padding: '12px 24px 20px', display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <button className="rf-btn rf-btn-sm rf-btn-quiet" onClick={() => dialogRef.current?.close()}>Отмена</button>
                    <button className="rf-btn rf-btn-sm rf-btn-primary" disabled={isPending} onClick={handleSubmit}>
                        {isPending ? 'Сохранение…' : editingId ? 'Сохранить' : 'Создать'}
                    </button>
                </div>
            </dialog>
        </div>
    );
};

export default AdminCategoriesPage;
