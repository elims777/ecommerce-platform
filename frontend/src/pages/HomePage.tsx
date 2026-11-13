import { useState, useRef, useEffect } from 'react';
import type { CSSProperties } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { ClickableCard } from '@/components/navigation';
import { Spin, Grid, App } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getProducts, getFeaturedProducts, getSaleProducts } from '@/api/products';
import { getLatestNews } from '@/api/news';
import { getCategoryTree } from '@/api/categories';
import ProductCard from '@/features/catalog/ProductCard';
import { useCartStore } from '@/store/cartStore';
import { useAuthStore } from '@/store/authStore';
import type { CategoryTree } from '@/types/product';
import type { News as NewsItem } from '@/types/news';
import { handleProfileIncomplete } from '@/utils/profileGate';
import PriceListModal from '@/components/PriceListModal';

// Акционные товары — метка на товаре или на его категории (без привязки к конкретной категории).

// ── Icons ─────────────────────────────────────────────────────
const ArrRight = ({ width = 16, height = 16 }: { width?: number; height?: number }) => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width={width} height={height} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12h14M13 6l6 6-6 6"/>
    </svg>
);
const DocIcon = () => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
    </svg>
);
const TruckIcon = () => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 6h12v10H2zM14 10h4l3 3v3h-7"/><circle cx="6.5" cy="17.5" r="1.7"/><circle cx="17" cy="17.5" r="1.7"/>
    </svg>
);
const ShieldIcon = () => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
    </svg>
);
const GridIcon = () => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
    </svg>
);
const ChevLeft = () => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m15 18-6-6 6-6"/>
    </svg>
);
const ChevRight = () => (
    <svg aria-hidden="true" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m9 18 6-6-6-6"/>
    </svg>
);
// ── Primary category colors (для первых 3 категорий из API) ──
const PRIMARY_COLORS = [
    { bg: 'var(--brand-red)', hover: 'var(--brand-red-hover)' },
    { bg: 'var(--brand-green)', hover: '#155930' },
    { bg: 'var(--brand-navy)', hover: 'var(--brand-navy-hover)' },
];

// Служебная категория «Импорт из 1С» — не показываем на главной.
const HIDDEN_CATEGORY_IDS = new Set([1]);

const formatNewsDate = (iso: string | null): string =>
    iso ? new Date(iso).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long' }) : '';

const NEWS_SLIDE_INTERVAL_MS = 3000;

