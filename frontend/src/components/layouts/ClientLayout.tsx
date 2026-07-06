import { useRef, useState, useEffect } from 'react';
import { Dropdown } from 'antd';
import { company } from '@/config/company';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { NavLink } from '@/components/navigation';
import { motion, AnimatePresence } from 'framer-motion';
import { useQuery } from '@tanstack/react-query';
import { getAvailableProductsCount } from '@/api/products';
import { useAuthStore } from '@/store/authStore';
import { useCartStore } from '@/store/cartStore';
import { useFavouritesStore } from '@/store/favouritesStore';
import { isAdmin } from '@/types/auth';
import type { ClientType } from '@/types/auth';
import type { MenuProps } from 'antd';

const TOPBAR_H = 36;
const MAIN_H = 76;
const CAT_H = 46;
const HEADER_TOTAL = TOPBAR_H + MAIN_H + CAT_H;

const PAGE_ORDER = ['/about', '/contacts', '/', '/catalog', '/cart', '/orders', '/profile', '/privacy-policy', '/personal-data'];
const getPageIndex = (pathname: string): number => {
    if (pathname.startsWith('/products/')) return 2.5;
    if (pathname.startsWith('/checkout')) return 4.5;
    const idx = PAGE_ORDER.indexOf(pathname);
    return idx >= 0 ? idx : 5;
};

const SearchIcon = () => (
    <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>
    </svg>
);
const CartIcon = () => (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 4h2.5l2 13.5h11l2-9h-14"/><circle cx="9" cy="20.5" r="1.4"/><circle cx="18" cy="20.5" r="1.4"/>
    </svg>
);
const UserIcon = () => (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="8.5" r="3.8"/><path d="M4.5 20c1.2-3.6 4.2-5.5 7.5-5.5s6.3 1.9 7.5 5.5"/>
    </svg>
);
const HeartIcon = () => (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10z"/>
    </svg>
);
const PhoneIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 4h3l2 5-2.5 1.5a11 11 0 0 0 6 6L15 14l5 2v3a2 2 0 0 1-2 2A15 15 0 0 1 3 6a2 2 0 0 1 2-2z"/>
    </svg>
);
const TruckIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2 6h12v10H2zM14 10h4l3 3v3h-7"/><circle cx="6.5" cy="17.5" r="1.7"/><circle cx="17" cy="17.5" r="1.7"/>
    </svg>
);
const PinIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 21s-7-7.5-7-12a7 7 0 0 1 14 0c0 4.5-7 12-7 12z"/><circle cx="12" cy="9" r="2.5"/>
    </svg>
);
const MenuIcon = () => (
    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round">
        <path d="M4 7h16M4 12h16M4 17h16"/>
    </svg>
);



