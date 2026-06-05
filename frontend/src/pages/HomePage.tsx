import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spin } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '@/api/products';
import { getCategoryTree } from '@/api/categories';
import ProductCard from '@/features/catalog/ProductCard';
import { useCartStore } from '@/store/cartStore';
import { App } from 'antd';
import type { CategoryTree } from '@/types/product';
import { useSliderStore } from '@/store/sliderStore';
import type { Slide } from '@/types/slider';
import { GRADIENT_PRESETS } from '@/types/slider';

// ── Icons ─────────────────────────────────────────────────────
const ArrRight = ({ width = 16, height = 16 }: { width?: number; height?: number }) => (
    <svg viewBox="0 0 24 24" width={width} height={height} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12h14M13 6l6 6-6 6"/>
    </svg>
);
const DocIcon = () => (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/>
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
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/>
    </svg>
);
const CheckIcon = () => (
    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12"/>
    </svg>
);
const GridIcon = () => (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/>
    </svg>
);
const ChevLeft = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m15 18-6-6 6-6"/>
    </svg>
);
const ChevRight = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="m9 18 6-6-6-6"/>
    </svg>
);

// ── Hero slides: берутся из sliderStore ───────────────────────

// ── Primary category colors (для первых 3 категорий из API) ──
const PRIMARY_COLORS = [
    { bg: 'var(--brand-red)', hover: 'var(--brand-red-hover)' },
    { bg: 'var(--brand-green)', hover: '#155930' },
    { bg: 'var(--brand-navy)', hover: 'var(--brand-navy-hover)' },
];

// ── Helpers ───────────────────────────────────────────────────
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

// ── Sub-components ────────────────────────────────────────────
const SmallBul = ({ children }: { children: React.ReactNode }) => (
    <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 'var(--text-sm)', lineHeight: 1.4 }}>
        <CheckIcon />
        <span>{children}</span>
    </div>
);

const HeroSideJur = ({ onRegister }: { onRegister: () => void }) => (
    <div style={{
        background: 'rgba(255,255,255,.95)', color: 'var(--ink-1)',
        borderRadius: 'var(--r-5)', padding: '16px 18px',
        width: '100%', maxWidth: 320,
    }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
            <BuildingIcon />
            <span style={{ fontSize: 'var(--text-sm)', fontWeight: 600 }}>Для юридических лиц</span>
            <span style={{
                marginLeft: 'auto', display: 'inline-flex', alignItems: 'center',
                height: 18, padding: '0 8px', borderRadius: 9,
                background: 'var(--brand-green-soft)', color: 'var(--brand-green)',
                fontSize: 11, fontWeight: 600,
            }}>−7% к прайсу</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginBottom: 14 }}>
            <SmallBul>Счёт-фактура, ЭДО, отсрочка до 45 дней</SmallBul>
            <SmallBul>Закреплённый менеджер</SmallBul>
            <SmallBul>Госзакупки 44-ФЗ · 223-ФЗ</SmallBul>
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
            <button
                onClick={onRegister}
                style={{
                    flex: 1, height: 32,
                    background: 'var(--brand-red)', color: '#fff',
                    border: 'none', borderRadius: 'var(--r-2)', fontSize: 'var(--text-sm)', fontWeight: 600,
                    cursor: 'pointer', fontFamily: 'var(--font-body)',
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--brand-red)'; }}
            >
                Открыть юр. счёт
            </button>
            <button style={{
                height: 32, padding: '0 12px',
                background: 'transparent', color: 'var(--ink-3)',
                border: '1px solid rgba(0,0,0,.12)', borderRadius: 'var(--r-2)', fontSize: 'var(--text-sm)',
                cursor: 'pointer', fontFamily: 'var(--font-body)',
            }}>
                Я физлицо
            </button>
        </div>
    </div>
);