/** Слайдер новостей: обложка на весь блок, автолистание с паузой при наведении. */
const NewsSlider = ({ news }: { news: NewsItem[] }) => {
    const [index, setIndex] = useState(0);
    const pausedRef = useRef(false);

    useEffect(() => {
        if (news.length < 2) return;
        const id = setInterval(() => {
            if (!pausedRef.current) setIndex((i) => (i + 1) % news.length);
        }, NEWS_SLIDE_INTERVAL_MS);
        return () => clearInterval(id);
    }, [news.length]);

    return (
        <div
            style={{ position: 'relative', flex: 1, minHeight: 260, borderRadius: 'var(--r-4)', overflow: 'hidden', background: 'var(--brand-navy)' }}
            onMouseEnter={() => { pausedRef.current = true; }}
            onMouseLeave={() => { pausedRef.current = false; }}
        >
            {news.map((item, i) => (
                <ClickableCard
                    key={item.id}
                    to={`/news/${item.slug}`}
                    aria-hidden={i !== index}
                    tabIndex={i === index ? 0 : -1}
                    style={{
                        position: 'absolute', inset: 0, display: 'block',
                        opacity: i === index ? 1 : 0, transition: 'opacity .6s',
                        pointerEvents: i === index ? 'auto' : 'none',
                    }}
                >
                    {item.coverImageUrl && (
                        <img
                            src={item.coverImageUrl}
                            alt=""
                            loading="lazy"
                            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                        />
                    )}
                    {/* усиленный скрим: градиент с трети высоты + тень на тексте — читается на любой обложке */}
                    <div style={{
                        position: 'absolute', left: 0, right: 0, bottom: 0,
                        padding: '96px 18px 30px',
                        background: 'linear-gradient(transparent, rgba(0,0,0,.45) 35%, rgba(0,0,0,.88) 75%)',
                        color: '#fff',
                    }}>
                        <div style={{
                            fontSize: 'var(--text-lg)', fontWeight: 600, lineHeight: 1.3,
                            textShadow: '0 1px 8px rgba(0,0,0,.45)',
                            display: '-webkit-box', WebkitBoxOrient: 'vertical', WebkitLineClamp: 2,
                            overflow: 'hidden',
                        }}>
                            {item.title}
                        </div>
                        <div style={{ fontSize: 'var(--text-sm)', color: 'rgba(255,255,255,.9)', marginTop: 4, textShadow: '0 1px 6px rgba(0,0,0,.4)' }}>
                            {formatNewsDate(item.publishedAt)}
                        </div>
                    </div>
                </ClickableCard>
            ))}
            {news.length > 1 && (
                <div style={{ position: 'absolute', bottom: 10, left: 0, right: 0, display: 'flex', justifyContent: 'center', gap: 6 }}>
                    {news.map((item, i) => (
                        <button
                            key={item.id}
                            type="button"
                            aria-label={`Новость ${i + 1}`}
                            onClick={() => setIndex(i)}
                            style={{
                                // как точки фото в карточке товара: активная — вытянутая
                                width: i === index ? 16 : 6, height: 6, padding: 0, border: 'none', cursor: 'pointer',
                                borderRadius: 3, transition: 'width .2s',
                                background: i === index ? '#fff' : 'rgba(255,255,255,.55)',
                                boxShadow: '0 1px 4px rgba(0,0,0,.5)',
                            }}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

type ShowcaseTab = 'hits' | 'sales' | 'new';

const SHOWCASE_TABS: { key: ShowcaseTab; label: string }[] = [
    { key: 'hits', label: 'Хиты продаж' },
    { key: 'sales', label: 'Акции' },
    { key: 'new', label: 'Новинки' },
];

const SHOWCASE_SCROLL_STEP = 320; // ширина карточки + gap

// общая строка заголовка колонок витрины: одинаковая высота → карусель и новости начинаются на одной линии
const SHOWCASE_HEAD_ROW: CSSProperties = { display: 'flex', alignItems: 'baseline', marginBottom: 18, minHeight: 40 };
const SHOWCASE_H2: CSSProperties = {
    fontFamily: 'var(--font-head)', fontSize: 'var(--text-3xl)', fontWeight: 600,
    letterSpacing: '-0.012em', color: 'var(--ink-1)', margin: 0,
};

const SHOWCASE_ADVANTAGES = [
    { icon: <GridIcon />, text: 'Широкий ассортимент' },
    { icon: <TruckIcon />, text: 'Поставка в короткие сроки' },
    { icon: <ShieldIcon />, text: 'Участвуем в торгах по 44 и 223 ФЗ' },
    { icon: <DocIcon />, text: 'Гибкие условия оплаты' },
];

const Showcase = ({ onAddToCart }: { onAddToCart: (productId: number) => void }) => {
    const [tab, setTab] = useState<ShowcaseTab>('hits');
    const screens = Grid.useBreakpoint();
    const isMobile = screens.md === false;
    // Боковая колонка «Новости и акции» помещается рядом с каруселью только от lg (992).
    const showSidebar = screens.lg === true;
    const scrollRef = useRef<HTMLDivElement>(null);

    const { data: hitsPage, isLoading: hitsLoading } = useQuery({
        queryKey: ['showcase', 'hits'],
        queryFn: getFeaturedProducts,
        enabled: tab === 'hits',
        staleTime: 60_000,
    });
    const { data: salesPage, isLoading: salesLoading } = useQuery({
        queryKey: ['showcase', 'sales'],
        queryFn: () => getSaleProducts({ size: 10 }),
        enabled: tab === 'sales',
        staleTime: 60_000,
    });
    const { data: newPage, isLoading: newLoading } = useQuery({
        queryKey: ['showcase', 'new'],
        queryFn: () => getProducts({ size: 10, sort: 'createdAt,desc' }),
        enabled: tab === 'new',
        staleTime: 60_000,
    });

    const activeData = tab === 'hits' ? hitsPage : tab === 'sales' ? salesPage : newPage;
    const activeLoading = tab === 'hits' ? hitsLoading : tab === 'sales' ? salesLoading : newLoading;
    const products = activeData?.content ?? [];

    // Правая колонка: последние новости. Запрашиваем только когда колонка видна (от lg).
    const { data: latestNews = [] } = useQuery({
        queryKey: ['news', 'latest'],
        queryFn: () => getLatestNews(5),
        enabled: showSidebar,
        staleTime: 5 * 60 * 1000,
    });

    const scrollByAmount = (dir: 1 | -1) => {
        scrollRef.current?.scrollBy({ left: dir * SHOWCASE_SCROLL_STEP, behavior: 'smooth' });
    };

    const pausedRef = useRef(false);

    // при смене таба показываем карусель с начала и снимаем паузу
    useEffect(() => {
        scrollRef.current?.scrollTo({ left: 0 });
        pausedRef.current = false;
    }, [tab]);

    // автопрокрутка карусели: раз в 3с сдвиг на карточку, в конце — возврат в начало; пауза при наведении
    useEffect(() => {
        if (isMobile || products.length === 0) return;
        const id = setInterval(() => {
            const el = scrollRef.current;
            if (!el || pausedRef.current) return;
            if (el.scrollLeft + el.clientWidth >= el.scrollWidth - 4) {
                el.scrollTo({ left: 0, behavior: 'smooth' });
            } else {
                el.scrollBy({ left: SHOWCASE_SCROLL_STEP, behavior: 'smooth' });
            }
        }, 3000);
        return () => clearInterval(id);
    }, [isMobile, products.length, tab]);

    return (
        <div>
            {/* Две колонки, у каждой свой заголовок: верх и низ карусели и новостей совпадают */}
            <div style={{ display: 'flex', gap: 16, flexDirection: isMobile ? 'column' : 'row' }}>
                <div style={{ flex: showSidebar ? '0 0 68%' : '1 1 auto', minWidth: 0, display: 'flex', flexDirection: 'column' }}>
                    <div style={{ ...SHOWCASE_HEAD_ROW, gap: 24, flexWrap: 'wrap' }}>
                        <h2 style={SHOWCASE_H2}>Витрина</h2>
                        <div style={{ display: 'flex', gap: 20 }}>
                            {SHOWCASE_TABS.map((t) => (
                                <button
                                    key={t.key}
                                    type="button"
                                    onClick={() => setTab(t.key)}
                                    style={{
                                        background: 'none', border: 'none', cursor: 'pointer',
                                        fontFamily: 'var(--font-body)', fontSize: 'var(--text-base)',
                                        padding: '4px 0',
                                        borderBottom: tab === t.key ? '2px solid var(--brand-red)' : '2px solid transparent',
                                        color: tab === t.key ? 'var(--ink-1)' : 'var(--ink-3)',
                                        fontWeight: tab === t.key ? 600 : 400,
                                    }}
                                >
                                    {t.label}
                                </button>
                            ))}
                        </div>
                    </div>
                    <div
                        style={{ position: 'relative' }}
                        onMouseEnter={() => { pausedRef.current = true; }}
                        onMouseLeave={() => { pausedRef.current = false; }}
                    >
                        {activeLoading ? (
                            <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>
                        ) : products.length > 0 ? (
                            <>
                                <div
                                    ref={scrollRef}
                                    style={{
                                        display: 'flex', gap: 14, overflowX: 'auto',
                                        scrollSnapType: 'x mandatory',
                                        // полоса прокрутки не нужна: есть стрелки, автопрокрутка и свайп
                                        scrollbarWidth: 'none',
                                    }}
                                >
                                    {products.map((product) => (
                                        <div key={product.id} style={{ flex: '0 0 auto', scrollSnapAlign: 'start' }}>
                                            <ProductCard product={product} onAddToCart={onAddToCart} />
                                        </div>
                                    ))}
                                </div>
                                {!isMobile && (
                                    <>
                                        <button
                                            type="button"
                                            aria-label="Прокрутить назад"
                                            onClick={() => scrollByAmount(-1)}
                                            style={{
                                                position: 'absolute', left: -14, top: '40%',
                                                width: 32, height: 32, borderRadius: 'var(--r-full)',
                                                background: 'var(--surface)', border: '1px solid var(--line-1)',
                                                color: 'var(--ink-1)', cursor: 'pointer',
                                                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                                                boxShadow: 'var(--shadow-2)',
                                            }}
                                        >
                                            <ChevLeft />
                                        </button>
                                        <button
                                            type="button"
                                            aria-label="Прокрутить вперёд"
                                            onClick={() => scrollByAmount(1)}
                                            style={{
                                                position: 'absolute', right: -14, top: '40%',
                                                width: 32, height: 32, borderRadius: 'var(--r-full)',
                                                background: 'var(--surface)', border: '1px solid var(--line-1)',
                                                color: 'var(--ink-1)', cursor: 'pointer',
                                                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                                                boxShadow: 'var(--shadow-2)',
                                            }}
                                        >
                                            <ChevRight />
                                        </button>
                                    </>
                                )}
                            </>
                        ) : null}
                    </div>
                </div>

                {showSidebar && (
                    <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
                        <div style={{ ...SHOWCASE_HEAD_ROW, justifyContent: 'space-between', gap: 8 }}>
                            <h2 style={SHOWCASE_H2}>Новости</h2>
                            {latestNews.length > 0 && (
                                <Link to="/news" style={{ fontSize: 'var(--text-base)', color: 'var(--brand-navy)', fontWeight: 500 }}>
                                    Все новости
                                </Link>
                            )}
                        </div>
                        {latestNews.length === 0 ? (
                            <div style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                                Новостей пока нет
                            </div>
                        ) : (
                            <NewsSlider news={latestNews} />
                        )}
                    </div>
                )}
            </div>

            {/* Полоса преимуществ */}
            <div style={{
                marginTop: 20, width: '100%',
                display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(210px, 1fr))', gap: 10,
            }}>
                {SHOWCASE_ADVANTAGES.map(({ icon, text }) => (
                    <div key={text} style={{
                        background: 'var(--surface)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-4)',
                        padding: '14px 16px', display: 'flex', alignItems: 'center', gap: 10,
                    }}>
                        <span style={{ color: 'var(--brand-navy)', display: 'inline-flex', flex: '0 0 auto' }}>{icon}</span>
                        <span style={{ fontSize: 'var(--text-base)', fontWeight: 500, color: 'var(--ink-1)' }}>{text}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};

const PrimaryGroupCard = ({ category, color, hoverColor, index, onClick }: {
    category: CategoryTree;
    color: string;
    hoverColor: string;
    index: number;
    onClick: () => void;
}) => {
    const [hovered, setHovered] = useState(false);
    const tags = ['143 заказа в мае', 'Сезон 2026', 'Хит госзакупок'];

    return (
        <div
            onClick={onClick} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } }}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
            style={{
                background: hovered ? hoverColor : color,
                borderRadius: 'var(--r-5)',
                padding: 20,
                color: '#fff',
                display: 'flex', flexDirection: 'column',
                position: 'relative', overflow: 'hidden',
                minHeight: 220,
                cursor: 'pointer',
                transition: 'background .15s',
            }}
        >
            {/* Watermark icon */}
            <div style={{ position: 'absolute', right: -16, top: -10, opacity: .12 }}>
                <GridIcon />
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                <div style={{
                    width: 38, height: 38, borderRadius: 'var(--r-4)',
                    background: 'rgba(255,255,255,.15)',
                    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                }}>
                    <GridIcon />
                </div>
                <span style={{ fontSize: 'var(--text-xs)', padding: '3px 8px', borderRadius: 'var(--r-full)', background: 'var(--overlay-white-18)', fontWeight: 500 }}>
                    {tags[index] ?? ''}
                </span>
            </div>

            <h3 style={{
                fontFamily: 'var(--font-head)', fontSize: 'var(--text-2xl)', fontWeight: 600, color: '#fff',
                letterSpacing: '-0.018em', lineHeight: 1.15,
                marginBottom: 'auto',
            }}>
                {category.name}
            </h3>

            <div style={{ marginTop: 16, borderTop: '1px solid rgba(255,255,255,.18)', paddingTop: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                <span style={{ fontSize: 'var(--text-base)', fontWeight: 500 }}>Перейти в раздел</span>
                <ArrRight />
            </div>
        </div>
    );
};

const SecondaryCatTile = ({ category, onClick }: { category: CategoryTree; onClick: () => void }) => {
    const [hovered, setHovered] = useState(false);
    return (
        <div
            onClick={onClick} role="button" tabIndex={0} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } }}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
            style={{
                background: 'var(--surface)', border: '1px solid var(--line-1)',
                borderRadius: 'var(--r-4)', padding: '14px 14px',
                display: 'flex', alignItems: 'center', gap: 12,
                cursor: 'pointer',
                boxShadow: hovered ? 'var(--shadow-2)' : 'none',
                transform: hovered ? 'translateY(-1px)' : 'none',
                transition: 'box-shadow .15s, transform .15s',
            }}
        >
            <div style={{
                width: 36, height: 36, borderRadius: 'var(--r-3)',
                background: 'var(--surface-2)', color: 'var(--brand-navy)',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                flex: '0 0 auto',
            }}>
                <GridIcon />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 'var(--text-base)', fontWeight: 500, color: 'var(--ink-1)', lineHeight: 1.3 }}>{category.name}</div>
            </div>
            <ArrRight width={14} height={14} />
        </div>
    );
};

const ServiceCard = ({ icon, title, text, cta, accent, onClick }: { icon: React.ReactNode; title: string; text: string; cta: string; accent?: string; onClick?: () => void }) => {
    const color = accent === 'navy' ? 'var(--brand-navy)' : accent === 'green' ? 'var(--brand-green)' : 'var(--brand-red)';
    const tint  = accent === 'navy' ? 'var(--navy-tint)' : accent === 'green' ? 'var(--brand-green-soft)' : 'var(--red-tint)';
    return (
        <div
            onClick={onClick} role={onClick ? 'button' : undefined} tabIndex={onClick ? 0 : undefined} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick?.(); } }}
            style={{ background: 'var(--surface)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-5)', padding: 22, display: 'flex', gap: 16, cursor: onClick ? 'pointer' : undefined }}
        >
            <div style={{
                width: 44, height: 44, borderRadius: 'var(--r-4)',
                background: tint, color, flex: '0 0 auto',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            }}>{icon}</div>
            <div style={{ flex: 1 }}>
                <div style={{ fontSize: 'var(--text-lg)', fontWeight: 600, marginBottom: 4, color: 'var(--ink-1)' }}>{title}</div>
                <p style={{ fontSize: 'var(--text-base)', color: 'var(--ink-3)', lineHeight: 1.5, margin: 0 }}>{text}</p>
                <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginTop: 10, fontSize: 'var(--text-base)', fontWeight: 600, color, cursor: 'pointer' }}>
                    {cta} <ArrRight width={14} height={14} />
                </div>
            </div>
        </div>
    );
};

