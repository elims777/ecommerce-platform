import { useState, useEffect, useRef } from 'react';
import { Spin } from 'antd';
import { searchProducts, getProducts } from '@/api/products';
import type { Product } from '@/types/product';
import type { SlideProduct } from '@/types/slider';

const formatPrice = (p: number) =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p);

const getPrimaryImage = (product: Product): string | null => {
    if (!product.images?.length) return null;
    return (product.images.find((i) => i.isPrimary) ?? product.images[0]).fileUrl;
};

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
    <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.3 }}>
        <rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/>
        <polyline points="21 15 16 10 5 21"/>
    </svg>
);

interface Props {
    open: boolean;
    alreadySelected: number[];
    onConfirm: (products: SlideProduct[]) => void;
    onClose: () => void;
}

const ProductPickerModal = ({ open, alreadySelected, onConfirm, onClose }: Props) => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<Product[]>([]);
    const [loading, setLoading] = useState(false);
    const [selected, setSelected] = useState<Set<number>>(new Set(alreadySelected));
    const [selectedProducts, setSelectedProducts] = useState<Map<number, Product>>(new Map());
    const inputRef = useRef<HTMLInputElement>(null);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        if (open) {
            setQuery('');
            setSelected(new Set(alreadySelected));
            setSelectedProducts(new Map());
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

    const handleConfirm = () => {
        const picks: SlideProduct[] = Array.from(selected).map((id) => {
            const p = selectedProducts.get(id) ?? results.find((r) => r.id === id);
            if (!p) return null;
            return { id: p.id, name: p.name, sku: p.sku, price: p.price, imageUrl: getPrimaryImage(p) };
        }).filter(Boolean) as SlideProduct[];
        onConfirm(picks);
    };

    const newCount = selected.size - alreadySelected.filter((id) => selected.has(id)).length;

    if (!open) return null;

    return (
        <div style={{
            position: 'fixed', inset: 0, zIndex: 1100,
            background: 'rgba(0,0,0,.45)', display: 'flex',
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
                    padding: '18px 20px 14px', borderBottom: '1px solid var(--line-1)',
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
                <div style={{ padding: '12px 20px', borderBottom: '1px solid var(--line-1)' }}>
                    <div style={{
                        display: 'flex', alignItems: 'center', gap: 10,
                        background: 'var(--surface-2)', border: '1px solid var(--line-1)',
                        borderRadius: 'var(--r-3)', padding: '0 12px', height: 38,
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
                <div style={{ flex: 1, overflowY: 'auto', padding: '8px 12px' }}>
                    {results.length === 0 && !loading && (
                        <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--ink-3)', fontSize: 13 }}>
                            Ничего не найдено
                        </div>
                    )}
                    {results.map((product) => {
                        const isSelected = selected.has(product.id);
                        const img = getPrimaryImage(product);
                        return (
                            <div
                                key={product.id}
                                onClick={() => toggle(product)}
                                style={{
                                    display: 'flex', alignItems: 'center', gap: 12,
                                    padding: '8px 10px', borderRadius: 'var(--r-3)',
                                    cursor: 'pointer',
                                    background: isSelected ? 'var(--red-tint)' : 'transparent',
                                    border: `1px solid ${isSelected ? 'var(--brand-red)' : 'transparent'}`,
                                    marginBottom: 4,
                                    transition: 'background .1s, border-color .1s',
                                }}
                            >
                                {/* Checkbox */}
                                <div style={{
                                    width: 20, height: 20, borderRadius: 5, flexShrink: 0,
                                    background: isSelected ? 'var(--brand-red)' : 'var(--surface)',
                                    border: `1.5px solid ${isSelected ? 'var(--brand-red)' : 'var(--line-2)'}`,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    transition: 'background .1s, border-color .1s',
                                    color: '#fff',
                                }}>
                                    {isSelected && <CheckIcon />}
                                </div>

                                {/* Image */}
                                <div style={{
                                    width: 44, height: 44, borderRadius: 'var(--r-2)', flexShrink: 0,
                                    background: 'var(--surface-2)',
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    overflow: 'hidden',
                                }}>
                                    {img
                                        ? <img src={img} alt={product.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                        : <ImgPlaceholder />
                                    }
                                </div>

                                {/* Info */}
                                <div style={{ flex: 1, minWidth: 0 }}>
                                    <div style={{
                                        fontSize: 13, fontWeight: 500, color: 'var(--ink-1)',
                                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                                    }}>
                                        {product.name}
                                    </div>
                                    <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2, display: 'flex', gap: 10 }}>
                                        {product.sku && <span style={{ fontFamily: 'var(--font-mono)' }}>{product.sku}</span>}
                                        <span>{product.categoryName ?? '—'}</span>
                                    </div>
                                </div>

                                {/* Price */}
                                <div style={{
                                    fontSize: 13, fontWeight: 600, color: 'var(--ink-1)',
                                    fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap',
                                }}>
                                    {formatPrice(product.price)}
                                </div>
                            </div>
                        );
                    })}
                </div>

                {/* Footer */}
                <div style={{
                    padding: '12px 20px', borderTop: '1px solid var(--line-1)',
                    display: 'flex', alignItems: 'center', gap: 10,
                }}>
                    <span style={{ flex: 1, fontSize: 12, color: 'var(--ink-3)' }}>
                        {selected.size > 0 ? `Выбрано ${selected.size} товаров` : 'Ни одного товара не выбрано'}
                    </span>
                    <button onClick={onClose} style={{
                        height: 34, padding: '0 16px', border: '1px solid var(--line-2)',
                        borderRadius: 'var(--r-3)', background: 'transparent', cursor: 'pointer',
                        fontSize: 13, color: 'var(--ink-2)', fontFamily: 'var(--font-body)',
                    }}>
                        Отмена
                    </button>
                    <button
                        onClick={handleConfirm}
                        disabled={selected.size === 0}
                        style={{
                            height: 34, padding: '0 18px', border: 0,
                            borderRadius: 'var(--r-3)',
                            background: selected.size > 0 ? 'var(--brand-red)' : 'var(--surface-3)',
                            color: selected.size > 0 ? '#fff' : 'var(--ink-4)',
                            cursor: selected.size > 0 ? 'pointer' : 'default',
                            fontSize: 13, fontWeight: 600, fontFamily: 'var(--font-body)',
                            transition: 'background .12s',
                        }}
                    >
                        {newCount > 0 ? `Добавить ${newCount}` : 'Применить'}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ProductPickerModal;