const ContextSwitcher = () => {
    const { user, switchContext } = useAuthStore();
    const fetchCart = useCartStore((s) => s.fetchCart);
    const [loading, setLoading] = useState(false);
    const [showPasswordModal, setShowPasswordModal] = useState(false);
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');

    if (!user?.companyName) return null;

    const handleSwitch = async (target: ClientType) => {
        if (target === user.clientType) return;
        if (target === 'B2C') { setShowPasswordModal(true); return; }
        setLoading(true);
        try {
            await switchContext('B2B');
            await fetchCart();
        } finally {
            setLoading(false);
        }
    };

    const handlePasswordSubmit = async () => {
        if (!password) return;
        setLoading(true);
        setError('');
        try {
            await switchContext('B2C', password);
            await fetchCart();
            setShowPasswordModal(false);
            setPassword('');
        } catch {
            setError('Неверный пароль');
        } finally {
            setLoading(false);
        }
    };

    const active = user.clientType;

    return (
        <>
            <div style={{ display: 'flex', alignItems: 'center', border: '1px solid var(--line-2)', borderRadius: 'var(--r-3)', overflow: 'hidden', opacity: loading ? 0.6 : 1 }}>
                {(['B2C', 'B2B'] as const).map((type) => (
                    <button key={type} onClick={() => handleSwitch(type)} disabled={loading} style={{
                        padding: '4px 10px', fontSize: 'var(--text-sm)', fontWeight: 600, border: 'none',
                        cursor: loading ? 'default' : 'pointer', fontFamily: 'var(--font-body)',
                        background: active === type ? 'var(--brand-red)' : 'transparent',
                        color: active === type ? '#fff' : 'var(--ink-2)',
                        transition: 'background 0.15s, color 0.15s',
                    }}>
                        {type === 'B2C' ? 'Физлицо' : 'Организация'}
                    </button>
                ))}
            </div>

            {showPasswordModal && (
                <div style={{ position: 'fixed', inset: 0, background: 'var(--overlay-dark)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 'var(--z-modal)' as any }}
                    onClick={() => { setShowPasswordModal(false); setPassword(''); setError(''); }}>
                    <div style={{ background: 'var(--surface)', borderRadius: 'var(--r-5)', padding: 'var(--sp-10)', width: 360 }}
                        onClick={(e) => e.stopPropagation()}>
                        <div style={{ fontWeight: 600, fontSize: 'var(--text-xl)', marginBottom: 8 }}>Переключение на личный аккаунт</div>
                        <div style={{ fontSize: 'var(--text-base)', color: 'var(--ink-3)', marginBottom: 16 }}>Введите пароль от личного аккаунта</div>
                        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                            onKeyDown={(e) => { if (e.key === 'Enter') handlePasswordSubmit(); }}
                            placeholder="Пароль" autoFocus
                            style={{
                                width: '100%', height: 40, padding: '0 12px',
                                border: `1px solid ${error ? 'var(--brand-red)' : 'var(--line-2)'}`,
                                borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontFamily: 'var(--font-body)',
                                outline: 'none', boxSizing: 'border-box', marginBottom: error ? 6 : 16,
                            }} />
                        {error && <div style={{ fontSize: 'var(--text-sm)', color: 'var(--brand-red)', marginBottom: 12 }}>{error}</div>}
                        <div style={{ display: 'flex', gap: 8 }}>
                            <button onClick={handlePasswordSubmit} disabled={loading || !password} style={{
                                flex: 1, height: 38, background: 'var(--brand-red)', color: '#fff',
                                border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500,
                                cursor: loading || !password ? 'default' : 'pointer', fontFamily: 'var(--font-body)',
                                opacity: !password ? 0.6 : 1,
                            }}>{loading ? 'Проверка...' : 'Войти'}</button>
                            <button onClick={() => { setShowPasswordModal(false); setPassword(''); setError(''); }} style={{
                                height: 38, padding: '0 16px', border: '1px solid var(--line-2)',
                                background: 'transparent', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)',
                                cursor: 'pointer', fontFamily: 'var(--font-body)', color: 'var(--ink-2)',
                            }}>Отмена</button>
                        </div>
                    </div>
                </div>
            )}
        </>
    );
};

const ClientLayout = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { isAuthenticated, user, logout } = useAuthStore();
    const totalItems = useCartStore((state) => state.totalItems);
    const fetchCart = useCartStore((state) => state.fetchCart);
    const fetchFavouriteIds = useFavouritesStore((s) => s.fetchIds);
    const clearFavourites = useFavouritesStore((s) => s.clear);
    const totalFavourites = useFavouritesStore((s) => s.ids.size);
    const [searchQuery, setSearchQuery] = useState('');
    const prevIndexRef = useRef(getPageIndex(location.pathname));

    const { data: availableCount } = useQuery({
        queryKey: ['products', 'count-available'],
        queryFn: getAvailableProductsCount,
        staleTime: 5 * 60 * 1000,
    });

    useEffect(() => {
        if (isAuthenticated) {
            fetchCart();
            fetchFavouriteIds();
        } else {
            clearFavourites();
        }
    }, [isAuthenticated, fetchCart, fetchFavouriteIds, clearFavourites]);

    const handleLogout = async () => {
        await logout();
        navigate('/');
    };

    const currentIndex = getPageIndex(location.pathname);
    prevIndexRef.current = currentIndex;

    const userMenuItems: MenuProps['items'] = [
        { key: 'orders', label: <NavLink to="/orders">Мои заказы</NavLink> },
        { key: 'profile', label: <NavLink to="/profile">Профиль</NavLink> },
        ...(user && !user.companyName ? [
            { type: 'divider' as const },
            { key: 'connect-org', label: <NavLink to="/profile">Подключить организацию</NavLink> },
        ] : []),
        ...(user && isAdmin(user) ? [
            { type: 'divider' as const },
            { key: 'admin', label: <NavLink to="/admin">Админ-панель</NavLink> },
        ] : []),
        { type: 'divider' as const },
        { key: 'logout', label: 'Выйти', onClick: handleLogout },
    ];

    return (
        <div style={{ minHeight: '100vh', background: 'var(--bg)', display: 'flex', flexDirection: 'column' }}>
            <header style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 'var(--z-header)' as any }}>
                {/* TopBar */}
                <div style={{
                    background: 'var(--brand-navy)', color: 'var(--overlay-white-85)',
                    fontSize: 'var(--text-sm)', height: TOPBAR_H,
                    display: 'flex', alignItems: 'center', padding: '0 var(--page-pad-x)', gap: 20,
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <PinIcon /> {company.address.full}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <TruckIcon /> Доставка по РФ
                    </div>
                    <div style={{ flex: 1 }} />
                    <NavLink to="/about" style={{ opacity: 0.85, color: 'inherit' }}>О компании</NavLink>
                    <NavLink to="/contacts" style={{ opacity: 0.85, color: 'inherit' }}>Контакты</NavLink>
                    <span style={{ opacity: 0.35 }}>·</span>
                    <a style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#fff', cursor: 'pointer', textDecoration: 'none' }}>
                        <PhoneIcon /> {company.phone.free}
                    </a>
                </div>

                {/* Main header */}
                <div style={{
                    background: 'var(--surface)', borderBottom: '1px solid var(--line-1)',
                    height: MAIN_H, display: 'flex', alignItems: 'center',
                    padding: '0 var(--page-pad-x)', gap: 28,
                }}>
                    <NavLink to="/" style={{ flexShrink: 0, display: 'flex', alignItems: 'center', gap: 12 }}>
                        <img src="/logo-dark.png" alt="РФснаб" style={{ height: 'var(--logo-h-header)', display: 'block' }} />
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                            <span style={{
                                fontFamily: 'var(--font-display)',
                                fontSize: 20, fontWeight: 700, letterSpacing: '-0.02em',
                                color: 'var(--brand-navy)', lineHeight: 1.1,
                            }}>РФснаб</span>
                            <span style={{
                                fontFamily: 'var(--font-body)',
                                fontSize: 'var(--text-xs)', fontWeight: 400, letterSpacing: '0.04em',
                                color: 'var(--ink-3)', lineHeight: 1.2, textTransform: 'uppercase',
                            }}>комплексное снабжение</span>
                        </div>
                    </NavLink>

                    {/* Search */}
                    <div style={{ flex: 1, maxWidth: 620, position: 'relative' }}>
                        <input
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Артикул, бренд или название товара"
                            style={{
                                width: '100%', height: 'var(--input-h-lg)', padding: '0 120px 0 42px',
                                border: '1px solid var(--line-2)', borderRadius: 'var(--r-4)',
                                fontSize: 'var(--text-md)', fontFamily: 'var(--font-body)', outline: 'none',
                                background: 'var(--surface)', color: 'var(--ink-1)',
                                transition: 'border-color 0.12s',
                            }}
                            onFocus={(e) => { e.currentTarget.style.borderColor = 'var(--brand-navy)'; }}
                            onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--line-2)'; }}
                            onKeyDown={(e) => {
                                if (e.key === 'Enter' && searchQuery.trim()) {
                                    navigate(`/catalog?q=${encodeURIComponent(searchQuery.trim())}`);
                                }
                            }}
                        />
                        <span style={{ position: 'absolute', left: 14, top: 13, color: 'var(--ink-3)' }}>
                            <SearchIcon />
                        </span>
                        <button
                            onClick={() => { if (searchQuery.trim()) navigate(`/catalog?q=${encodeURIComponent(searchQuery.trim())}`); }}
                            style={{
                                position: 'absolute', right: 4, top: 4, height: 36, padding: '0 18px',
                                background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)',
                                fontSize: 'var(--text-md)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--brand-red)'; }}
                        >
                            Найти
                        </button>
                    </div>

                    {/* User actions */}
                    <div style={{ display: 'flex', gap: 2, alignItems: 'center', marginLeft: 'auto', flexShrink: 0 }}>
                        <NavLink to="/favourites" variant="icon" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, padding: '6px 10px', borderRadius: 'var(--r-3)', color: 'var(--ink-1)' }}>
                            <span style={{ position: 'relative' }}>
                                <HeartIcon />
                                {totalFavourites > 0 && (
                                    <span style={{ position: 'absolute', top: -4, right: -8, minWidth: 16, height: 16, padding: '0 4px', borderRadius: 'var(--r-4)', background: 'var(--brand-red)', color: '#fff', fontSize: 10, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid #fff' }}>{totalFavourites}</span>
                                )}
                            </span>
                            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>Избранное</span>
                        </NavLink>
                        <NavLink to="/cart" variant="icon" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, padding: '6px 10px', borderRadius: 'var(--r-3)', color: 'var(--ink-1)' }}>
                            <span style={{ position: 'relative', color: 'var(--brand-red)' }}>
                                <CartIcon />
                                {totalItems > 0 && (
                                    <span style={{ position: 'absolute', top: -4, right: -8, minWidth: 16, height: 16, padding: '0 4px', borderRadius: 'var(--r-4)', background: 'var(--brand-red)', color: '#fff', fontSize: 10, fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px solid #fff' }}>{totalItems}</span>
                                )}
                            </span>
                            <span style={{ fontSize: 'var(--text-xs)', color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>Корзина</span>
                        </NavLink>
                        <div style={{ width: 1, height: 28, background: 'var(--line-1)', margin: '0 4px' }} />
                        {isAuthenticated ? (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                <ContextSwitcher />
                                <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
                                    <button style={{
                                        display: 'flex', alignItems: 'center', gap: 6,
                                        background: 'transparent', border: '1px solid var(--line-2)',
                                        borderRadius: 'var(--r-3)', padding: '6px 12px', cursor: 'pointer',
                                        fontSize: 'var(--text-base)', fontWeight: 500, color: 'var(--ink-1)', fontFamily: 'var(--font-body)',
                                    }}>
                                        <UserIcon /> {user?.clientType === 'B2B' ? user.companyName : user?.firstname}
                                    </button>
                                </Dropdown>
                            </div>
                        ) : (
                            <NavLink
                                to="/login"
                                style={{
                                    display: 'inline-flex', alignItems: 'center', gap: 6,
                                    height: 36, padding: '0 14px',
                                    background: 'transparent', color: 'var(--brand-navy)',
                                    border: '1px solid var(--brand-navy)', borderRadius: 'var(--r-3)',
                                    fontSize: 'var(--text-base)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                    transition: 'background 0.12s',
                                }}
                                onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--navy-tint)'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                            >
                                <UserIcon /> Войти
                            </NavLink>
                        )}
                    </div>
                </div>

                {/* Categories nav */}
                <div style={{
                    background: 'var(--surface)', borderBottom: '1px solid var(--line-1)',
                    height: CAT_H, display: 'flex', alignItems: 'center',
                    padding: '0 var(--page-pad-x)', gap: 0,
                }}>
                    <NavLink
                        to="/catalog"
                        style={{
                            display: 'inline-flex', alignItems: 'center', gap: 8,
                            height: 36, padding: '0 14px', marginRight: 8,
                            background: 'var(--brand-red)', color: '#fff',
                            border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 600,
                            cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}
                    >
                        <MenuIcon /> Каталог товаров
                    </NavLink>
                    <a style={{
                        display: 'inline-flex', alignItems: 'center', gap: 4,
                        padding: '0 14px', height: '100%', fontSize: 'var(--text-md)', fontWeight: 500,
                        color: 'var(--ink-1)', cursor: 'pointer', textDecoration: 'none',
                    }}>
                        Акции
                    </a>
                    <div style={{ flex: 1 }} />
                    <span style={{ fontSize: 'var(--text-sm)', color: 'var(--ink-3)' }}>
                        <span style={{ color: 'var(--brand-green)', fontWeight: 600 }}>●</span>{' '}
                        {(availableCount ?? 12480).toLocaleString('ru-RU')} товаров в наличии · отгрузка от 1 часа
                    </span>
                </div>
            </header>

            <div style={{ height: HEADER_TOTAL }} />

            <main style={{ flex: 1, maxWidth: 'var(--page-max-w)', margin: '0 auto', width: '100%', padding: '0 var(--page-pad-x)' }}>
                <AnimatePresence mode="wait" initial={false}>
                    <motion.div
                        key={location.pathname}
                        initial={{ opacity: 0, y: 12 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: -8 }}
                        transition={{ duration: 0.2, ease: [0.25, 0, 0, 1] }}
                    >
                        <Outlet />
                    </motion.div>
                </AnimatePresence>
            </main>

            <footer style={{ background: 'var(--footer-bg)', color: '#fff', padding: 'var(--sp-14) var(--page-pad-x) 28px', marginTop: 'var(--sp-14)' }}>
                <div style={{ maxWidth: 'var(--footer-max-w)', margin: '0 auto' }}>
                    <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr 1fr 1fr 1fr', gap: 'var(--sp-14)', marginBottom: 32 }}>
                        <div>
                            <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 10 }}>
                                <img src="/logo-light.png" alt="РФснаб" style={{ height: 'var(--logo-h-footer)', display: 'block' }} />
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                                    <span style={{ fontFamily: 'var(--font-display)', fontSize: 18, fontWeight: 700, color: '#fff', letterSpacing: '-0.02em', lineHeight: 1.1 }}>РФснаб</span>
                                    <span style={{ fontFamily: 'var(--font-body)', fontSize: 10, fontWeight: 400, letterSpacing: '0.04em', color: 'rgba(255,255,255,.5)', textTransform: 'uppercase' }}>комплексное снабжение</span>
                                </div>
                            </div>
                            <p style={{ fontSize: 'var(--text-base)', color: 'rgba(255,255,255,.6)', lineHeight: 1.6 }}>
                                Комплексное снабжение предприятий и организаций. Работаем с {company.founded} года.
                            </p>
                        </div>
                        {[
                            ['Каталог', ['Противопожарное оборудование', 'СИЗ и спецодежда', 'Инструмент', 'Электротехника', 'Все категории →']],
                            ['Компания', ['О нас', 'Реквизиты', 'Сертификаты', 'Поставщикам', 'Вакансии']],
                            ['Покупателям', ['Доставка', 'Оплата и договор', 'Возврат', 'Госзакупки 44-ФЗ', 'Тендеры']],
                            ['Контакты', [company.phone.free, company.email.sales, company.workHours, company.address.city]],
                        ].map(([heading, items]) => (
                            <div key={heading as string}>
                                <div style={{ fontSize: 'var(--text-base)', fontWeight: 600, color: '#fff', marginBottom: 14 }}>{heading}</div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 'var(--text-base)', color: 'rgba(255,255,255,.6)' }}>
                                    {(items as string[]).map((item) => (
                                        <a key={item} style={{ cursor: 'pointer', color: 'inherit', textDecoration: 'none', transition: 'color 0.12s' }}
                                            onMouseEnter={(e) => { e.currentTarget.style.color = 'rgba(255,255,255,.9)'; }}
                                            onMouseLeave={(e) => { e.currentTarget.style.color = 'rgba(255,255,255,.6)'; }}
                                        >{item}</a>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                    <div style={{
                        paddingTop: 20, borderTop: '1px solid rgba(255,255,255,.1)',
                        display: 'flex', justifyContent: 'space-between', fontSize: 'var(--text-sm)', color: 'rgba(255,255,255,.45)',
                    }}>
                        <span>© {company.founded}–{new Date().getFullYear()} {company.legalName} · ИНН {company.inn}</span>
                        <span style={{ display: 'flex', gap: 18 }}>
                            <a href="/privacy-policy" style={{ color: 'inherit', textDecoration: 'none', transition: 'color 0.12s' }}
                                onMouseEnter={(e) => { e.currentTarget.style.color = 'rgba(255,255,255,.8)'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.color = 'rgba(255,255,255,.45)'; }}
                            >Политика конфиденциальности</a>
                            <a href="/personal-data" style={{ color: 'inherit', textDecoration: 'none', transition: 'color 0.12s' }}
                                onMouseEnter={(e) => { e.currentTarget.style.color = 'rgba(255,255,255,.8)'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.color = 'rgba(255,255,255,.45)'; }}
                            >Публичная оферта</a>
                        </span>
                    </div>
                </div>
            </footer>
        </div>
    );
};

export default ClientLayout;