// ── Main ──────────────────────────────────────────────────────
const HomePage = () => {
    const navigate = useNavigate();
    const { message: messageApi, modal } = App.useApp();
    const addItem = useCartStore((state) => state.addItem);
    const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
    const [priceListModalOpen, setPriceListModalOpen] = useState(false);

    const { data: categoryTree = [], isLoading: categoriesLoading } = useQuery({
        queryKey: ['categories', 'tree'],
        queryFn: getCategoryTree,
        staleTime: 5 * 60 * 1000,
    });

    const rootCategories = categoryTree.filter((c) => !HIDDEN_CATEGORY_IDS.has(c.id));
    const primaryCategories = rootCategories.slice(0, 3);
    const secondaryCategories = rootCategories.slice(3, 11);

    const handleAddToCart = async (productId: number) => {
        if (!isAuthenticated) { void navigate('/login'); return; }
        try {
            await addItem(productId, 1);
            messageApi.success('Товар добавлен в корзину');
        } catch (err) {
            if (handleProfileIncomplete(err, modal, navigate)) return;
            messageApi.error('Ошибка при добавлении в корзину');
        }
    };

    const handleCategoryClick = (categoryId: number) => {
        void navigate(`/catalog?category=${categoryId}`);
    };

    const handlePriceListClick = () => {
        if (!isAuthenticated) { void navigate('/login'); return; }
        setPriceListModalOpen(true);
    };

    return (
        <div style={{ paddingBottom: 'var(--sp-20)' }}>
            {/* SHOWCASE */}
            <div style={{ padding: '20px 0 0' }}>
                <Showcase onAddToCart={handleAddToCart} />
            </div>

            {/* PRIMARY 3 CATEGORIES */}
            <div style={{ paddingTop: 36 }}>
                <div style={{ marginBottom: 16 }}>
                    <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-3xl)', fontWeight: 600, letterSpacing: '-0.012em', color: 'var(--ink-1)', margin: 0 }}>
                        Основные направления
                    </h2>
                    <p style={{ fontSize: 'var(--text-base)', color: 'var(--ink-3)', marginTop: 4, marginBottom: 0 }}>
                        Три направления, которые закрывают 80% заявок снабженца
                    </p>
                </div>

                {categoriesLoading ? (
                    <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
                ) : primaryCategories.length > 0 ? (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 14 }}>
                        {primaryCategories.map((cat, i) => (
                            <PrimaryGroupCard
                                key={cat.id}
                                category={cat}
                                color={PRIMARY_COLORS[i].bg}
                                hoverColor={PRIMARY_COLORS[i].hover}
                                index={i}
                                onClick={() => handleCategoryClick(cat.id)}
                            />
                        ))}
                    </div>
                ) : (
                    <div style={{ textAlign: 'center', padding: 40, color: 'var(--ink-3)', fontSize: 'var(--text-md)' }}>
                        Категории загружаются...
                    </div>
                )}
            </div>

            {/* SECONDARY CATEGORIES */}
            {secondaryCategories.length > 0 && (
                <div style={{ paddingTop: 20 }}>
                    <div style={{ fontSize: 'var(--text-sm)', fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 12 }}>
                        Сопутствующие категории
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 10 }}>
                        {secondaryCategories.map((cat) => (
                            <SecondaryCatTile
                                key={cat.id}
                                category={cat}
                                onClick={() => handleCategoryClick(cat.id)}
                            />
                        ))}
                    </div>
                </div>
            )}

            {/* SERVICE CARDS */}
            <div style={{ paddingTop: 36 }}>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 14 }}>
                    <ServiceCard
                        icon={<DocIcon />}
                        title="Прайс-лист одним кликом в XLS"
                        text="Выберите нужные категории — сформируем актуальный прайс с ценами для вашей категории клиента."
                        cta="Заказать прайс"
                        onClick={handlePriceListClick}
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

            <PriceListModal open={priceListModalOpen} onClose={() => setPriceListModalOpen(false)} />
        </div>
    );
};

export default HomePage;
