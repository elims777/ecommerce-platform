import { useEffect } from 'react';
import { Popconfirm, Skeleton, App } from 'antd';
import { DeleteOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '@/store/cartStore';
import type { CartItemDto } from '@/api/cart';

const formatPrice = (price: number): string =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(price);

const CartItemRow = ({ item, onRemove, onUpdateQty }: { item: CartItemDto; onRemove: (item: CartItemDto) => void; onUpdateQty: (id: number, qty: number) => void }) => (
    <div style={{ display: 'grid', gridTemplateColumns: '1fr 160px 130px 130px 36px', gap: 16, alignItems: 'center', padding: '18px 20px', borderBottom: '1px solid var(--line-1)' }}>
        <div>
            <div style={{ fontSize: 14, fontWeight: 500, lineHeight: 1.35, color: 'var(--ink-1)', marginBottom: 4, cursor: 'pointer' }}>
                {item.productName}
            </div>
        </div>

        <div style={{ display: 'flex', border: '1px solid var(--line-2)', borderRadius: 6, height: 36, alignItems: 'center' }}>
            <button
                onClick={() => onUpdateQty(item.productId, Math.max(1, item.quantity - 1))}
                style={{ width: 32, height: 34, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
                −
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
                style={{ width: 32, height: 34, border: 0, background: 'transparent', color: 'var(--ink-2)', cursor: 'pointer', fontSize: 16, display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
                +
            </button>
        </div>

        <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 13, fontVariantNumeric: 'tabular-nums', color: 'var(--ink-2)' }}>{formatPrice(item.price)}</div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>за шт.</div>
        </div>

        <div style={{ textAlign: 'right' }}>
            <div style={{ fontFamily: 'var(--font-head)', fontSize: 16, fontWeight: 600, fontVariantNumeric: 'tabular-nums', color: 'var(--ink-1)' }}>
                {formatPrice(item.subtotal)}
            </div>
        </div>

        <Popconfirm title="Удалить из корзины?" onConfirm={() => onRemove(item)} okText="Да" cancelText="Нет">
            <button style={{ width: 32, height: 32, border: 0, background: 'transparent', color: 'var(--ink-3)', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', borderRadius: 4, transition: 'color 0.12s' }}
                onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--brand-red)')}
                onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--ink-3)')}
            >
                <DeleteOutlined />
            </button>
        </Popconfirm>
    </div>
);

const CartPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const { items, totalAmount, isLoading, fetchCart, updateQuantity, removeItem, clearCart } = useCartStore();

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
        try {
            await clearCart();
            messageApi.success('Корзина очищена');
        } catch {
            messageApi.error('Ошибка при очистке корзины');
        }
    };

    if (isLoading) return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 320px', gap: 24, padding: '24px 0' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} style={{ background: '#fff', borderRadius: 10, padding: 20, border: '1px solid var(--line-1)' }}>
              <Skeleton active avatar={{ shape: 'square', size: 80 }} paragraph={{ rows: 2 }} title={{ width: '50%' }} />
            </div>
          ))}
        </div>
        <div style={{ background: '#fff', borderRadius: 10, padding: 20, border: '1px solid var(--line-1)', height: 220 }}>
          <Skeleton active paragraph={{ rows: 4 }} />
        </div>
      </div>
    );

    if (items.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '80px 0' }}>
                <ShoppingCartOutlined style={{ fontSize: 64, color: 'var(--ink-4)', display: 'block', margin: '0 auto 16px' }} />
                <div style={{ fontFamily: 'var(--font-head)', fontSize: 20, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>Корзина пуста</div>
                <div style={{ fontSize: 14, color: 'var(--ink-3)', marginBottom: 24 }}>Добавьте товары из каталога</div>
                <button
                    onClick={() => navigate('/')}
                    style={{ display: 'inline-flex', alignItems: 'center', height: 40, padding: '0 20px', border: '1px solid var(--brand-navy)', color: 'var(--brand-navy)', background: 'transparent', borderRadius: 6, fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                >
                    Перейти в каталог
                </button>
            </div>
        );
    }

    return (
        <div style={{ paddingTop: 20, paddingBottom: 60 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14, marginBottom: 24 }}>
                <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 32, fontWeight: 600, letterSpacing: '-0.02em', color: 'var(--ink-1)', margin: 0 }}>Корзина</h1>
                <span style={{ fontSize: 14, color: 'var(--ink-3)' }}>{items.length} {items.length === 1 ? 'товар' : 'товара'}</span>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 24 }}>
                {/* Список товаров */}
                <div>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 8, background: 'var(--surface)', overflow: 'hidden' }}>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 160px 130px 130px 36px', gap: 16, padding: '10px 20px', background: 'var(--surface-2)', borderBottom: '1px solid var(--line-1)', fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
                            <span>Товар</span>
                            <span>Количество</span>
                            <span style={{ textAlign: 'right' }}>Цена</span>
                            <span style={{ textAlign: 'right' }}>Сумма</span>
                            <span />
                        </div>
                        {items.map((item) => (
                            <CartItemRow key={item.productId} item={item} onRemove={handleRemove} onUpdateQty={handleUpdateQuantity} />
                        ))}
                        <div style={{ padding: '12px 20px', background: 'var(--surface-2)', borderTop: '1px solid var(--line-1)', display: 'flex', justifyContent: 'flex-end' }}>
                            <Popconfirm title="Очистить корзину?" description="Все товары будут удалены" onConfirm={handleClear} okText="Да" cancelText="Отмена">
                                <button style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 32, padding: '0 12px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--brand-red)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}>
                                    Очистить корзину
                                </button>
                            </Popconfirm>
                        </div>
                    </div>

                    <button
                        onClick={() => navigate('/')}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 36, padding: '0 16px', border: '1px solid var(--line-2)', background: 'transparent', color: 'var(--ink-2)', borderRadius: 6, fontSize: 13, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)', marginTop: 16 }}
                    >
                        ← Продолжить покупки
                    </button>
                </div>

                {/* Сайдбар итого */}
                <aside>
                    <div style={{ border: '1px solid var(--line-1)', borderRadius: 8, padding: 20, position: 'sticky', top: 20, background: 'var(--surface)' }}>
                        <h3 style={{ fontFamily: 'var(--font-head)', fontSize: 17, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 16 }}>Итого по заказу</h3>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 16 }}>
                            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14, color: 'var(--ink-2)' }}>
                                <span>Товары ({items.length} поз.)</span>
                                <span style={{ fontVariantNumeric: 'tabular-nums' }}>{formatPrice(totalAmount)}</span>
                            </div>
                        </div>

                        <div style={{ height: 1, background: 'var(--line-1)', margin: '16px 0' }} />

                        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 20 }}>
                            <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>К оплате</span>
                            <span style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 26, letterSpacing: '-0.02em', fontVariantNumeric: 'tabular-nums', color: 'var(--ink-1)' }}>
                                {formatPrice(totalAmount)}
                            </span>
                        </div>

                        <button
                            onClick={() => navigate('/checkout')}
                            style={{ display: 'inline-flex', alignItems: 'center', justifyContent: 'center', width: '100%', height: 48, background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6, fontSize: 15, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)', transition: 'background 0.12s' }}
                            onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--brand-red-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--brand-red)')}
                        >
                            Оформить заказ →
                        </button>

                        <div style={{ fontSize: 11.5, color: 'var(--ink-3)', textAlign: 'center', marginTop: 10, lineHeight: 1.5 }}>
                            Менеджер согласует наличие и условия в течение 15 минут
                        </div>
                    </div>
                </aside>
            </div>
        </div>
    );
};

export default CartPage;