const HeroSideStats = ({ counts, labels }: { counts: string[]; labels: string[] }) => (
    <div style={{
        background: 'rgba(255,255,255,.10)', backdropFilter: 'blur(6px)',
        border: '1px solid var(--overlay-white-18)',
        borderRadius: 'var(--r-5)', padding: 16,
        width: '100%', maxWidth: 320,
        display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px 16px',
    }}>
        {counts.map((c, i) => (
            <div key={i}>
                <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 'var(--text-3xl)', color: '#fff', letterSpacing: '-0.015em', fontVariantNumeric: 'tabular-nums' }}>{c}</div>
                <div style={{ fontSize: 'var(--text-xs)', color: 'var(--overlay-white-70)', marginTop: 2 }}>{labels[i]}</div>
            </div>
        ))}
    </div>
);

const arrowBtn: React.CSSProperties = {
    width: 30, height: 30, borderRadius: 'var(--r-full)',
    background: 'var(--overlay-white-14)', color: '#fff',
    border: 0, cursor: 'pointer',
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
};

const formatPrice = (p: number) =>
    new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB', minimumFractionDigits: 0 }).format(p);

const getSlideBgStyle = (slide: Slide): React.CSSProperties => {
    if (slide.type === 'image' && slide.imageUrl) {
        const fit = slide.imageFit ?? 'cover';
        return {
            backgroundImage: `url(${slide.imageUrl})`,
            backgroundSize: fit === 'contain' ? 'contain' : fit === 'fill' ? '100% 100%' : 'cover',
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat',
        };
    }
    const grad = slide.gradientPreset === 'custom' ? slide.customGradient : GRADIENT_PRESETS[slide.gradientPreset];
    return { background: grad };
};

