import { useState, useRef, useEffect } from 'react';
import { App } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { getProductById } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import {
    updateProduct, uploadImage, deleteImage, setPrimaryImage,
    addAttribute, updateAttribute, deleteAttribute, setParentProduct, searchProducts,
} from '@/api/adminProducts';
import type { ProductRequest, ProductAttributeRequest } from '@/api/adminProducts';
import type { CategoryTree, ProductImage } from '@/types/product';

const flattenCategories = (tree: CategoryTree[], prefix = ''): { value: number; label: string }[] => {
    const result: { value: number; label: string }[] = [];
    for (const node of tree) {
        result.push({ value: node.id, label: prefix + node.name });
        if (node.children.length > 0)
            result.push(...flattenCategories(node.children, prefix + '— '));
    }
    return result;
};

interface AttributeRow { id: number; attributeName: string; attributeValue: string; }

const inputStyle: React.CSSProperties = {
    width: '100%', height: 34, padding: '0 10px', boxSizing: 'border-box',
    borderRadius: 'var(--r-2)', border: '1px solid var(--line-2)',
    fontSize: 13, background: 'var(--surface)', color: 'var(--ink-1)', outline: 'none',
};
const labelStyle: React.CSSProperties = {
    display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13,
};
const labelTextStyle: React.CSSProperties = { color: 'var(--ink-3)', fontWeight: 500 };

const AdminProductEditPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const queryClient = useQueryClient();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const productId = Number(id);

    const [form, setForm] = useState<ProductRequest>({
        name: '', description: '', shortDescription: '', price: 0,
        stockQuantity: 0, categoryId: undefined, isActive: true,
        isFeatured: false, sku: '', unitOfMeasure: '', material: '',
    });
    const [formTouched, setFormTouched] = useState(false);

    const [attrForm, setAttrForm] = useState<ProductAttributeRequest>({ attributeName: '', attributeValue: '' });
    const [editingAttrId, setEditingAttrId] = useState<number | null>(null);

    const [parentSearch, setParentSearch] = useState('');
    const [parentSearchResults, setParentSearchResults] = useState<{ id: number; name: string }[]>([]);

    const { data: product, isLoading } = useQuery({
        queryKey: ['adminProduct', productId],
        queryFn: () => getProductById(productId),
        enabled: !!id,
    });

    const { data: categoryTree = [] } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    useEffect(() => {
        if (product && !formTouched) {
            setForm({
                name: product.name,
                description: product.description || '',
                shortDescription: product.shortDescription || '',
                price: product.price,
                stockQuantity: product.stockQuantity,
                categoryId: product.categoryId,
                isActive: product.isActive,
                isFeatured: product.isFeatured,
                sku: product.sku || '',
                unitOfMeasure: product.unitOfMeasure || '',
                material: product.material || '',
            });
        }
    }, [product]);

    const categoryOptions = flattenCategories(categoryTree);

    const invalidateProduct = () => {
        queryClient.invalidateQueries({ queryKey: ['adminProduct', productId] });
        queryClient.invalidateQueries({ queryKey: ['adminProducts'] });
    };

    const saveMutation = useMutation({
        mutationFn: (values: ProductRequest) => updateProduct(productId, values),
        onSuccess: () => { messageApi.success('Товар сохранён'); invalidateProduct(); },
        onError: () => messageApi.error('Ошибка при сохранении'),
    });

    const uploadMutation = useMutation({
        mutationFn: (file: File) => uploadImage(productId, file),
        onSuccess: () => { messageApi.success('Изображение загружено'); invalidateProduct(); },
        onError: () => messageApi.error('Ошибка при загрузке изображения'),
    });

    const deleteImageMutation = useMutation({
        mutationFn: (imageId: number) => deleteImage(productId, imageId),
        onSuccess: () => { messageApi.success('Изображение удалено'); invalidateProduct(); },
        onError: () => messageApi.error('Ошибка при удалении'),
    });

    const setPrimaryMutation = useMutation({
        mutationFn: (imageId: number) => setPrimaryImage(productId, imageId),
        onSuccess: () => { messageApi.success('Главное изображение обновлено'); invalidateProduct(); },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const addAttrMutation = useMutation({
        mutationFn: (request: ProductAttributeRequest) => addAttribute(productId, request),
        onSuccess: () => {
            messageApi.success('Характеристика добавлена');
            invalidateProduct();
            setAttrForm({ attributeName: '', attributeValue: '' });
        },
        onError: () => messageApi.error('Ошибка при добавлении'),
    });

    const updateAttrMutation = useMutation({
        mutationFn: ({ attrId, request }: { attrId: number; request: ProductAttributeRequest }) =>
            updateAttribute(productId, attrId, request),
        onSuccess: () => {
            messageApi.success('Характеристика обновлена');
            invalidateProduct();
            setEditingAttrId(null);
            setAttrForm({ attributeName: '', attributeValue: '' });
        },
        onError: () => messageApi.error('Ошибка при обновлении'),
    });

    const deleteAttrMutation = useMutation({
        mutationFn: (attrId: number) => deleteAttribute(productId, attrId),
        onSuccess: () => { messageApi.success('Характеристика удалена'); invalidateProduct(); },
        onError: () => messageApi.error('Ошибка при удалении'),
    });

    const setParentMutation = useMutation({
        mutationFn: (parentId: number | null) => setParentProduct(productId, parentId),
        onSuccess: () => { messageApi.success('Сохранено'); invalidateProduct(); setParentSearchResults([]); setParentSearch(''); },
        onError: () => messageApi.error('Ошибка при сохранении'),
    });

    const handleParentSearch = async (query: string) => {
        setParentSearch(query);
        if (query.trim().length < 2) { setParentSearchResults([]); return; }
        try {
            const results = await searchProducts(query);
            setParentSearchResults(results.filter(p => p.id !== productId).map(p => ({ id: p.id, name: p.name })));
        } catch {
            setParentSearchResults([]);
        }
    };

    const handleAttrSubmit = () => {
        if (!attrForm.attributeName.trim() || !attrForm.attributeValue.trim()) {
            messageApi.error('Заполните название и значение');
            return;
        }
        if (editingAttrId) {
            updateAttrMutation.mutate({ attrId: editingAttrId, request: attrForm });
        } else {
            addAttrMutation.mutate(attrForm);
        }
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (file) uploadMutation.mutate(file);
        e.target.value = '';
    };

    if (isLoading) {
        return <div style={{ textAlign: 'center', padding: 120, color: 'var(--ink-3)' }}>Загрузка…</div>;
    }
    if (!product) return null;

    const sortedImages = product.images
        ? [...product.images].sort((a, b) => {
            if (a.isPrimary) return -1;
            if (b.isPrimary) return 1;
            return a.displayOrder - b.displayOrder;
        })
        : [];

    return (
        <div>
            {/* Header */}
            <div className="rf-admin-back" onClick={() => navigate(-1)}>
                <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                    <path d="M10 4L6 8l4 4"/>
                </svg>
                Каталог
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
                <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, margin: 0 }}>
                    {product.name}
                </h2>
                <span className={`rf-badge ${product.isActive ? 'rf-badge-success' : 'rf-badge-neutral'}`}>
                    {product.isActive ? 'Активен' : 'Неактивен'}
                </span>
            </div>

            {/* Two-column layout */}
            <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
                {/* Left column */}
                <div style={{ flex: '1 1 0', minWidth: 0 }}>
                    {/* Main info card */}
                    <div className="rf-card" style={{ marginBottom: 16, overflow: 'hidden' }}>
                        <div className="rf-card-header"><h3>Основная информация</h3></div>
                        <div className="rf-card-body" style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                            <label style={labelStyle}>
                                <span style={labelTextStyle}>Название <span style={{ color: 'var(--brand-red)' }}>*</span></span>
                                <input
                                    type="text"
                                    style={inputStyle}
                                    value={form.name}
                                    onChange={(e) => { setForm((f) => ({ ...f, name: e.target.value })); setFormTouched(true); }}
                                />
                            </label>

                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                                <label style={labelStyle}>
                                    <span style={labelTextStyle}>Цена, ₽</span>
                                    <input
                                        type="number"
                                        min={0}
                                        step={0.01}
                                        style={inputStyle}
                                        value={form.price}
                                        onChange={(e) => { setForm((f) => ({ ...f, price: Number(e.target.value) })); setFormTouched(true); }}
                                    />
                                </label>
                                <label style={labelStyle}>
                                    <span style={labelTextStyle}>Остаток</span>
                                    <input
                                        type="number"
                                        min={0}
                                        style={inputStyle}
                                        value={form.stockQuantity}
                                        onChange={(e) => { setForm((f) => ({ ...f, stockQuantity: Number(e.target.value) })); setFormTouched(true); }}
                                    />
                                </label>
                            </div>

                            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr', gap: 14 }}>
                                <label style={labelStyle}>
                                    <span style={labelTextStyle}>Категория</span>
                                    <select
                                        style={{ ...inputStyle, height: 34 }}
                                        value={form.categoryId ?? ''}
                                        onChange={(e) => { setForm((f) => ({ ...f, categoryId: e.target.value ? Number(e.target.value) : undefined })); setFormTouched(true); }}
                                    >
                                        <option value="">Без категории</option>
                                        {categoryOptions.map((opt) => (
                                            <option key={opt.value} value={opt.value}>{opt.label}</option>
                                        ))}
                                    </select>
                                </label>
                                <label style={labelStyle}>
                                    <span style={labelTextStyle}>Артикул</span>
                                    <input
                                        type="text"
                                        style={inputStyle}
                                        value={form.sku ?? ''}
                                        onChange={(e) => { setForm((f) => ({ ...f, sku: e.target.value })); setFormTouched(true); }}
                                    />
                                </label>
                                <label style={labelStyle}>
                                    <span style={labelTextStyle}>Ед. изм.</span>
                                    <input
                                        type="text"
                                        style={inputStyle}
                                        placeholder="шт."
                                        value={form.unitOfMeasure ?? ''}
                                        onChange={(e) => { setForm((f) => ({ ...f, unitOfMeasure: e.target.value })); setFormTouched(true); }}
                                    />
                                </label>
                            </div>

                            <label style={labelStyle}>
                                <span style={labelTextStyle}>Краткое описание</span>
                                <textarea
                                    rows={2}
                                    style={{ ...inputStyle, height: 'auto', padding: '8px 10px', resize: 'vertical' }}
                                    value={form.shortDescription ?? ''}
                                    onChange={(e) => { setForm((f) => ({ ...f, shortDescription: e.target.value })); setFormTouched(true); }}
                                />
                            </label>

                            <label style={labelStyle}>
                                <span style={labelTextStyle}>Полное описание</span>
                                <textarea
                                    rows={5}
                                    style={{ ...inputStyle, height: 'auto', padding: '8px 10px', resize: 'vertical' }}
                                    value={form.description ?? ''}
                                    onChange={(e) => { setForm((f) => ({ ...f, description: e.target.value })); setFormTouched(true); }}
                                />
                            </label>

                            <label style={labelStyle}>
                                <span style={labelTextStyle}>Материал</span>
                                <input
                                    type="text"
                                    style={inputStyle}
                                    placeholder="Например: хлопок 65%, полиэстер 35%"
                                    value={form.material ?? ''}
                                    onChange={(e) => { setForm((f) => ({ ...f, material: e.target.value })); setFormTouched(true); }}
                                />
                            </label>

                            <div style={{ display: 'flex', gap: 24 }}>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
                                    <input
                                        type="checkbox"
                                        checked={form.isActive}
                                        onChange={(e) => { setForm((f) => ({ ...f, isActive: e.target.checked })); setFormTouched(true); }}
                                    />
                                    <span style={labelTextStyle}>Активен</span>
                                </label>
                                <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
                                    <input
                                        type="checkbox"
                                        checked={form.isFeatured}
                                        onChange={(e) => { setForm((f) => ({ ...f, isFeatured: e.target.checked })); setFormTouched(true); }}
                                    />
                                    <span style={labelTextStyle}>Хит продаж</span>
                                </label>
                            </div>

                            <div>
                                <button
                                    className="rf-btn rf-btn-primary"
                                    disabled={saveMutation.isPending}
                                    onClick={() => saveMutation.mutate(form)}
                                >
                                    {saveMutation.isPending ? 'Сохранение…' : '✓ Сохранить'}
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Attributes card */}
                    <div className="rf-card" style={{ overflow: 'hidden' }}>
                        <div className="rf-card-header"><h3>Характеристики</h3></div>

                        {(product.attributes as AttributeRow[])?.length > 0 ? (
                            <div className="rf-admin-table-wrap">
                                <table className="rf-admin-table">
                                    <thead>
                                        <tr>
                                            <th>Название</th>
                                            <th>Значение</th>
                                            <th style={{ width: 100 }}></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {(product.attributes as AttributeRow[]).map((attr) => (
                                            <tr key={attr.id}>
                                                <td>{attr.attributeName}</td>
                                                <td>{attr.attributeValue}</td>
                                                <td>
                                                    <div style={{ display: 'flex', gap: 4 }}>
                                                        <button
                                                            className="rf-btn rf-btn-sm rf-btn-ghost"
                                                            style={{ height: 26, padding: '0 8px', fontSize: 12 }}
                                                            onClick={() => {
                                                                setEditingAttrId(attr.id);
                                                                setAttrForm({ attributeName: attr.attributeName, attributeValue: attr.attributeValue });
                                                            }}
                                                        >
                                                            Изм.
                                                        </button>
                                                        <button
                                                            className="rf-btn rf-btn-sm rf-btn-ghost"
                                                            style={{ height: 26, padding: '0 8px', fontSize: 12, color: 'var(--brand-red)' }}
                                                            onClick={() => {
                                                                if (window.confirm('Удалить характеристику?')) {
                                                                    deleteAttrMutation.mutate(attr.id);
                                                                }
                                                            }}
                                                        >
                                                            ✕
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <div style={{ padding: '16px 22px', color: 'var(--ink-3)', fontSize: 13 }}>Нет характеристик</div>
                        )}

                        <div style={{ padding: '14px 22px', borderTop: '1px solid var(--line-1)' }}>
                            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', marginBottom: 10, textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                                {editingAttrId ? 'Редактировать характеристику' : 'Добавить характеристику'}
                            </div>
                            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                                <input
                                    type="text"
                                    style={{ ...inputStyle, width: 200 }}
                                    placeholder="Название"
                                    value={attrForm.attributeName}
                                    onChange={(e) => setAttrForm((f) => ({ ...f, attributeName: e.target.value }))}
                                />
                                <input
                                    type="text"
                                    style={{ ...inputStyle, width: 200 }}
                                    placeholder="Значение"
                                    value={attrForm.attributeValue}
                                    onChange={(e) => setAttrForm((f) => ({ ...f, attributeValue: e.target.value }))}
                                    onKeyDown={(e) => { if (e.key === 'Enter') handleAttrSubmit(); }}
                                />
                                <button
                                    className="rf-btn rf-btn-sm rf-btn-primary"
                                    disabled={addAttrMutation.isPending || updateAttrMutation.isPending}
                                    onClick={handleAttrSubmit}
                                >
                                    {editingAttrId ? 'Сохранить' : '+ Добавить'}
                                </button>
                                {editingAttrId && (
                                    <button
                                        className="rf-btn rf-btn-sm rf-btn-quiet"
                                        onClick={() => { setEditingAttrId(null); setAttrForm({ attributeName: '', attributeValue: '' }); }}
                                    >
                                        Отмена
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>

                </div>

                {/* Right column: images + variants */}
                <div style={{ flex: '0 0 360px' }}>
                    <div className="rf-card" style={{ overflow: 'hidden' }}>
                        <div className="rf-card-header"><h3>Изображения</h3></div>
                        <div className="rf-card-body">
                            <input
                                ref={fileInputRef}
                                type="file"
                                accept="image/*"
                                style={{ display: 'none' }}
                                onChange={handleFileChange}
                            />
                            <button
                                className="rf-btn rf-btn-quiet"
                                style={{ width: '100%', marginBottom: 16 }}
                                disabled={uploadMutation.isPending}
                                onClick={() => fileInputRef.current?.click()}
                            >
                                {uploadMutation.isPending ? 'Загрузка…' : '↑ Загрузить изображение'}
                            </button>

                            {sortedImages.length === 0 ? (
                                <div style={{ textAlign: 'center', color: 'var(--ink-3)', fontSize: 13, padding: '20px 0' }}>
                                    Нет изображений
                                </div>
                            ) : (
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                    {sortedImages.map((img: ProductImage) => (
                                        <div
                                            key={img.id}
                                            style={{
                                                display: 'flex', alignItems: 'center', gap: 10,
                                                padding: '8px 10px', borderRadius: 'var(--r-3)',
                                                border: `1px solid ${img.isPrimary ? 'var(--brand-red)' : 'var(--line-1)'}`,
                                                background: img.isPrimary ? 'var(--red-tint)' : 'var(--surface)',
                                            }}
                                        >
                                            <img
                                                src={img.fileUrl}
                                                alt=""
                                                style={{ width: 52, height: 52, objectFit: 'contain', borderRadius: 4, flexShrink: 0, background: '#fff' }}
                                            />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>{img.contentType}</div>
                                                <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                                                    {img.width}×{img.height} · {Math.round((img.fileSize || 0) / 1024)} КБ
                                                </div>
                                                {img.isPrimary && (
                                                    <span className="rf-badge rf-badge-red" style={{ marginTop: 2 }}>Главное</span>
                                                )}
                                            </div>
                                            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                                                {!img.isPrimary && (
                                                    <button
                                                        className="rf-btn rf-btn-sm rf-btn-ghost"
                                                        style={{ height: 26, padding: '0 8px', fontSize: 11 }}
                                                        title="Сделать главным"
                                                        onClick={() => setPrimaryMutation.mutate(img.id)}
                                                    >
                                                        ★
                                                    </button>
                                                )}
                                                <button
                                                    className="rf-btn rf-btn-sm rf-btn-ghost"
                                                    style={{ height: 26, padding: '0 8px', fontSize: 11, color: 'var(--brand-red)' }}
                                                    title="Удалить"
                                                    onClick={() => {
                                                        if (window.confirm('Удалить изображение?')) {
                                                            deleteImageMutation.mutate(img.id);
                                                        }
                                                    }}
                                                >
                                                    ✕
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Parent product card */}
                    <div className="rf-card" style={{ overflow: 'visible', marginTop: 16 }}>
                        <div className="rf-card-header"><h3>Группировка вариантов</h3></div>
                        <div className="rf-card-body" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                            {product.isVariantChild && product.parentProductId ? (
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 10px', background: 'var(--brand-green-soft)', borderRadius: 'var(--r-2)' }}>
                                    <span style={{ fontSize: 13, color: 'var(--brand-green)', fontWeight: 500 }}>
                                        Дочерний вариант · ID родителя: {product.parentProductId}
                                    </span>
                                    <button
                                        className="rf-btn rf-btn-sm rf-btn-ghost"
                                        style={{ height: 24, padding: '0 8px', fontSize: 12, color: 'var(--brand-red)', flexShrink: 0 }}
                                        disabled={setParentMutation.isPending}
                                        onClick={() => { if (window.confirm('Снять привязку к родительскому товару?')) setParentMutation.mutate(null); }}
                                    >
                                        Снять
                                    </button>
                                </div>
                            ) : (
                                <div style={{ fontSize: 13, color: 'var(--ink-3)', marginBottom: 4 }}>
                                    Найдите родительский товар чтобы сделать этот товар его вариантом
                                </div>
                            )}

                            <div style={{ position: 'relative' }}>
                                <input
                                    type="text"
                                    style={{ ...inputStyle, width: '100%' }}
                                    placeholder="Найти родительский товар..."
                                    value={parentSearch}
                                    onChange={(e) => handleParentSearch(e.target.value)}
                                />
                                {parentSearchResults.length > 0 && (
                                    <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, zIndex: 10, background: 'var(--surface)', border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)', boxShadow: '0 4px 12px rgba(0,0,0,.08)', maxHeight: 200, overflowY: 'auto' }}>
                                        {parentSearchResults.map(p => (
                                            <div
                                                key={p.id}
                                                onClick={() => {
                                                    if (window.confirm(`Назначить родителем: «${p.name}»?`)) {
                                                        setParentMutation.mutate(p.id);
                                                    }
                                                }}
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
                        </div>
                    </div>

                    {/* Variants card */}
                    {product.variants && product.variants.length > 0 && (
                        <div className="rf-card" style={{ overflow: 'hidden', marginTop: 16 }}>
                            <div className="rf-card-header"><h3>Варианты ({product.variants.length})</h3></div>
                            <div className="rf-admin-table-wrap">
                                <table className="rf-admin-table">
                                    <thead>
                                        <tr>
                                            <th>Параметры</th>
                                            <th style={{ textAlign: 'right' }}>Остаток</th>
                                            <th style={{ textAlign: 'center', width: 60 }}>Акт.</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {product.variants.map((v) => {
                                            const ATTR_ORDER = ['Размер', 'Рост'];
                                            const attrParts = v.attributes
                                                ? ATTR_ORDER.filter(k => v.attributes![k])
                                                : [];
                                            const inStock = v.stockQuantity > 0;
                                            return (
                                                <tr key={v.id}>
                                                    <td style={{ fontWeight: 500, lineHeight: 1.5 }}>
                                                        {attrParts.length > 0
                                                            ? attrParts.map((k, i) => (
                                                                <span key={k}>
                                                                    {i > 0 && <br />}
                                                                    <span style={{ color: 'var(--ink-3)', fontSize: 12, fontWeight: 400 }}>{k}: </span>
                                                                    {v.attributes![k]}
                                                                </span>
                                                            ))
                                                            : (v.attributes ? Object.values(v.attributes).join(' / ') : '—')
                                                        }
                                                    </td>
                                                    <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', color: inStock ? 'var(--brand-green)' : 'var(--brand-red)', fontWeight: 500 }}>
                                                        {v.stockQuantity}
                                                    </td>
                                                    <td style={{ textAlign: 'center' }}>
                                                        <span className={`rf-badge ${v.isActive ? 'rf-badge-success' : 'rf-badge-neutral'}`}>
                                                            {v.isActive ? 'да' : 'нет'}
                                                        </span>
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default AdminProductEditPage;
