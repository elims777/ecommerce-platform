import { useState, useMemo } from 'react';
import { Image, App, Skeleton } from 'antd';
import { ShoppingCartOutlined, ShoppingOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useParams, useNavigate } from 'react-router-dom';
import { getProductById } from '@/api/products';
import type { ProductImage, ProductVariant } from '@/types/product';
import { useCartStore } from '@/store/cartStore';
import { useDisplayPrice } from '@/utils/priceUtils';

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }).format(price);

const sortImages = (images: ProductImage[]): ProductImage[] =>
    [...images].sort((a, b) => {
        if (a.isPrimary && !b.isPrimary) return -1;
        if (!a.isPrimary && b.isPrimary) return 1;
        return a.displayOrder - b.displayOrder;
    });

// Извлекает уникальные значения атрибута из списка вариантов
const getAttributeValues = (variants: ProductVariant[], key: string): string[] =>
    [...new Set(variants.map(v => v.attributes?.[key]).filter(Boolean) as string[])];

// Находит вариант по выбранным атрибутам
const findVariant = (variants: ProductVariant[], selected: Record<string, string>): ProductVariant | null => {
    return variants.find(v =>
        v.isActive && Object.entries(selected).every(([k, val]) => v.attributes?.[k] === val)
    ) ?? null;
};

const ProductPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const [quantity, setQuantity] = useState(1);
    const [selectedAttrs, setSelectedAttrs] = useState<Record<string, string>>({});
    const addItem = useCartStore((state) => state.addItem);

    const { data: product, isLoading, isError } = useQuery({
        queryKey: ['product', id],
        queryFn: () => getProductById(Number(id)),
        enabled: !!id,
    });

    const hasVariants = (product?.variants?.length ?? 0) > 1;

    // Ключи атрибутов вариантов (размер, рост и т.д.)
    const attrKeys = useMemo(() => {
        if (!hasVariants || !product?.variants) return [];
        const keys = new Set<string>();
        product.variants.forEach(v => v.attributes && Object.keys(v.attributes).forEach(k => keys.add(k)));
        return [...keys];
    }, [product?.variants, hasVariants]);

    // Выбранный вариант
    const selectedVariant = useMemo(() => {
        if (!hasVariants || !product?.variants) return null;
        return findVariant(product.variants, selectedAttrs);
    }, [product?.variants, selectedAttrs, hasVariants]);

    // Цена: из выбранного варианта или с товара
    const activePrice = selectedVariant?.price ?? product?.price ?? 0;
    const activeWholesalePrice = selectedVariant?.wholesalePrice ?? product?.wholesalePrice ?? null;
    const activeStock = selectedVariant?.stockQuantity ?? product?.stockQuantity ?? 0;

    const displayPrice = useDisplayPrice({ price: activePrice, wholesalePrice: activeWholesalePrice });

    const handleAddToCart = async () => {
        if (!product) return;
        if (hasVariants && !selectedVariant) {
            messageApi.warning('Выберите все параметры товара');
            return;
        }
        try {
            await addItem(product.id, quantity, selectedVariant?.id ?? null);
            messageApi.success(`${product.name} (${quantity} шт.) добавлен в корзину`);
        } catch {
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    if (isLoading) {
        return (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr 280px', gap: 32, padding: '24px 0' }}>
                <Skeleton.Image active style={{ width: '100%', height: 340, borderRadius: 8 }} />
                <div>
                    <Skeleton active paragraph={{ rows: 6 }} title={{ width: '80%' }} />
                </div>
                <div style={{ background: 'var(--surface)', borderRadius: 'var(--r-5)', padding: 20, border: '1px solid var(--line-1)', height: 280 }}>
                    <Skeleton active paragraph={{ rows: 4 }} />
                </div>
            </div>
        );
    }

    if (isError || !product) {
        return (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <div style={{ fontFamily: 'var(--font-head)', fontSize: 20, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>Товар не найден</div>
                <button
                    onClick={() => navigate('/')}
                    style={{ display: 'inline-flex', alignItems: 'center', height: 'var(--btn-h-base)', padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                >
                    Вернуться в каталог
                </button>
            </div>
        );
    }

    const sortedImages = sortImages(product.images || []);
    const inStock = activeStock > 0;

    return (
        <div style={{ paddingTop: 20, paddingBottom: 60 }}>
            {/* Хлебные крошки */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 'var(--text-sm)', color: 'var(--ink-3)', marginBottom: 16 }}>
                <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Каталог</span>
                {product.categoryName && (
                    <>
                        <span style={{ opacity: 0.5 }}>›</span>
                        <span
                            onClick={() => navigate(`/?category=${product.categoryId}`)}
                            style={{ cursor: 'pointer', color: 'var(--ink-2)', fontWeight: 500 }}
                        >
                            {product.categoryName}
                        </span>
                    </>
                )}
                <span style={{ opacity: 0.5 }}>›</span>
                <span style={{ color: 'var(--ink-1)', fontWeight: 500 }}>{product.name}</span>
            </div>

            <button
                onClick={() => navigate(-1)}
                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 0', background: 'none', border: 'none', color: 'var(--brand-navy)', fontSize: 'var(--text-base)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)', marginBottom: 20 }}
            >
                <ArrowLeftOutlined /> Назад
            </button>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 320px', gap: 28 }}>
                {/* Галерея */}
                <div>
                    <div style={{ borderRadius: 'var(--r-5)', overflow: 'hidden', border: '1px solid var(--line-1)', background: 'var(--surface-2)', display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: 400 }}>
                        {sortedImages.length > 0 ? (
                            <Image.PreviewGroup>
                                <Image
                                    src={sortedImages[0].fileUrl}
                                    alt={sortedImages[0].altText || product.name}
                                    style={{ maxHeight: 420, objectFit: 'contain' }}
                                />
                            </Image.PreviewGroup>
                        ) : (
                            <ShoppingOutlined style={{ fontSize: 80, color: 'var(--ink-4)' }} />
                        )}
                    </div>
                    {sortedImages.length > 1 && (
                        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 8, marginTop: 10 }}>
                            {sortedImages.slice(1, 6).map((img, i) => (
                                <div key={img.id} style={{ height: 80, borderRadius: 'var(--r-3)', border: i === 0 ? '2px solid var(--brand-red)' : '1px solid var(--line-1)', overflow: 'hidden', background: 'var(--surface-2)' }}>
                                    <img src={img.fileUrl} alt={img.altText || product.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Описание */}
                <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                        {product.categoryName && (
                            <span style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--brand-navy)' }}>{product.categoryName}</span>
                        )}
                        {inStock ? (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 22, padding: '0 8px', borderRadius: 'var(--r-full)', fontSize: 'var(--text-sm)', fontWeight: 500, background: 'var(--brand-green-soft)', color: 'var(--brand-green)' }}>
                                <span style={{ width: 6, height: 6, borderRadius: 3, background: 'currentColor', flexShrink: 0 }} />
                                В наличии {activeStock} {product.unitOfMeasure || 'шт.'}
                            </span>
                        ) : (
                            <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: 22, padding: '0 8px', borderRadius: 'var(--r-full)', fontSize: 'var(--text-sm)', fontWeight: 500, background: 'var(--red-tint)', color: 'var(--brand-red)' }}>
                                <span style={{ width: 6, height: 6, borderRadius: 3, background: 'currentColor', flexShrink: 0 }} />
                                Нет в наличии
                            </span>
                        )}
                        {product.sku && (
                            <span style={{ marginLeft: 'auto', fontSize: 'var(--text-sm)', color: 'var(--ink-3)', fontFamily: 'var(--font-mono)' }}>
                                Арт. {product.sku}
                            </span>
                        )}
                    </div>

                    <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-4xl)', fontWeight: 600, letterSpacing: '-0.018em', lineHeight: 1.2, color: 'var(--ink-1)', marginBottom: 16 }}>
                        {product.name}
                    </h1>

                    {product.isFeatured && (
                        <span style={{ display: 'inline-flex', alignItems: 'center', height: 22, padding: '0 8px', borderRadius: 'var(--r-full)', fontSize: 'var(--text-sm)', fontWeight: 500, background: 'var(--brand-green-soft)', color: 'var(--brand-green)', marginBottom: 12 }}>
                            Хит продаж
                        </span>
                    )}

                    {product.shortDescription && (
                        <p style={{ fontSize: 'var(--text-md)', color: 'var(--ink-2)', lineHeight: 1.6, marginBottom: 20 }}>{product.shortDescription}</p>
                    )}

                    {/* Характеристики */}
                    {product.attributes && product.attributes.length > 0 && (
                        <div style={{ borderTop: '1px solid var(--line-1)', paddingTop: 20, marginBottom: 20 }}>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px 24px' }}>
                                {product.attributes.map((attr) => (
                                    <div key={attr.id} style={{ display: 'flex', fontSize: 'var(--text-base)', padding: '5px 0', borderBottom: '1px dashed var(--line-1)' }}>
                                        <span style={{ color: 'var(--ink-3)', flex: 1 }}>{attr.attributeName}</span>
                                        <span style={{ fontWeight: 500 }}>{attr.attributeValue}</span>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Полное описание */}
                    {product.description && (
                        <div style={{ borderTop: '1px solid var(--line-1)', paddingTop: 20 }}>
                            <div style={{ display: 'flex', gap: 0, borderBottom: '1px solid var(--line-1)', marginBottom: 16 }}>
                                <div style={{ padding: '10px 18px', fontSize: 'var(--text-base)', fontWeight: 600, color: 'var(--ink-1)', borderBottom: '2px solid var(--brand-red)', marginBottom: -1 }}>
                                    Описание
                                </div>
                            </div>
                            <p style={{ fontSize: 'var(--text-base)', color: 'var(--ink-2)', lineHeight: 1.6, whiteSpace: 'pre-wrap' }}>{product.description}</p>
                        </div>
                    )}
                </div>

                {/* Buy box */}
                <div>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', padding: 20, position: 'sticky', top: 20, background: 'var(--surface)' }}>
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 6 }}>
                            <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-6xl)', letterSpacing: '-0.02em', fontVariantNumeric: 'tabular-nums', color: 'var(--ink-1)' }}>
                                {formatPrice(displayPrice)}
                            </span>
                        </div>
                        <div style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)', marginBottom: hasVariants ? 16 : 20 }}>
                            за 1 {product.unitOfMeasure || 'шт.'} · НДС {product.vatRate ?? 20}% включён
                        </div>

                        {/* Селектор вариантов */}
                        {hasVariants && attrKeys.map(attrKey => (
                            <div key={attrKey} style={{ marginBottom: 14 }}>
                                <div style={{ fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--ink-2)', marginBottom: 6 }}>
                                    {attrKey}
                                    {selectedAttrs[attrKey] && (
                                        <span style={{ fontWeight: 400, color: 'var(--ink-1)', marginLeft: 6 }}>{selectedAttrs[attrKey]}</span>
                                    )}
                                </div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                                    {getAttributeValues(product.variants, attrKey).map(val => {
                                        const isSelected = selectedAttrs[attrKey] === val;
                                        const testAttrs = { ...selectedAttrs, [attrKey]: val };
                                        const available = findVariant(product.variants, testAttrs) !== null
                                            || attrKeys.filter(k => k !== attrKey).every(k => !selectedAttrs[k]);
                                        return (
                                            <button
                                                key={val}
                                                onClick={() => setSelectedAttrs(prev => ({ ...prev, [attrKey]: val }))}
                                                style={{
                                                    height: 32, padding: '0 12px',
                                                    border: isSelected ? '2px solid var(--brand-red)' : '1px solid var(--line-2)',
                                                    borderRadius: 'var(--r-3)',
                                                    background: isSelected ? 'var(--red-tint)' : 'var(--surface)',
                                                    color: isSelected ? 'var(--brand-red)' : available ? 'var(--ink-1)' : 'var(--ink-4)',
                                                    fontSize: 'var(--text-sm)', fontWeight: isSelected ? 600 : 400,
                                                    cursor: available ? 'pointer' : 'not-allowed',
                                                    fontFamily: 'var(--font-body)',
                                                    textDecoration: available ? 'none' : 'line-through',
                                                    opacity: available ? 1 : 0.5,
                                                    transition: 'border-color 0.1s, background 0.1s',
                                                }}
                                            >
                                                {val}
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>
                        ))}

                        {hasVariants && attrKeys.length > 0 && !selectedVariant && (
                            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)', marginBottom: 14 }}>
                                Выберите {attrKeys.join(' и ')}
                            </div>
                        )}

                        {inStock && (
                            <>
                                <div style={{ marginBottom: 14 }}>
                                    <label style={{ display: 'block', fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--ink-2)', marginBottom: 6 }}>Количество</label>
                                    <div style={{ display: 'flex', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)', height: 'var(--input-h-lg)', alignItems: 'center' }}>
                                        <button
                                            onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                                            style={{ width: 40, height: 42, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                        >
                                            −
                                        </button>
                                        <input
                                            type="number"
                                            min={1}
                                            max={activeStock}
                                            value={quantity}
                                            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
                                            style={{ flex: 1, textAlign: 'center', border: 0, background: 'transparent', fontSize: 'var(--text-xl)', fontWeight: 600, outline: 'none', fontFamily: 'var(--font-head)', color: 'var(--ink-1)', fontVariantNumeric: 'tabular-nums' }}
                                        />
                                        <button
                                            onClick={() => setQuantity((q) => Math.min(activeStock, q + 1))}
                                            style={{ width: 40, height: 42, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                        >
                                            +
                                        </button>
                                    </div>
                                </div>

                                <button
                                    onClick={handleAddToCart}
                                    style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, width: '100%', height: 'var(--btn-h-xl)', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-lg)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)', transition: 'background 0.12s', marginBottom: 10 }}
                                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                                    onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                                >
                                    <ShoppingCartOutlined style={{ fontSize: 18 }} />
                                    Добавить в корзину
                                </button>
                            </>
                        )}

                        {!inStock && (
                            <button
                                disabled
                                style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '100%', height: 'var(--btn-h-xl)', background: 'var(--surface-3)', color: 'var(--ink-3)', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)', fontSize: 'var(--text-lg)', fontWeight: 500, cursor: 'not-allowed', fontFamily: 'var(--font-body)' }}
                            >
                                Нет в наличии
                            </button>
                        )}

                        {product.externalCode && (
                            <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid var(--line-1)', fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                                Код 1С: <span style={{ fontFamily: 'var(--font-mono)' }}>{product.externalCode}</span>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default ProductPage;
