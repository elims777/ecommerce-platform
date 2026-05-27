import { useNavigate } from 'react-router-dom';
import { Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import ProductCard from '@/features/catalog/ProductCard';
import { useCartStore } from '@/store/cartStore';
import { App } from 'antd';
import type { CategoryTree } from '@/types/product';

const ArrRight = () => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12h14M13 6l6 6-6 6"/>
    </svg>
);
const DocIcon = () => (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>
    </svg>
);
const TruckIcon = () => (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 6h12v10H2zM14 10h4l3 3v3h-7"/><circle cx="6.5" cy="17.5" r="1.7"/><circle cx="17" cy="17.5" r="1.7"/>
    </svg>
);
const ShieldIcon = () => (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
);
const BuildingIcon = () => (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>
    </svg>
);
const CheckIcon = () => (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12"/>
    </svg>
);
const GridIcon = () => (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
    </svg>
);

const flattenCategories = (tree: CategoryTree[]): CategoryTree[] => {
    const result: CategoryTree[] = [];
    const traverse = (nodes: CategoryTree[]) => {
        for (const node of nodes) {
            result.push(node);
            if (node.children?.length) traverse(node.children);
        }
    };
    traverse(tree);
    return result;
};

const CategoryTile = ({ category, featured, onClick }: { category: CategoryTree; featured: boolean; onClick: () => void }) => {
    if (featured) {
        return (
            <div
                onClick={onClick}
                style={{
                    gridColumn: 'span 2', gridRow: 'span 2',
                    background: 'linear-gradient(140deg, #C0272D 0%, #8E1C24 100%)',
                    color: '#fff', borderRadius: 10, padding: 24,
                    display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
                    position: 'relative', overflow: 'hidden', minHeight: 220, cursor: 'pointer',
                }}
            >
                <div style={{ position: 'absolute', right: -20, top: -20, opacity: 0.15 }}>
                    <GridIcon />
                </div>
                <div>
                    <span style={{ fontSize: 11, opacity: 0.8, letterSpacing: '0.08em', textTransform: 'uppercase' }}>Самая популярная категория</span>
                    <h3 style={{ fontFamily: 'var(--font-head)', fontSize: 26, fontWeight: 600, color: '#fff', marginTop: 8, letterSpacing: '-0.02em', lineHeight: 1.1 }}>
                        {category.name}
                    </h3>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: 13 }}>Перейти в раздел</span>
                    <ArrRight />
                </div>
            </div>
        );
    }
    return (
        <div
            onClick={onClick}
            style={{
                background: '#fff', border: '1px solid var(--line-1)',
                borderRadius: 8, padding: 16,
                display: 'flex', flexDirection: 'column', gap: 12,
                minHeight: 104, cursor: 'pointer',
                transition: 'box-shadow 0.15s, transform 0.15s',
            }}
            onMouseEnter={(e) => {
                e.currentTarget.style.boxShadow = 'var(--shadow-2)';
                e.currentTarget.style.transform = 'translateY(-1px)';
            }}
            onMouseLeave={(e) => {
                e.currentTarget.style.boxShadow = '';
                e.currentTarget.style.transform = '';
            }}
        >
            <div style={{
                width: 40, height: 40, borderRadius: 6,
                background: 'var(--surface-2)', color: 'var(--brand-navy)',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            }}>
                <GridIcon />
            </div>
            <div>
                <div style={{ fontSize: 13.5, fontWeight: 600, color: 'var(--ink-1)', lineHeight: 1.3 }}>{category.name}</div>
            </div>
        </div>
    );
};

const ServiceCard = ({ icon, title, text, cta, accent }: { icon: React.ReactNode; title: string; text: string; cta: string; accent?: string }) => {
    const color = accent === 'navy' ? 'var(--brand-navy)' : accent === 'green' ? '#1A6B3A' : 'var(--brand-red)';
    const tint  = accent === 'navy' ? '#EEF3F9' : accent === 'green' ? '#E6F1EB' : '#FDF2F2';
    return (
        <div style={{ background: '#fff', border: '1px solid var(--line-1)', borderRadius: 10, padding: 22, display: 'flex', gap: 16 }}>
            <div style={{
                width: 44, height: 44, borderRadius: 8,
                background: tint, color, flex: '0 0 auto',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            }}>{icon}</div>
            <div style={{ flex: 1 }}>
                <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 4, color: 'var(--ink-1)' }}>{title}</div>
                <p style={{ fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.5, margin: 0 }}>{text}</p>
                <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginTop: 10, fontSize: 13, fontWeight: 600, color, cursor: 'pointer' }}>
                    {cta} <ArrRight />
                </div>
            </div>
        </div>
    );
};

const Bul = ({ children }: { children: React.ReactNode }) => (
    <div style={{ display: 'flex', gap: 10, fontSize: 13, lineHeight: 1.4 }}>
        <span style={{
            width: 18, height: 18, borderRadius: 4,
            background: '#E6F1EB', color: '#1A6B3A',
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flex: '0 0 auto',
        }}>
            <CheckIcon />
        </span>
        <span style={{ color: 'var(--ink-2)' }}>{children}</span>
    </div>
);

