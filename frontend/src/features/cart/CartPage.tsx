import { useEffect, useState } from 'react';
import { App } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import type { CartItemDto } from '@/api/cart';

// ── Icons ─────────────────────────────────────────────────────
const TrashIcon = () => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4h6v2"/>
    </svg>
);
const MinusIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <line x1="5" y1="12" x2="19" y2="12"/>
    </svg>
);
const PlusIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
    </svg>
);
const CartEmptyIcon = () => (
    <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 4h2.5l2 13.5h11l2-9h-14"/><circle cx="9" cy="20.5" r="1.4"/><circle cx="18" cy="20.5" r="1.4"/>
    </svg>
);
const ArrRight = () => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12h14M13 6l6 6-6 6"/>
    </svg>
);

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(price);

// ── Confirm dialog (нативный) ─────────────────────────────────
const ConfirmDialog = ({ message, onConfirm, onCancel }: { message: string; onConfirm: () => void; onCancel: () => void }) => (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--overlay-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 'var(--z-modal)' as any }}
        onClick={onCancel}>
        <div style={{ background: 'var(--surface)', borderRadius: 'var(--r-5)', padding: '24px 28px', width: 340, boxShadow: 'var(--shadow-pop)' }}
            onClick={(e) => e.stopPropagation()}>
            <div style={{ fontWeight: 600, fontSize: 15, color: 'var(--ink-1)', marginBottom: 8 }}>Подтверждение</div>
            <div style={{ fontSize: 13.5, color: 'var(--ink-2)', marginBottom: 20 }}>{message}</div>
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                <button onClick={onCancel} style={{ height: 'var(--btn-h-md)', padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 'var(--r-3)', fontSize: 'var(--text-base)', cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                    Отмена
                </button>
                <button onClick={onConfirm} style={{ height: 'var(--btn-h-md)', padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-base)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                    Удалить
                </button>
            </div>
        </div>
    </div>
);

// ── CartItemRow ───────────────────────────────────────────────
const CartItemRow = ({ item, onRemove, onUpdateQty }: {
    item: CartItemDto;
    onRemove: (item: CartItemDto) => void;
    onUpdateQty: (id: number, qty: number) => void;
}) => {
    const [confirmOpen, setConfirmOpen] = useState(false);

    return (
        <>
            {confirmOpen && (
                <ConfirmDialog
                    message={`Удалить «${item.productName}» из корзины?`}
                    onConfirm={() => { setConfirmOpen(false); onRemove(item); }}
                    onCancel={() => setConfirmOpen(false)}
                />
            )}
            <div style={{
                display: 'grid',
                gridTemplateColumns: '1fr 160px 130px 130px 36px',
                gap: 16, alignItems: 'center',
                padding: '18px 20px', borderBottom: '1px solid var(--line-1)',
            }}>
                <div>
                    <div style={{ fontSize: 14, fontWeight: 500, lineHeight: 1.35, color: 'var(--ink-1)' }}>
                        {item.productName}
                    </div>
                </div>

                {/* Qty stepper */}
                <div style={{ display: 'flex', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)', height: 'var(--btn-h-md)', alignItems: 'center' }}>
                    <button
                        onClick={() => onUpdateQty(item.productId, Math.max(1, item.quantity - 1))}
                        style={{ width: 32, height: 34, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    >
                        <MinusIcon />
                    </button>
                    <input
                        type="number"
                        min={1}
                        value={item.quantity}
                        onChange={(e) => onUpdateQty(item.productId, Math.max(1, Number(e.target.value)))}
                        style={{ flex: 1, textAlign: 'center', border: 0, background: 'transparent', fontSize: 14, fontWeight: 600, outline: 'none', minWidth: 0, fontFamily: 'var(--font-head)', color: 'var(--ink-1)', fontVariantNumeric: 'tabular-nums' }}
                    />
                    <button
                        onClick={() => onUpdateQty(item.productId, item.quantity + 1)}
                        style={{ width: 32, height: 34, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
                    >
                        <PlusIcon />
                    </button>
                </div>

                {/* Unit price */}
                <div style={{ textAlign: 'right' }}>
                    <div style={{ fontSize: 13, fontVariantNumeric: 'tabular-nums', color: 'var(--ink-2)' }}>{formatPrice(item.price)}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>за шт.</div>
                </div>

                {/* Subtotal */}
                <div style={{ textAlign: 'right' }}>
                    <div style={{ fontFamily: 'var(--font-head)', fontSize: 16, fontWeight: 600, fontVariantNumeric: 'tabular-nums', color: 'var(--ink-1)' }}>
                        {formatPrice(item.subtotal)}
                    </div>
                </div>

                {/* Delete */}
                <button
                    onClick={() => setConfirmOpen(true)}
                    style={{ width: 32, height: 32, border: 0, background: 'transparent', color: 'var(--ink-3)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: 4, transition: 'color 0.12s' }}
                    onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--brand-red)')}
                    onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--ink-3)')}
                >
                    <TrashIcon />
                </button>
            </div>
        </>
    );
};

// ── Skeleton ──────────────────────────────────────────────────
const CartSkeleton = () => (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 24, paddingTop: 20 }}>
        <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', background: 'var(--surface)', overflow: 'hidden' }}>
            {Array.from({ length: 3 }).map((_, i) => (
                <div key={i} style={{ display: 'flex', gap: 16, padding: '18px 20px', borderBottom: '1px solid var(--line-1)' }}>
                    <div style={{ flex: 1, height: 14, background: 'var(--surface-3)', borderRadius: 4, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
                    <div style={{ width: 120, height: 14, background: 'var(--surface-3)', borderRadius: 4, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
                    <div style={{ width: 90, height: 14, background: 'var(--surface-3)', borderRadius: 4, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
                </div>
            ))}
        </div>
        <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', background: 'var(--surface)', padding: 20, height: 200, animation: 'rf-pulse 1.5s ease-in-out infinite' }}/>
    </div>
);

// ── CartPage ──────────────────────────────────────────────────
const CartPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const { items, totalAmount, isLoading, fetchCart, updateQuantity, removeItem, clearCart } = useCartStore();
    const [clearConfirmOpen, setClearConfirmOpen] = useState(false);

    useEffect(() => { fetchCart(); }, [fetchCart]);

    const handleRemove = async (item: CartItemDto) => {
        try {
            await removeItem(item.productId);
            messageApi.success(`${item.productName} удалён из корзины`);
        } catch {
            messageApi.error('Ошибка при удалении товара');
        }
    };

    const handleUpdateQuantity = async (productId: number, quantity: number) => {
        try {
            await updateQuantity(productId, quantity);
        } catch {
            messageApi.error('Ошибка при обновлении количества');
        }
    };

    const handleClear = async () => {
        setClearConfirmOpen(false);
        try {
            await clearCart();
            messageApi.success('Корзина очищена');
        } catch {
            messageApi.error('Ошибка при очистке корзины');
        }
    };

    if (isLoading) return <CartSkeleton />;

    if (items.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <div style={{
                    width: 72, height: 72, borderRadius: '50%', background: 'var(--surface-2)',
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    margin: '0 auto 20px', color: 'var(--ink-3)',
                }}>
                    <CartEmptyIcon />
                </div>
                <div style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>
                    Корзина пуста
                </div>
                <div style={{ fontSize: 14, color: 'var(--ink-3)', maxWidth: 340, margin: '0 auto 8px' }}>
                    Найдите нужный товар в каталоге — добавляйте прямо с карточки товара.
                </div>
                <div style={{ fontSize: 13, color: 'var(--ink-4)', marginBottom: 28 }}>
                    Для юридических лиц доступна оплата по счёту и работа по договору.
                </div>
                <button
                    onClick={() => navigate('/catalog')}
                    style={{
                        display: 'inline-flex', alignItems: 'center', gap: 8,
                        height: 44, padding: '0 28px', background: 'var(--brand-red)', color: '#fff',
                        border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-lg)', fontWeight: 500,
                        cursor: 'pointer', fontFamily: 'var(--font-body)',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                >
                    Перейти в каталог <ArrRight />
                </button>
            </div>
        );
    }

    return (
        <div style={{ paddingTop: 20, paddingBottom: 60 }}>
            {clearConfirmOpen && (
                <ConfirmDialog
                    message="Все товары будут удалены из корзины."
                    onConfirm={handleClear}
                    onCancel={() => setClearConfirmOpen(false)}
                />
            )}

            {/* Breadcrumbs */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 'var(--text-sm)', color: 'var(--ink-3)', marginBottom: 12 }}>
                <span onClick={() => navigate('/')} style={{ cursor: 'pointer' }}>Главная</span>
                <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6"/></svg>
                <span style={{ color: 'var(--ink-1)', fontWeight: 500 }}>Корзина</span>
            </div>

            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, marginBottom: 24 }}>
                <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', margin: 0 }}>Корзина</h1>
                <span style={{ fontSize: 14, color: 'var(--ink-3)' }}>{items.length} {items.length === 1 ? 'товар' : 'товара'}</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 380px', gap: 24 }}>
                {/* Items table */}
                <div>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', background: 'var(--surface)', overflow: 'hidden' }}>
                        {/* Header */}
                        <div style={{
                            display: 'grid', gridTemplateColumns: '1fr 160px 130px 130px 36px',
                            gap: 16, padding: '10px 20px',
                            background: 'var(--surface-2)', borderBottom: '1px solid var(--line-1)',
                            fontSize: 11, fontWeight: 600, color: 'var(--ink-3)',
                            textTransform: 'uppercase', letterSpacing: '0.05em',
                        }}>
                            <span>Товар</span>
                            <span>Количество</span>
                            <span style={{ textAlign: 'right' }}>Цена</span>
                            <span style={{ textAlign: 'right' }}>Сумма</span>
                            <span />
                        </div>

                        {items.map((item) => (
                            <CartItemRow
                                key={item.productId}
                                item={item}
                                onRemove={handleRemove}
                                onUpdateQty={handleUpdateQuantity}
                            />
                        ))}

                        {/* Footer actions */}
                        <div style={{ padding: '12px 20px', background: 'var(--surface-2)', borderTop: '1px solid var(--line-1)', display: 'flex', justifyContent: 'flex-end' }}>
                            <button
                                onClick={() => setClearConfirmOpen(true)}
                                style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 'var(--btn-h-sm)', padding: '0 12px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--brand-red)', borderRadius: 'var(--r-3)', fontSize: 'var(--text-base)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                                onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--red-tint)'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                            >
                                <TrashIcon /> Очистить корзину
                            </button>
                        </div>
                    </div>

                    <button
                        onClick={() => navigate('/catalog')}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 'var(--btn-h-md)', padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 'var(--r-3)', fontSize: 'var(--text-base)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)', marginTop: 16 }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--surface-2)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                    >
                        ← Продолжить покупки
                    </button>
                </div>

                {/* Summary sidebar */}
                <aside>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)', padding: 20, position: 'sticky', top: 20, background: 'var(--surface)' }}>
                        <h3 style={{ fontFamily: 'var(--font-head)', fontSize: 17, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 16 }}>
                            Итого по заявке
                        </h3>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 16 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--ink-2)' }}>
                                <span>Товары ({items.length} поз.)</span>
                                <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatPrice(totalAmount)}</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--brand-green)' }}>
                                <span>Доставка по графику</span>
                                <span style={{ fontWeight: 500 }}>Бесплатно</span>
                            </div>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, color: 'var(--ink-3)' }}>
                                <span>НДС 20% (в т.ч.)</span>
                                <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatPrice(totalAmount / 1.2 * 0.2)}</span>
                            </div>
                        </div>

                        <div style={{ height: 1, background: 'var(--line-1)', margin: '16px 0' }} />

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 4 }}>
                            <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>К оплате</span>
                            <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 26, letterSpacing: '-0.02em', fontVariantNumeric: 'tabular-nums', color: 'var(--ink-1)' }}>
                                {formatPrice(totalAmount)}
                            </span>
                        </div>

                        <button
                            onClick={() => navigate('/checkout')}
                            style={{
                                display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                                width: '100%', height: 48, marginTop: 16,
                                background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)',
                                fontSize: 'var(--text-lg)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                transition: 'background 0.12s',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                        >
                            Оформить заявку <ArrRight />
                        </button>
                    </div>
                </aside>
            </div>
        </div>
    );
};

export default CartPage;
