import { useState, useMemo } from 'react';
import { Image, App, Skeleton, Modal, Button } from 'antd';
import { ShoppingCartOutlined, ShoppingOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import { getProductById } from '@/api/products';
import type { ProductImage, ProductChild } from '@/types/product';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import { useDisplayPrice } from '@/utils/priceUtils';
import { savePendingAddToCart, clearPendingAddToCart } from '@/utils/pendingCart';

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

const ATTR_ORDER = ['Размер', 'Рост'];

const VariantAttrs = ({ variant }: { variant: ProductChild }) => {
    const attrMap = variant.attributes
        ? Object.fromEntries(variant.attributes.map(a => [a.attributeName, a.attributeValue]))
        : {};
    const parts = ATTR_ORDER.filter(k => attrMap[k]);
    if (parts.length === 0) {
        const vals = Object.values(attrMap);
        return <span>{vals.length > 0 ? vals.join(' / ') : '—'}</span>;
    }
    return (
        <span>
            {parts.map((k, i) => (
                <span key={k}>
                    {i > 0 && <br />}
                    <span style={{ color: 'var(--ink-3)', fontSize: 'var(--text-xs)' }}>{k}: </span>
                    {attrMap[k]}
                </span>
            ))}
        </span>
    );
};

const ProductPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();
    const { message: messageApi } = App.useApp();
    const [quantity, setQuantity] = useState(1);
    const [variantQtys, setVariantQtys] = useState<Record<number, number>>({});
    const [authModalOpen, setAuthModalOpen] = useState(false);
    const addItem = useCartStore((state) => state.addItem);
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated);

    const { data: product, isLoading, isError } = useQuery({
        queryKey: ['product', id],
        queryFn: () => getProductById(Number(id)),
        enabled: !!id,
    });

    const hasVariants = (product?.children?.length ?? 0) > 0;
    const activeVariants = useMemo(
        () => (product?.children ?? []).filter(v => v.isActive),
        [product?.children],
    );

    const activeStock = product?.stockQuantity ?? 0;
    const displayPrice = useDisplayPrice({ price: product?.price ?? 0, wholesalePrice: product?.wholesalePrice ?? null });

    const setVariantQty = (variantId: number, qty: number) =>
        setVariantQtys(prev => ({ ...prev, [variantId]: qty }));

    const savePendingAdd = () => {
        if (!product) return;
        if (hasVariants) {
            const variantItems = activeVariants
                .map(v => ({ productId: v.id, quantity: variantQtys[v.id] ?? 0 }))
                .filter(i => i.quantity > 0);
            savePendingAddToCart(variantItems);
        } else {
            savePendingAddToCart([{ productId: product.id, quantity }]);
        }
    };

    const handleAddToCart = async () => {
        if (!product) return;
        if (!isAuthenticated) { savePendingAdd(); setAuthModalOpen(true); return; }
        if (hasVariants) {
            const toAdd = activeVariants.filter(v => (variantQtys[v.id] ?? 0) > 0);
            if (toAdd.length === 0) {
                messageApi.warning('Укажите количество хотя бы для одного варианта');
                return;
            }
            try {
                await Promise.all(toAdd.map(v => addItem(v.id, variantQtys[v.id])));
                messageApi.success(`${product.name} добавлен в корзину`);
            } catch {
                messageApi.error('Ошибка при добавлении в корзину');
            }
        } else {
            try {
                await addItem(product.id, quantity);
                messageApi.success(`${product.name} (${quantity} шт.) добавлен в корзину`);
                setQuantity(1);
            } catch {
                messageApi.error('Ошибка при добавлении в корзину');
            }
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
        <>
        <Modal
            open={authModalOpen}
            onCancel={() => { clearPendingAddToCart(); setAuthModalOpen(false); }}
            footer={null}
            title="Войдите, чтобы добавить в корзину"
            centered
            width={360}
        >
            <p style={{ color: 'var(--ink-2)', marginBottom: 20 }}>
                Для добавления товаров в корзину необходимо войти или зарегистрироваться.
            </p>
            <div style={{ display: 'flex', gap: 10 }}>
                <Button type="primary" block onClick={() => navigate('/login', { state: { from: location } })} style={{ background: 'var(--brand-red)', borderColor: 'var(--brand-red)' }}>
                    Войти
                </Button>
                <Button block onClick={() => navigate('/register', { state: { from: location } })}>
                    Зарегистрироваться
                </Button>
            </div>
        </Modal>
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

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 360px', gap: 28 }}>
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

                    {/* Материал */}
                    {product.material && (
                        <div style={{ borderTop: '1px solid var(--line-1)', paddingTop: 16, marginTop: product.description ? 16 : 0 }}>
                            <div style={{ display: 'flex', fontSize: 'var(--text-base)', padding: '5px 0' }}>
                                <span style={{ color: 'var(--ink-3)', flex: '0 0 120px' }}>Материал</span>
                                <span style={{ fontWeight: 500, color: 'var(--ink-1)' }}>{product.material}</span>
                            </div>
                        </div>
                    )}
                </div>

                {/* Buy box */}
                <div>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', padding: 20, position: 'sticky', top: 20, background: 'var(--surface)' }}>
                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 6 }}>
                            <div>
                                <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-6xl)', letterSpacing: '-0.02em', fontVariantNumeric: 'tabular-nums', color: 'var(--ink-1)' }}>
                                    {formatPrice(displayPrice)}
                                </span>
                                <div style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)', marginTop: 2 }}>
                                    за 1 {product.unitOfMeasure || 'шт.'}
                                </div>
                            </div>
                            <button
                                onClick={handleAddToCart}
                                style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8, height: 44, padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)', transition: 'background 0.12s', whiteSpace: 'nowrap', flexShrink: 0 }}
                                onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                                onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                            >
                                <ShoppingCartOutlined style={{ fontSize: 16 }} />
                                В корзину
                            </button>
                        </div>

                        {/* Таблица вариантов */}
                        {hasVariants ? (
                            <div style={{ marginBottom: 16 }}>
                                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 'var(--text-sm)' }}>
                                    <thead>
                                        <tr style={{ borderBottom: '1px solid var(--line-1)' }}>
                                            <th style={{ textAlign: 'left', padding: '4px 6px 6px 0', color: 'var(--ink-3)', fontWeight: 500 }}>Параметры</th>
                                            <th style={{ textAlign: 'right', padding: '4px 6px 6px', color: 'var(--ink-3)', fontWeight: 500 }}>Цена</th>
                                            <th style={{ textAlign: 'right', padding: '4px 6px 6px', color: 'var(--ink-3)', fontWeight: 500 }}>Остаток</th>
                                            <th style={{ textAlign: 'center', padding: '4px 0 6px 6px', color: 'var(--ink-3)', fontWeight: 500 }}>Кол-во</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {activeVariants.map(v => {
                                            const qty = variantQtys[v.id] ?? 0;
                                            const inStockV = v.stockQuantity > 0;
                                            const variantPrice = v.price ?? product.price;
                                            return (
                                                <tr key={v.id} style={{ borderBottom: '1px solid var(--line-1)', opacity: inStockV ? 1 : 0.45 }}>
                                                    <td style={{ padding: '7px 6px 7px 0', color: 'var(--ink-1)', fontWeight: 500, lineHeight: 1.5 }}>
                                                        <VariantAttrs variant={v} />
                                                    </td>
                                                    <td style={{ padding: '7px 6px', textAlign: 'right', color: 'var(--ink-2)', whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                                                        {formatPrice(variantPrice)}
                                                    </td>
                                                    <td style={{ padding: '7px 6px', textAlign: 'right', whiteSpace: 'nowrap', color: inStockV ? 'var(--brand-green)' : 'var(--brand-red)', fontVariantNumeric: 'tabular-nums' }}>
                                                        {inStockV ? `${v.stockQuantity} ${product.unitOfMeasure || 'шт.'}` : 'нет'}
                                                    </td>
                                                    <td style={{ padding: '7px 0 7px 6px' }}>
                                                        {inStockV ? (
                                                            <div style={{ display: 'flex', border: '1px solid var(--line-2)', borderRadius: 'var(--r-2)', height: 28, alignItems: 'center', minWidth: 80 }}>
                                                                <button
                                                                    onClick={() => setVariantQty(v.id, Math.max(0, qty - 1))}
                                                                    style={{ width: 26, height: 26, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
                                                                >−</button>
                                                                <input
                                                                    type="number"
                                                                    min={0}
                                                                    max={v.stockQuantity}
                                                                    value={qty === 0 ? '' : qty}
                                                                    placeholder="0"
                                                                    onChange={(e) => {
                                                                        const val = e.target.value === '' ? 0 : Math.min(v.stockQuantity, Math.max(0, Number(e.target.value)));
                                                                        setVariantQty(v.id, val);
                                                                    }}
                                                                    style={{ flex: 1, width: 0, textAlign: 'center', border: 0, background: 'transparent', fontSize: 13, fontWeight: 600, outline: 'none', fontFamily: 'var(--font-head)', color: 'var(--ink-1)', fontVariantNumeric: 'tabular-nums' }}
                                                                />
                                                                <button
                                                                    onClick={() => setVariantQty(v.id, Math.min(v.stockQuantity, qty + 1))}
                                                                    style={{ width: 26, height: 26, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}
                                                                >+</button>
                                                            </div>
                                                        ) : (
                                                            <span style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-4)' }}>—</span>
                                                        )}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        ) : (
                            <div style={{ marginBottom: 14 }}>
                                <label style={{ display: 'block', fontSize: 'var(--text-sm)', fontWeight: 500, color: 'var(--ink-2)', marginBottom: 6 }}>Количество</label>
                                <div style={{ display: 'flex', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)', height: 'var(--input-h-lg)', alignItems: 'center' }}>
                                    <button
                                        onClick={() => setQuantity((q) => Math.max(1, q - 1))}
                                        style={{ width: 40, height: 42, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                    >−</button>
                                    <input
                                        type="number"
                                        min={1}
                                        value={quantity}
                                        onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
                                        style={{ flex: 1, textAlign: 'center', border: 0, background: 'transparent', fontSize: 'var(--text-xl)', fontWeight: 600, outline: 'none', fontFamily: 'var(--font-head)', color: 'var(--ink-1)', fontVariantNumeric: 'tabular-nums' }}
                                    />
                                    <button
                                        onClick={() => setQuantity((q) => q + 1)}
                                        style={{ width: 40, height: 42, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 18, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                                    >+</button>
                                </div>
                            </div>
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
        </>
    );
};

export default ProductPage;
