import { useState, useEffect, useRef } from 'react';
import { Spin } from 'antd';
import { searchProducts, getProducts } from '@/api/products';
import type { Product } from '@/types/product';
import type { SlideProduct, SlideProductFields } from '@/types/slider';
import { defaultProductFields } from '@/types/slider';

const formatPrice = (p: number) =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p);

// ── Icons ─────────────────────────────────────────────────────
const SearchIcon = () => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="7"/><path d="M21 21l-4.35-4.35"/>
    </svg>
);
const CloseIcon = () => (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <path d="M18 6 6 18M6 6l12 12"/>
    </svg>
);
const CheckIcon = () => (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12"/>
    </svg>
);
const ImgPlaceholder = () => (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.3 }}>
        <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
        <polyline points="21 15 16 10 5 21"/>
    </svg>
);
const ArrLeft = () => (
    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M19 12H5M12 5l-7 7 7 7"/>
    </svg>
);

// ── Checkbox row ──────────────────────────────────────────────
const CheckRow = ({ checked, onChange, children }: { checked: boolean; onChange: (v: boolean) => void; children: React.ReactNode }) => (
    <div
        onClick={() => onChange(!checked)}
        style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '8px 10px', borderRadius: 'var(--r-3)',
            cursor: 'pointer',
            background: checked ? 'var(--red-tint)' : 'transparent',
            border: `1px solid ${checked ? 'var(--brand-red)' : 'var(--line-1)'}`,
            transition: 'background .1s, border-color .1s',
        }}
    >
        <div style={{
            width: 18, height: 18, borderRadius: 4, flexShrink: 0,
            background: checked ? 'var(--brand-red)' : 'var(--surface)',
            border: `1.5px solid ${checked ? 'var(--brand-red)' : 'var(--line-2)'}`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            color: '#fff', transition: 'background .1s',
        }}>
            {checked && <CheckIcon />}
        </div>
        <span style={{ fontSize: 13, color: 'var(--ink-1)', userSelect: 'none' }}>{children}</span>
    </div>
);

// ── Шаг 2: настройка полей одного товара ─────────────────────
interface FieldsStepProps {
    product: Product;
    fields: SlideProductFields;
    onChange: (f: SlideProductFields) => void;
    onBack: () => void;
    onNext: () => void;
    currentIndex: number;
    total: number;
}