const HeroSlider = ({ onRegister, onCatalog }: { onRegister: () => void; onCatalog: () => void }) => {
    const rawSlides = useSliderStore((s) => s.slides);
    const slides = [...rawSlides]
        .filter((s) => s.enabled)
        .sort((a, b) => a.displayOrder - b.displayOrder);

    const [idx, setIdx] = useState(0);
    const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const navigate = useNavigate();

    const activeIdx = Math.min(idx, Math.max(slides.length - 1, 0));

    const resetTimer = () => {
        if (timerRef.current) clearInterval(timerRef.current);
        if (slides.length > 1) {
            timerRef.current = setInterval(() => setIdx((i) => (i + 1) % slides.length), 7000);
        }
    };

    useEffect(() => {
        resetTimer();
        return () => { if (timerRef.current) clearInterval(timerRef.current); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [slides.length]);

    const go = (next: number) => { setIdx(next); resetTimer(); };

    if (slides.length === 0) return null;

    const s = slides[activeIdx];
    const bgStyle = getSlideBgStyle(s);
    const textPos = s.textPosition ?? { x: 5, y: 20 };

    const handleCta1 = () => {
        if (!s.cta1Link) return;
        if (s.cta1Link.startsWith('http')) window.open(s.cta1Link, '_blank');
        else navigate(s.cta1Link);
    };
    const handleCta2 = () => {
        if (!s.cta2Link) return;
        if (s.cta2Link.startsWith('http')) window.open(s.cta2Link, '_blank');
        else navigate(s.cta2Link);
    };

    return (
        <div style={{
            ...bgStyle,
            borderRadius: 'var(--r-5)',
            color: '#fff',
            position: 'relative',
            overflow: 'hidden',
            minHeight: 300,
            transition: 'background .35s ease',
        }}>
            {/* Watermark */}
            <img src="/logo-light.png" alt=""
                style={{
                    position: 'absolute', right: -24, bottom: -28,
                    height: 300, width: 'auto', opacity: .12,
                    pointerEvents: 'none',
                }}
            />

            {/* Text block — абсолютно позиционирован по textPosition */}
            <div style={{
                position: 'absolute',
                left: `${textPos.x}%`,
                top: `${textPos.y}%`,
                maxWidth: 580,
                zIndex: 2,
                padding: '28px 0 60px',
            }}>
                {s.eyebrow && (
                    <div style={{
                        display: 'inline-flex', alignItems: 'center', gap: 8,
                        background: 'var(--overlay-white-12)', padding: '5px 12px', borderRadius: 'var(--r-full)',
                        fontSize: 'var(--text-sm)', marginBottom: 14,
                    }}>
                        <span style={{ width: 6, height: 6, borderRadius: 3, background: '#FFE08A', display: 'inline-block' }}/>
                        <span style={{ fontWeight: 600 }}>{s.eyebrow}</span>
                    </div>
                )}
                {s.title && (
                    <h1 style={{
                        fontFamily: 'var(--font-head)', fontSize: 'var(--text-6xl)', fontWeight: 600,
                        letterSpacing: '-0.022em', lineHeight: 1.12, color: '#fff',
                        margin: '0 0 12px',
                    }}>
                        {s.title}
                    </h1>
                )}
                {s.text && (
                    <p style={{ margin: '0 0 18px', fontSize: 'var(--text-md)', lineHeight: 1.55, color: 'var(--overlay-white-70)' }}>
                        {s.text}
                    </p>
                )}
                <div style={{ display: 'flex', gap: 10 }}>
                    {s.cta1Label && (
                        <button
                            onClick={s.cta1Link === '/catalog' ? onCatalog : handleCta1}
                            style={{
                                display: 'inline-flex', alignItems: 'center', gap: 6,
                                background: 'var(--surface)', color: 'var(--ink-1)', fontWeight: 600,
                                padding: '0 18px', height: 'var(--btn-h-lg)', border: 'none', borderRadius: 'var(--r-3)',
                                fontSize: 'var(--text-md)', cursor: 'pointer', fontFamily: 'var(--font-body)',
                                transition: 'opacity .12s',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.opacity = '.9'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.opacity = '1'; }}
                        >
                            {s.cta1Label} <ArrRight />
                        </button>
                    )}
                    {s.cta2Label && (
                        <button
                            onClick={handleCta2}
                            style={{
                                display: 'inline-flex', alignItems: 'center', gap: 6,
                                background: 'var(--overlay-white-14)', color: '#fff',
                                padding: '0 18px', height: 'var(--btn-h-lg)', border: 0, borderRadius: 'var(--r-3)',
                                fontSize: 'var(--text-md)', cursor: 'pointer', fontFamily: 'var(--font-body)',
                                transition: 'background .12s',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = 'rgba(255,255,255,.22)'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--overlay-white-14)'; }}
                        >
                            <DocIcon /> {s.cta2Label}
                        </button>
                    )}
                </div>
            </div>

            {/* Products block — right side, fixed */}
            {s.products.length > 0 && (
                <div style={{
                    position: 'absolute', right: 36, top: '50%',
                    transform: 'translateY(-50%)',
                    display: 'flex', flexDirection: 'column', gap: 8,
                    width: 280, zIndex: 2,
                }}>
                    {s.products.slice(0, 3).map((p) => (
                        <div
                            key={p.id}
                            onClick={() => navigate(`/products/${p.id}`)}
                            style={{
                                display: 'flex', alignItems: 'center', gap: 10,
                                background: 'rgba(255,255,255,.12)', backdropFilter: 'blur(6px)',
                                border: '1px solid rgba(255,255,255,.2)',
                                borderRadius: 'var(--r-3)', padding: '8px 10px',
                                cursor: 'pointer', transition: 'background .12s',
                            }}
                            onMouseEnter={(e) => { (e.currentTarget as HTMLDivElement).style.background = 'rgba(255,255,255,.2)'; }}
                            onMouseLeave={(e) => { (e.currentTarget as HTMLDivElement).style.background = 'rgba(255,255,255,.12)'; }}
                        >
                            {p.imageUrl && (
                                <div style={{
                                    width: 40, height: 40, borderRadius: 'var(--r-2)',
                                    background: 'rgba(255,255,255,.9)', flexShrink: 0,
                                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                                    overflow: 'hidden',
                                }}>
                                    <img src={p.imageUrl} alt={p.name} style={{ maxWidth: '100%', maxHeight: '100%', objectFit: 'contain' }} />
                                </div>
                            )}
                            <div style={{ flex: 1, minWidth: 0 }}>
                                <div style={{ fontSize: 12, fontWeight: 500, color: '#fff', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                    {p.name}
                                </div>
                                <div style={{ fontSize: 12, fontWeight: 700, color: 'rgba(255,255,255,.9)', fontVariantNumeric: 'tabular-nums' }}>
                                    {formatPrice(p.price)}
                                </div>
                            </div>
                            <ArrRight width={12} height={12} />
                        </div>
                    ))}
                </div>
            )}

            {/* Invisible spacer to give slide height */}
            <div style={{ height: 300, visibility: 'hidden' }} />

            {/* Slider controls */}
            {slides.length > 1 && (
                <div style={{
                    position: 'absolute', left: 36, bottom: 14, right: 36,
                    display: 'flex', alignItems: 'center', gap: 14, zIndex: 3,
                }}>
                    <div style={{ display: 'flex', gap: 6 }}>
                        {slides.map((_, i) => (
                            <button key={i} onClick={() => go(i)} style={{
                                width: i === activeIdx ? 28 : 8, height: 4, borderRadius: 2,
                                background: i === activeIdx ? '#fff' : 'rgba(255,255,255,.4)',
                                border: 0, padding: 0, cursor: 'pointer',
                                transition: 'width .25s, background .25s',
                            }}/>
                        ))}
                    </div>
                    <div style={{ flex: 1 }}/>
                    <span style={{ fontSize: 'var(--text-xs)', color: 'var(--overlay-white-60)', fontVariantNumeric: 'tabular-nums' }}>
                        {String(activeIdx + 1).padStart(2, '0')} / {String(slides.length).padStart(2, '0')}
                    </span>
                    <button onClick={() => go((activeIdx - 1 + slides.length) % slides.length)} style={arrowBtn}>
                        <ChevLeft />
                    </button>
                    <button onClick={() => go((activeIdx + 1) % slides.length)} style={arrowBtn}>
                        <ChevRight />
                    </button>
                </div>
            )}
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
            onClick={onClick}
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
                <span style={{ fontSize: 13, fontWeight: 500 }}>Перейти в раздел</span>
                <ArrRight />
            </div>
        </div>
    );
};

const SecondaryCatTile = ({ category, onClick }: { category: CategoryTree; onClick: () => void }) => {
    const [hovered, setHovered] = useState(false);
    return (
        <div
            onClick={onClick}
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
                <div style={{ fontSize: 13, fontWeight: 500, color: 'var(--ink-1)', lineHeight: 1.3 }}>{category.name}</div>
            </div>
            <ArrRight width={14} height={14} />
        </div>
    );
};

const ServiceCard = ({ icon, title, text, cta, accent }: { icon: React.ReactNode; title: string; text: string; cta: string; accent?: string }) => {
    const color = accent === 'navy' ? 'var(--brand-navy)' : accent === 'green' ? 'var(--brand-green)' : 'var(--brand-red)';
    const tint  = accent === 'navy' ? 'var(--navy-tint)' : accent === 'green' ? 'var(--brand-green-soft)' : 'var(--red-tint)';
    return (
        <div style={{ background: 'var(--surface)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-5)', padding: 22, display: 'flex', gap: 16 }}>
            <div style={{
                width: 44, height: 44, borderRadius: 'var(--r-4)',
                background: tint, color, flex: '0 0 auto',
                display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            }}>{icon}</div>
            <div style={{ flex: 1 }}>
                <div style={{ fontSize: 15, fontWeight: 600, marginBottom: 4, color: 'var(--ink-1)' }}>{title}</div>
                <p style={{ fontSize: 13, color: 'var(--ink-3)', lineHeight: 1.5, margin: 0 }}>{text}</p>
                <div style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginTop: 10, fontSize: 13, fontWeight: 600, color, cursor: 'pointer' }}>
                    {cta} <ArrRight width={14} height={14} />
                </div>
            </div>
        </div>
    );
};

// ── Main ──────────────────────────────────────────────────────
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
    const primaryCategories = allCategories.slice(0, 3);
    const secondaryCategories = allCategories.slice(3, 11);

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
            {/* HERO SLIDER */}
            <div style={{ padding: '20px 0 0' }}>
                <HeroSlider
                    onRegister={() => navigate('/register')}
                    onCatalog={() => navigate('/catalog')}
                />
            </div>

            {/* PRIMARY 3 CATEGORIES */}
            <div style={{ paddingTop: 36 }}>
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', marginBottom: 16 }}>
                    <div>
                        <h2 style={{ fontFamily: 'var(--font-head)', fontSize: 22, fontWeight: 600, letterSpacing: '-0.012em', color: 'var(--ink-1)', margin: 0 }}>
                            Основные направления
                        </h2>
                        <p style={{ fontSize: 13, color: 'var(--ink-3)', marginTop: 4, marginBottom: 0 }}>
                            Три направления, которые закрывают 80% заявок снабженца
                        </p>
                    </div>
                    <span
                        onClick={() => navigate('/catalog')}
                        style={{ fontSize: 13, color: 'var(--brand-navy)', fontWeight: 500, cursor: 'pointer', whiteSpace: 'nowrap' }}
                    >
                        Все разделы каталога →
                    </span>
                </div>

                {categoriesLoading ? (
                    <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
                ) : primaryCategories.length > 0 ? (
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 14 }}>
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
                    <div style={{ textAlign: 'center', padding: 40, color: 'var(--ink-3)', fontSize: 14 }}>
                        Категории загружаются...
                    </div>
                )}
            </div>

            {/* SECONDARY CATEGORIES */}
            {secondaryCategories.length > 0 && (
                <div style={{ paddingTop: 20 }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 12 }}>
                        Сопутствующие категории
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 10 }}>
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

            {/* FEATURED PRODUCTS */}
            <div style={{ paddingTop: 40 }}>
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
                    <div style={{
                        display: 'flex', gap: 14, overflowX: 'auto',
                        scrollSnapType: 'x mandatory',
                        paddingBottom: 8,
                        scrollbarWidth: 'thin',
                        scrollbarColor: 'var(--line-2) transparent',
                    }}>
                        {featuredPage.content.map((product) => (
                            <div key={product.id} style={{ flex: '0 0 auto', scrollSnapAlign: 'start' }}>
                                <ProductCard
                                    product={product}
                                    onClick={() => navigate(`/products/${product.id}`)}
                                    onAddToCart={handleAddToCart}
                                />
                            </div>
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
                    background: 'var(--surface)', border: '1px solid var(--line-1)', borderRadius: 'var(--r-5)',
                    padding: '22px 28px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 32,
                }}>
                    <div style={{ fontFamily: 'var(--font-head)', fontWeight: 600, fontSize: 15, maxWidth: 230, color: 'var(--ink-1)' }}>
                        Нам доверяют 18 200+ организаций
                    </div>
                    <div style={{ display: 'flex', gap: 36, fontSize: 13, fontWeight: 600, color: 'var(--ink-3)', flex: 1, justifyContent: 'space-around' }}>
                        {['РЖД', 'Газпром-нефть', 'Северсталь', 'Магнит', 'Сибур', 'Х5 Group'].map((b) => (
                            <span key={b} style={{ letterSpacing: '0.05em' }}>{b}</span>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default HomePage;