const Stat = ({ n, l }: { n: string; l: string }) => (
    <div>
        <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 20, color: '#fff', letterSpacing: '-0.01em', fontVariantNumeric: 'tabular-nums' }}>{n}</div>
        <div style={{ fontSize: 11, color: 'rgba(255,255,255,.6)', marginTop: 2 }}>{l}</div>
    </div>
);

const HomePage = () => {
    const navigate = useNavigate();
    const { message: messageApi } = App.useApp();
    const addItem = useCartStore((state) => state.addItem);

    const { data: categoryTree = [], isLoading: categoriesLoading } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    const { data: featuredPage, isLoading: productsLoading } = useQuery({
        queryKey: ['products', { page: 0, size: 8 }],
        queryFn: () => getProducts({ page: 0, size: 8, sort: 'createdAt,desc' }),
        staleTime: 60 * 1000,
    });

    const allCategories = flattenCategories(categoryTree);
    const displayCategories = allCategories.slice(0, 11);

    const handleAddToCart = async (productId: number) => {
        try {
            await addItem(productId, 1);
            messageApi.success('Товар добавлен в корзину');
        } catch {
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    const handleCategoryClick = (categoryId: number) => {
        navigate(`/catalog?category=${categoryId}`);
    };

    return (
        <div style={{ paddingBottom: 60 }}>
            {/* HERO */}
            <div style={{ padding: '24px 0 0' }}>
                <div style={{
                    background: 'linear-gradient(95deg, #1E3A5F 0%, #16304F 100%)',
                    borderRadius: 12,
                    padding: '36px 44px',
                    color: '#fff',
                    display: 'grid',
                    gridTemplateColumns: '1.4fr 1fr',
                    gap: 32,
                    alignItems: 'center',
                    position: 'relative',
                    overflow: 'hidden',
                }}>
                    <svg width="280" height="240" viewBox="0 0 280 240" style={{ position: 'absolute', right: -40, bottom: -30, opacity: 0.25, pointerEvents: 'none' }}>
                        <polygon points="140,10 140,180 30,210" fill="#C0272D"/>
                        <polygon points="140,10 140,180 250,210" fill="#0F2845"/>
                        <polygon points="30,210 250,210 140,180" fill="#1A6B3A"/>
                    </svg>

                    <div style={{ position: 'relative', zIndex: 1 }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, background: 'rgba(255,255,255,.1)', padding: '5px 12px', borderRadius: 99, fontSize: 12, marginBottom: 16 }}>
                            <span style={{ width: 6, height: 6, borderRadius: 3, background: '#1A6B3A', display: 'inline-block' }}/>
                            Поставки от 1 часа по Москве
                        </div>
                        <h1 style={{
                            fontFamily: 'var(--font-head)', fontSize: 38, fontWeight: 600,
                            letterSpacing: '-0.022em', lineHeight: 1.1, color: '#fff',
                            maxWidth: 580, margin: '0 0 14px',
                        }}>
                            Комплексное снабжение предприятий — в одном кабинете
                        </h1>
                        <p style={{ margin: '0 0 24px', fontSize: 14.5, lineHeight: 1.55, color: 'rgba(255,255,255,.78)', maxWidth: 480 }}>
                            СИЗ, спецодежда, противопожарное оборудование, медицинские расходники и ещё 12 000+ позиций — со счёт-фактурой, ЭДО и отсрочкой по договору.
                        </p>
                        <div style={{ display: 'flex', gap: 10, marginBottom: 26 }}>
                            <button
                                onClick={() => navigate('/catalog')}
                                style={{
                                    display: 'inline-flex', alignItems: 'center', gap: 8,
                                    height: 44, padding: '0 20px',
                                    background: '#C0272D', color: '#fff',
                                    border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 600,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.background = '#a82227'}
                                onMouseLeave={(e) => e.currentTarget.style.background = '#C0272D'}
                            >
                                Открыть каталог <ArrRight />
                            </button>
                            <button
                                style={{
                                    display: 'inline-flex', alignItems: 'center', gap: 8,
                                    height: 44, padding: '0 20px',
                                    background: 'rgba(255,255,255,.12)', color: '#fff',
                                    border: '1px solid rgba(255,255,255,.2)', borderRadius: 6, fontSize: 14, fontWeight: 500,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,255,255,.2)'}
                                onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(255,255,255,.12)'}
                            >
                                <DocIcon /> Запрос на снабжение
                            </button>
                        </div>
                        <div style={{ display: 'flex', gap: 28, fontSize: 12.5 }}>
                            <Stat n="12 480" l="товаров в наличии"/>
                            <Stat n="18 200" l="клиентов с 2008 г."/>
                            <Stat n="84" l="региона доставки"/>
                            <Stat n="4.9 ★" l="оценка на Я.Маркете"/>
                        </div>
                    </div>

                    {/* Side card */}
                    <div style={{
                        background: '#fff', color: 'var(--ink-1)',
                        borderRadius: 10, padding: 22,
                        position: 'relative', zIndex: 1,
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
                            <BuildingIcon />
                            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)' }}>Для юридических лиц</span>
                            <span style={{
                                marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', height: 20, padding: '0 7px',
                                borderRadius: 4, background: '#E6F1EB', color: '#1A6B3A', fontSize: 11, fontWeight: 600,
                            }}>−7% к прайсу</span>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 18 }}>
                            <Bul>Безналичный расчёт, счёт-фактура, ЭДО</Bul>
                            <Bul>Отсрочка платежа до 45 дней по договору</Bul>
                            <Bul>Закреплённый персональный менеджер</Bul>
                            <Bul>Госзакупки 44-ФЗ · 223-ФЗ</Bul>
                        </div>
                        <div style={{ display: 'flex', gap: 8 }}>
                            <button
                                onClick={() => navigate('/register')}
                                style={{
                                    flex: 1, height: 38,
                                    background: 'var(--brand-red)', color: '#fff',
                                    border: 'none', borderRadius: 6, fontSize: 13, fontWeight: 600,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.background = '#a82227'}
                                onMouseLeave={(e) => e.currentTarget.style.background = 'var(--brand-red)'}
                            >
                                Открыть юр. счёт
                            </button>
                            <button
                                onClick={() => navigate('/register')}
                                style={{
                                    height: 38, padding: '0 14px',
                                    background: 'transparent', color: 'var(--ink-3)',
                                    border: '1px solid var(--line-2)', borderRadius: 6, fontSize: 13, fontWeight: 500,
                                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.background = 'var(--surface-2)'}
                                onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
                            >
                                Я физлицо
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* CATEGORIES */}
            <div style={{ paddingTop: 36 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 18 }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, letterSpacing: '-0.012em', color: 'var(--ink-1)', margin: 0 }}>
                        Категории каталога
                    </h2>
                    <span
                        onClick={() => navigate('/catalog')}
                        style={{ fontSize: 13, color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}
                    >
                        Все разделы →
                    </span>
                </div>

                {categoriesLoading ? (
                    <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
                ) : displayCategories.length > 0 ? (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
                        {displayCategories.map((cat, i) => (
                            <CategoryTile
                                key={cat.id}
                                category={cat}
                                featured={i === 0}
                                onClick={() => handleCategoryClick(cat.id)}
                            />
                        ))}
                    </div>
                ) : (
                    <div style={{ textAlign: 'center', padding: 40, color: 'var(--ink-3)', fontSize: 14 }}>
                        Категории загружаются...
                    </div>
                )}
            </div>

            {/* FEATURED PRODUCTS */}
            <div style={{ paddingTop: 36 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 18 }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, letterSpacing: '-0.012em', color: 'var(--ink-1)', margin: 0 }}>
                        Хиты снабжения
                    </h2>
                    <span
                        onClick={() => navigate('/catalog')}
                        style={{ fontSize: 13, color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer' }}
                    >
                        Смотреть все →
                    </span>
                </div>

                {productsLoading ? (
                    <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
                ) : featuredPage && featuredPage.content.length > 0 ? (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 14 }}>
                        {featuredPage.content.slice(0, 4).map((product) => (
                            <ProductCard
                                key={product.id}
                                product={product}
                                onClick={() => navigate(`/products/${product.id}`)}
                                onAddToCart={handleAddToCart}
                            />
                        ))}
                    </div>
                ) : null}
            </div>

            {/* SERVICE CARDS */}
            <div style={{ paddingTop: 36 }}>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
                    <ServiceCard
                        icon={<DocIcon />}
                        title="Прайс-лист одним XLS"
                        text="Скачайте полный прайс с актуальными остатками и ценами для вашей категории клиента."
                        cta="Скачать прайс"
                    />
                    <ServiceCard
                        icon={<TruckIcon />}
                        title="Доставка по графику"
                        text="Регулярные отгрузки на ваш склад — еженедельно, по согласованной заявке."
                        cta="Настроить график"
                        accent="navy"
                    />
                    <ServiceCard
                        icon={<ShieldIcon />}
                        title="Сертификаты на товар"
                        text="Декларации, паспорта качества и сертификаты ТР ТС в один клик к каждой накладной."
                        cta="Как это работает"
                        accent="green"
                    />
                </div>
            </div>

            {/* TRUST STRIP */}
            <div style={{ paddingTop: 36 }}>
                <div style={{
                    background: '#fff', border: '1px solid var(--line-1)', borderRadius: 10,
                    padding: '22px 28px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 32,
                }}>
                    <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 15, maxWidth: 230, color: 'var(--ink-1)' }}>
                        Нам доверяют 18 200+ организаций
                    </div>
                    <div style={{ display: 'flex', gap: 36, fontSize: 13, fontWeight: 600, color: 'var(--ink-3)', flex: 1, justifyContent: 'space-around' }}>
                        {['РЖД', 'Газпром-нефть', 'Северсталь', 'Магнит', 'Сибур', 'Х5 Group'].map(b => (
                            <span key={b} style={{ letterSpacing: '0.05em' }}>{b}</span>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default HomePage;
