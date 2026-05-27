import { useState, useRef } from 'react';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    getCategoryTree, getCategoryById, createCategory, updateCategory,
    deleteCategory, activateCategory, deactivateCategory,
} from '@/api/adminCategories';
import type { CategoryRequest } from '@/api/adminCategories';
import type { CategoryTree } from '@/types/product';

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

interface TreeItemProps {
    cat: CategoryTree;
    selectedId: number | null;
    onSelect: (id: number) => void;
}

const CategoryTreeItem = ({ cat, selectedId, onSelect }: TreeItemProps) => (
    <li style={{ listStyle: 'none' }}>
        <div
            onClick={() => onSelect(cat.id)}
            style={{
                padding: '6px 10px',
                borderRadius: 4,
                cursor: 'pointer',
                fontSize: 13,
                background: selectedId === cat.id ? 'var(--red-tint)' : 'transparent',
                color: selectedId === cat.id ? 'var(--brand-red)' : 'var(--ink-1)',
                fontWeight: selectedId === cat.id ? 600 : 400,
                display: 'flex',
                alignItems: 'center',
                gap: 6,
            }}
        >
            <svg width="13" height="13" viewBox="0 0 15 15" fill="none" style={{ flexShrink: 0, color: 'var(--ink-3)' }}>
                <path d="M1 3.5A1.5 1.5 0 0 1 2.5 2h3.879a1.5 1.5 0 0 1 1.06.44L8.5 3.5H12.5A1.5 1.5 0 0 1 14 5v6a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 1 11V3.5Z" stroke="currentColor" strokeWidth="1.2"/>
            </svg>
            <span>{cat.name}</span>
            {!cat.isActive && (
                <span style={{ fontSize: 11, color: 'var(--ink-3)', marginLeft: 4 }}>(скрыта)</span>
            )}
        </div>
        {cat.children.length > 0 && (
            <ul style={{ paddingLeft: 16, margin: 0 }}>
                {cat.children
                    .sort((a, b) => a.displayOrder - b.displayOrder)
                    .map((child) => (
                        <CategoryTreeItem key={child.id} cat={child} selectedId={selectedId} onSelect={onSelect} />
                    ))}
            </ul>
        )}
    </li>
);

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

const AdminCategoriesPage = () => {
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const dialogRef = useRef<HTMLDialogElement>(null);

    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [catForm, setCatForm] = useState<CategoryRequest>({ name: '', description: '', parentId: undefined });

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

    const parentOptions = flattenForSelect(tree, editingId || undefined);
    const isPending = createMutation.isPending || updateMutation.isPending;

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
                <div className="rf-card" style={{ flex: '0 0 340px', overflow: 'hidden' }}>
                    <div className="rf-card-header"><h3>Дерево категорий</h3></div>
                    <div style={{ padding: '12px 8px', maxHeight: 600, overflowY: 'auto' }}>
                        {isLoading ? (
                            <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>Загрузка…</div>
                        ) : tree.length === 0 ? (
                            <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>Нет категорий</div>
                        ) : (
                            <ul style={{ margin: 0, padding: 0 }}>
                                {tree
                                    .sort((a, b) => a.displayOrder - b.displayOrder)
                                    .map((cat) => (
                                        <CategoryTreeItem key={cat.id} cat={cat} selectedId={selectedId} onSelect={setSelectedId} />
                                    ))}
                            </ul>
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