const FieldsStep = ({ product, fields, onChange, onBack, onNext, currentIndex, total }: FieldsStepProps) => {
    const set = <K extends keyof SlideProductFields>(key: K, value: SlideProductFields[K]) =>
        onChange({ ...fields, [key]: value });

    const sortedImages = [...(product.images ?? [])].sort((a, b) => {
        if (a.isPrimary && !b.isPrimary) return -1;
        if (!a.isPrimary && b.isPrimary) return 1;
        return a.displayOrder - b.displayOrder;
    });

    return (
        <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Header */}
            <div style={{ padding: '14px 20px 12px', borderBottom: '1px solid var(--line-1)', display: 'flex', alignItems: 'center', gap: 12 }}>
                <button onClick={onBack} style={{
                    width: 30, height: 30, border: '1px solid var(--line-1)', borderRadius: 'var(--r-2)',
                    background: 'transparent', cursor: 'pointer', color: 'var(--ink-2)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                    <ArrLeft />
                </button>
                <div style={{ flex: 1 }}>
                    <div style={{ fontWeight: 600, fontSize: 14, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {product.name}
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        Настройте поля · товар {currentIndex + 1} из {total}
                    </div>
                </div>
            </div>

            {/* Content */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '14px 20px', display: 'flex', gap: 20 }}>
                {/* Left: fields */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 4 }}>
                        Показывать на слайде
                    </div>
                    <CheckRow checked={fields.showName} onChange={(v) => set('showName', v)}>
                        Название товара
                    </CheckRow>
                    <CheckRow checked={fields.showPrice} onChange={(v) => set('showPrice', v)}>
                        Цена
                    </CheckRow>
                    <CheckRow
                        checked={fields.showShortDescription}
                        onChange={(v) => set('showShortDescription', v)}
                    >
                        <span>
                            Краткое описание
                            {!product.shortDescription && (
                                <span style={{ fontSize: 11, color: 'var(--ink-4)', marginLeft: 6 }}>(не заполнено)</span>
                            )}
                        </span>
                    </CheckRow>
                    <CheckRow
                        checked={fields.showDescription}
                        onChange={(v) => set('showDescription', v)}
                    >
                        <span>
                            Полное описание
                            {!product.description && (
                                <span style={{ fontSize: 11, color: 'var(--ink-4)', marginLeft: 6 }}>(не заполнено)</span>
                            )}
                        </span>
                    </CheckRow>

                    {/* Photo selection */}
                    {sortedImages.length > 0 && (
                        <div style={{ marginTop: 8 }}>
                            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>
                                Фото на слайде ({sortedImages.length})
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 6 }}>
                                {sortedImages.map((img, i) => (
                                    <div
                                        key={i}
                                        onClick={() => set('selectedImageIndex', i)}
                                        style={{
                                            position: 'relative', aspectRatio: '1',
                                            borderRadius: 'var(--r-2)', overflow: 'hidden',
                                            border: `2px solid ${fields.selectedImageIndex === i ? 'var(--brand-red)' : 'var(--line-1)'}`,
                                            cursor: 'pointer', background: 'var(--surface-2)',
                                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                                        }}
                                    >
                                        <img src={img.fileUrl} alt="" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                        {fields.selectedImageIndex === i && (
                                            <div style={{
                                                position: 'absolute', top: 3, right: 3,
                                                width: 16, height: 16, borderRadius: '50%',
                                                background: 'var(--brand-red)',
                                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                            }}>
                                                <CheckIcon />
                                            </div>
                                        )}
                                        {img.isPrimary && (
                                            <div style={{
                                                position: 'absolute', bottom: 2, left: 2,
                                                fontSize: 8, fontWeight: 700,
                                                background: 'rgba(0,0,0,.55)', color: '#fff',
                                                padding: '1px 4px', borderRadius: 2,
                                            }}>
                                                осн.
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                    {sortedImages.length === 0 && (
                        <div style={{ fontSize: 12, color: 'var(--ink-4)', padding: '8px 0' }}>
                            У товара нет фотографий
                        </div>
                    )}
                </div>

                {/* Right: preview */}
                <div style={{ width: 160, flexShrink: 0 }}>
                    <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 8 }}>
                        Превью
                    </div>
                    <div style={{
                        background: 'rgba(255,255,255,.12)',
                        border: '1px solid rgba(255,255,255,.2)',
                        borderRadius: 'var(--r-3)', padding: '8px',
                        background: 'var(--surface-2)',
                        display: 'flex', flexDirection: 'column', gap: 6,
                    }}>
                        {/* Image preview */}
                        <div style={{
                            width: '100%', aspectRatio: '1', borderRadius: 'var(--r-2)',
                            background: 'var(--surface)', overflow: 'hidden',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                            {sortedImages[fields.selectedImageIndex]
                                ? <img src={sortedImages[fields.selectedImageIndex].fileUrl} alt="" style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                : <ImgPlaceholder />
                            }
                        </div>
                        {fields.showName && (
                            <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-1)', lineHeight: 1.3 }}>
                                {product.name.length > 40 ? product.name.slice(0, 40) + '…' : product.name}
                            </div>
                        )}
                        {fields.showShortDescription && product.shortDescription && (
                            <div style={{ fontSize: 10, color: 'var(--ink-3)', lineHeight: 1.3 }}>
                                {product.shortDescription.slice(0, 60)}{product.shortDescription.length > 60 ? '…' : ''}
                            </div>
                        )}
                        {fields.showDescription && product.description && !fields.showShortDescription && (
                            <div style={{ fontSize: 10, color: 'var(--ink-3)', lineHeight: 1.3 }}>
                                {product.description.slice(0, 60)}{product.description.length > 60 ? '…' : ''}
                            </div>
                        )}
                        {fields.showPrice && (
                            <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--brand-red)', fontVariantNumeric: 'tabular-nums' }}>
                                {formatPrice(product.price)}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* Footer */}
            <div style={{
                padding: '10px 20px', borderTop: '1px solid var(--line-1)',
                display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10,
            }}>
                <button onClick={onBack} style={{
                    height: 34, padding: '0 16px', border: '1px solid var(--line-2)',
                    borderRadius: 'var(--r-3)', background: 'transparent', cursor: 'pointer',
                    fontSize: 13, color: 'var(--ink-2)', fontFamily: 'var(--font-body)',
                }}>
                    ← Назад
                </button>
                <button onClick={onNext} style={{
                    height: 34, padding: '0 20px', border: 0,
                    borderRadius: 'var(--r-3)', background: 'var(--brand-red)',
                    color: '#fff', fontWeight: 600, fontSize: 13,
                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                }}>
                    {currentIndex + 1 < total ? 'Следующий товар →' : 'Готово'}
                </button>
            </div>
        </div>
    );
};

// ── Main component ────────────────────────────────────────────
type Step = 'select' | 'fields';

interface Props {
    open: boolean;
    alreadySelected: number[];
    onConfirm: (products: SlideProduct[]) => void;
    onClose: () => void;
}

const ProductPickerModal = ({ open, alreadySelected, onConfirm, onClose }: Props) => {
    const [step, setStep] = useState<Step>('select');
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<Product[]>([]);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState<Set<number>>(new Set(alreadySelected));
    const [selectedProducts, setSelectedProducts] = useState<Map<number, Product>>(new Map());

    // fields настройка: productId → SlideProductFields
    const [fieldsMap, setFieldsMap] = useState<Map<number, SlideProductFields>>(new Map());
    const [fieldsQueue, setFieldsQueue] = useState<Product[]>([]);
    const [fieldsQueueIdx, setFieldsQueueIdx] = useState(0);

    const inputRef = useRef<HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        if (open) {
            setStep('select');
            setQuery('');
            setSelected(new Set(alreadySelected));
            setSelectedProducts(new Map());
            setFieldsMap(new Map());
            setTimeout(() => inputRef.current?.focus(), 80);
            loadInitial();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [open]);

    const loadInitial = async () => {
        setLoading(true);
        try {
            const page = await getProducts({ page: 0, size: 30, sort: 'createdAt,desc' });
            setResults(page.content);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!open) return;
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (!query.trim()) { loadInitial(); return; }
        debounceRef.current = setTimeout(async () => {
            setLoading(true);
            try {
                const data = await searchProducts(query.trim());
                setResults(data);
            } finally {
                setLoading(false);
            }
        }, 280);
        return () => { if (debounceRef.current) clearTimeout(debounceRef.current); };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [query, open]);

    const toggle = (product: Product) => {
        setSelected((prev) => {
            const next = new Set(prev);
            if (next.has(product.id)) {
                next.delete(product.id);
                setSelectedProducts((m) => { const nm = new Map(m); nm.delete(product.id); return nm; });
            } else {
                next.add(product.id);
                setSelectedProducts((m) => new Map(m).set(product.id, product));
            }
            return next;
        });
    };

    // Переход к настройке полей — только для новых товаров
    const handleGoToFields = () => {
        const newIds = Array.from(selected).filter((id) => !alreadySelected.includes(id));
        const newProds = newIds
            .map((id) => selectedProducts.get(id) ?? results.find((r) => r.id === id))
            .filter(Boolean) as Product[];
        if (newProds.length === 0) {
            // нет новых — просто подтверждаем (пустой список — сигнал что ничего нового)
            onConfirm([]);
            return;
        }
        setFieldsQueue(newProds);
        setFieldsQueueIdx(0);
        setFieldsMap(new Map(newProds.map((p) => [p.id, defaultProductFields()])));
        setStep('fields');
    };

    const handleFieldsNext = () => {
        if (fieldsQueueIdx + 1 < fieldsQueue.length) {
            setFieldsQueueIdx((i) => i + 1);
        } else {
            // Все настроены — собираем результат
            const picks: SlideProduct[] = fieldsQueue.map((p) => {
                const f = fieldsMap.get(p.id) ?? defaultProductFields();
                const sortedImages = [...(p.images ?? [])].sort((a, b) => {
                    if (a.isPrimary && !b.isPrimary) return -1;
                    if (!a.isPrimary && b.isPrimary) return 1;
                    return a.displayOrder - b.displayOrder;
                });
                const selectedImg = sortedImages[f.selectedImageIndex] ?? sortedImages[0] ?? null;
                return {
                    id: p.id,
                    name: p.name,
                    sku: p.sku,
                    price: p.price,
                    shortDescription: p.shortDescription,
                    description: p.description,
                    images: sortedImages.map((img) => ({ fileUrl: img.fileUrl, isPrimary: img.isPrimary, displayOrder: img.displayOrder })),
                    imageUrl: selectedImg?.fileUrl ?? null,
                    fields: f,
                };
            });
            onConfirm(picks);
        }
    };

    const newCount = Array.from(selected).filter((id) => !alreadySelected.includes(id)).length;

    if (!open) return null;

    // ── Step: fields ──
    if (step === 'fields') {
        const currentProduct = fieldsQueue[fieldsQueueIdx];
        return (
            <div style={{
                position: 'fixed', inset: 0, zIndex: 1100,
                background: 'rgba(0,0,0,.5)', display: 'flex',
                alignItems: 'center', justifyContent: 'center',
            }}
                onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
            >
                <div style={{
                    background: 'var(--surface)', borderRadius: 'var(--r-5)',
                    width: 680, maxHeight: '82vh', display: 'flex', flexDirection: 'column',
                    boxShadow: 'var(--shadow-4)',
                }}>
                    <FieldsStep
                        product={currentProduct}
                        fields={fieldsMap.get(currentProduct.id) ?? defaultProductFields()}
                        onChange={(f) => setFieldsMap((m) => new Map(m).set(currentProduct.id, f))}
                        onBack={() => { setStep('select'); setFieldsQueueIdx(0); }}
                        onNext={handleFieldsNext}
                        currentIndex={fieldsQueueIdx}
                        total={fieldsQueue.length}
                    />
                </div>
            </div>
        );
    }

    // ── Step: select ──
    return (
        <div style={{
            position: 'fixed', inset: 0, zIndex: 1100,
            background: 'rgba(0,0,0,.5)', display: 'flex',
            alignItems: 'center', justifyContent: 'center',
        }}
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
        >
            <div style={{
                background: 'var(--surface)', borderRadius: 'var(--r-5)',
                width: 680, maxHeight: '80vh', display: 'flex', flexDirection: 'column',
                boxShadow: 'var(--shadow-4)',
            }}>
                {/* Header */}
                <div style={{
                    padding: '16px 20px 12px', borderBottom: '1px solid var(--line-1)',
                    display: 'flex', alignItems: 'center', gap: 12,
                }}>
                    <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 600, fontSize: 15, color: 'var(--ink-1)' }}>Выбрать товары</div>
                        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 2 }}>
                            {selected.size > 0 ? `Выбрано: ${selected.size}` : 'Найдите и отметьте нужные товары'}
                        </div>
                    </div>
                    <button onClick={onClose} style={{
                        width: 32, height: 32, border: 0, borderRadius: 'var(--r-3)',
                        background: 'transparent', cursor: 'pointer', color: 'var(--ink-3)',
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                    }}>
                        <CloseIcon />
                    </button>
                </div>

                {/* Search */}
                <div style={{ padding: '10px 20px', borderBottom: '1px solid var(--line-1)' }}>
                    <div style={{
                        display: 'flex', alignItems: 'center', gap: 10,
                        background: 'var(--surface-2)', border: '1px solid var(--line-1)',
                        borderRadius: 'var(--r-3)', padding: '0 12px', height: 36,
                    }}>
                        <SearchIcon />
                        <input
                            ref={inputRef}
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            placeholder="Поиск по названию или артикулу…"
                            style={{
                                flex: 1, border: 0, background: 'transparent', outline: 'none',
                                fontSize: 13, color: 'var(--ink-1)', fontFamily: 'var(--font-body)',
                            }}
                        />
                        {loading && <Spin size="small" />}
                    </div>
                </div>

                {/* Results */}
                <div style={{ flex: 1, overflowY: 'auto', padding: '6px 12px' }}>
                    {results.length === 0 && !loading && (
                        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--ink-3)', fontSize: 13 }}>
                            Ничего не найдено
                        </div>
                    )}
                    {results.map((product) => {
                        const isSelected = selected.has(product.id);
                        const isAlready = alreadySelected.includes(product.id);
                        const img = product.images?.length
                            ? (product.images.find((i) => i.isPrimary) ?? product.images[0]).fileUrl
                            : null;
                        return (
                            <div
                                key={product.id}
                                onClick={() => toggle(product)}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 12,
                                    padding: '7px 10px', borderRadius: 'var(--r-3)',
                                    cursor: 'pointer',
                                    background: isSelected ? 'var(--red-tint)' : 'transparent',
                                    border: `1px solid ${isSelected ? 'var(--brand-red)' : 'transparent'}`,
                                    marginBottom: 3,
                                    transition: 'background .1s, border-color .1s',
                                }}
                            >
                                <div style={{
                                    width: 20, height: 20, borderRadius: 5, flexShrink: 0,
                                    background: isSelected ? 'var(--brand-red)' : 'var(--surface)',
                                    border: `1.5px solid ${isSelected ? 'var(--brand-red)' : 'var(--line-2)'}`,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    color: '#fff',
                                }}>
                                    {isSelected && <CheckIcon />}
                                </div>
                                <div style={{
                                    width: 42, height: 42, borderRadius: 'var(--r-2)', flexShrink: 0,
                                    background: 'var(--surface-2)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden',
                                }}>
                                    {img
                                        ? <img src={img} alt={product.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                        : <ImgPlaceholder />
                                    }
                                </div>
                                <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--ink-1)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                        {product.name}
                                    </div>
                                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, display: 'flex', gap: 10 }}>
                                        {product.sku && <span style={{ fontFamily: 'var(--font-mono)' }}>{product.sku}</span>}
                                        <span>{product.categoryName ?? '—'}</span>
                                        {product.images?.length > 1 && (
                                            <span>{product.images.length} фото</span>
                                        )}
                                    </div>
                                </div>
                                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 3 }}>
                                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', fontVariantNumeric: 'tabular-nums' }}>
                                        {formatPrice(product.price)}
                                    </div>
                                    {isAlready && (
                                        <div style={{ fontSize: 10, color: 'var(--ink-4)', background: 'var(--surface-2)', padding: '1px 6px', borderRadius: 'var(--r-full)' }}>
                                            уже добавлен
                                        </div>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>

                {/* Footer */}
                <div style={{
                    padding: '10px 20px', borderTop: '1px solid var(--line-1)',
                    display: 'flex', alignItems: 'center', gap: 10,
                }}>
                    <span style={{ flex: 1, fontSize: 12, color: 'var(--ink-3)' }}>
                        {newCount > 0
                            ? `${newCount} новых товаров — далее настройка полей`
                            : selected.size > 0 ? 'Все товары уже добавлены' : 'Ни одного товара не выбрано'}
                    </span>
                    <button onClick={onClose} style={{
                        height: 34, padding: '0 16px', border: '1px solid var(--line-2)',
                        borderRadius: 'var(--r-3)', background: 'transparent', cursor: 'pointer',
                        fontSize: 13, color: 'var(--ink-2)', fontFamily: 'var(--font-body)',
                    }}>
                        Отмена
                    </button>
                    <button
                        onClick={handleGoToFields}
                        disabled={newCount === 0}
                        style={{
                            height: 34, padding: '0 18px', border: 0,
                            borderRadius: 'var(--r-3)',
                            background: newCount > 0 ? 'var(--brand-red)' : 'var(--surface-3)',
                            color: newCount > 0 ? '#fff' : 'var(--ink-4)',
                            cursor: newCount > 0 ? 'pointer' : 'default',
                            fontSize: 13, fontWeight: 600, fontFamily: 'var(--font-body)',
                        }}
                    >
                        Далее: поля →
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ProductPickerModal;
