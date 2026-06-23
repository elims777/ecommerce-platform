import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Spin } from 'antd';
import { App } from 'antd';
import { getFavouriteProducts } from '@/api/favourites';
import ProductCard from '@/features/catalog/ProductCard';
import { useCartStore } from '@/store/cartStore';

const HeartEmptyIcon = () => (
    <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" style={{ color: 'var(--ink-4)' }}>
        <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>
    </svg>
);

const FavouritesPage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const addItem = useCartStore((s) => s.addItem);

    const { data: products = [], isLoading } = useQuery({
        queryKey: ['favourites', 'products'],
        queryFn: getFavouriteProducts,
        staleTime: 0,
    });

    const handleAddToCart = async (productId: number) => {
        try {
            await addItem(productId, 1);
            messageApi.success('Товар добавлен в корзину');
        } catch {
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    return (
        <div style={{ paddingBottom: 60 }}>
            <div style={{ paddingTop: 28, marginBottom: 24 }}>
                <h1 style={{
                    fontFamily: 'var(--font-head)', fontSize: 26, fontWeight: 600,
                    letterSpacing: '-0.018em', color: 'var(--ink-1)', margin: 0,
                }}>
                    Избранное
                </h1>
                {!isLoading && products.length > 0 && (
                    <p style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 6, marginBottom: 0 }}>
                        {products.length} {products.length === 1 ? 'товар' : products.length < 5 ? 'товара' : 'товаров'}
                    </p>
                )}
            </div>

            {isLoading ? (
                <div style={{ textAlign: 'center', padding: 80 }}>
                    <Spin size="large" />
                </div>
            ) : products.length === 0 ? (
                <div style={{
                    display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
                    padding: '80px 0', gap: 16,
                }}>
                    <HeartEmptyIcon />
                    <div style={{ fontSize: 16, fontWeight: 500, color: 'var(--ink-2)' }}>В избранном пока ничего нет</div>
                    <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>Добавляйте товары, нажимая на сердечко в карточке</div>
                    <button
                        onClick={() => navigate('/catalog')}
                        style={{
                            marginTop: 8, height: 38, padding: '0 20px',
                            background: 'var(--brand-red)', color: '#fff',
                            border: 'none', borderRadius: 'var(--r-3)', fontSize: 14, fontWeight: 500,
                            cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--brand-red)'; }}
                    >
                        Перейти в каталог
                    </button>
                </div>
            ) : (
                <div style={{
                    display: 'flex', flexWrap: 'wrap', gap: 14,
                }}>
                    {products.map((product) => (
                        <ProductCard
                            key={product.id}
                            product={product}
                            onClick={() => navigate(`/products/${product.id}`)}
                            onAddToCart={handleAddToCart}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

export default FavouritesPage;
