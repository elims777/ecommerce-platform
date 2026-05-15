import { useRef, useState } from 'react';
import { Badge, Dropdown } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuthStore } from '@/store/authStore';
import { useCartStore } from '@/store/cartStore';
import { isAdmin } from '@/types/auth';
import type { MenuProps } from 'antd';
import { useEffect } from 'react';

const TOPBAR_H = 36;
const MAIN_H = 76;
const CAT_H = 46;
const HEADER_TOTAL = TOPBAR_H + MAIN_H + CAT_H;

const PAGE_ORDER = ['/about', '/contacts', '/', '/cart', '/orders', '/profile', '/privacy-policy', '/personal-data'];
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
const ChevDownIcon = () => (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="m6 9 6 6 6-6"/>
    </svg>
);
const DownloadIcon = () => (
    <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M12 4v12M7 11l5 5 5-5M4 20h16"/>
    </svg>
);

const TriMark = ({ size = 28 }: { size?: number }) => (
    <svg width={size} height={size * 0.95} viewBox="0 0 40 38">
        <polygon points="20,2 20,28 4,32" fill="#C0272D"/>
        <polygon points="20,2 20,28 36,32" fill="#1E3A5F"/>
        <polygon points="4,32 36,32 20,28" fill="#1A6B3A"/>
    </svg>
);

const HeaderAction = ({ icon, label, count, highlight, onClick }: { icon: React.ReactNode; label: string; count?: number; highlight?: boolean; onClick?: () => void }) => (
    <button onClick={onClick} style={{
        display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2,
        background: 'transparent', border: 0, cursor: 'pointer',
        padding: '6px 10px', borderRadius: 6, color: 'var(--ink-1)', fontFamily: 'inherit',
    }}>
        <span style={{ position: 'relative', color: highlight ? 'var(--brand-red)' : 'var(--ink-1)' }}>
            {icon}
            {count != null && count > 0 && (
                <span style={{
                    position: 'absolute', top: -4, right: -8,
                    minWidth: 16, height: 16, padding: '0 4px', borderRadius: 8,
                    background: 'var(--brand-red)', color: '#fff',
                    fontSize: 10, fontWeight: 700,
                    display: 'flex', alignItems: 'center', justifyContent: 'center',
                    border: '2px solid #fff',
                }}>{count}</span>
            )}
        </span>
        <span style={{ fontSize: 11, color: 'var(--ink-3)', whiteSpace: 'nowrap' }}>{label}</span>
    </button>
);

const ClientLayout = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { isAuthenticated, user, logout } = useAuthStore();
    const totalItems = useCartStore((state) => state.totalItems);
    const fetchCart = useCartStore((state) => state.fetchCart);
    const [searchQuery, setSearchQuery] = useState('');
    const prevIndexRef = useRef(getPageIndex(location.pathname));

    useEffect(() => {
        if (isAuthenticated) fetchCart();
    }, [isAuthenticated, fetchCart]);

    const handleLogout = async () => {
        await logout();
        navigate('/');
    };

    const currentIndex = getPageIndex(location.pathname);
    prevIndexRef.current = currentIndex;

    const userMenuItems: MenuProps['items'] = [
        { key: 'orders', label: 'Мои заказы', onClick: () => navigate('/orders') },
        { key: 'profile', label: 'Профиль', onClick: () => navigate('/profile') },
        ...(user && isAdmin(user) ? [
            { type: 'divider' as const },
            { key: 'admin', label: 'Админ-панель', onClick: () => navigate('/admin') },
        ] : []),
        { type: 'divider' as const },
        { key: 'logout', label: 'Выйти', onClick: handleLogout },
    ];

    const isActive = (path: string) => location.pathname === path;

    const navLink = (path: string, label: string, hasChev?: boolean) => (
        <a
            key={path}
            onClick={() => navigate(path)}
            style={{
                display: 'inline-flex', alignItems: 'center', gap: 4,
                padding: '0 14px', height: '100%', fontSize: 14, fontWeight: 500,
                color: isActive(path) ? 'var(--brand-red)' : 'var(--ink-1)',
                borderBottom: isActive(path) ? '2px solid var(--brand-red)' : '2px solid transparent',
                marginBottom: -1, cursor: 'pointer', textDecoration: 'none',
                transition: 'color 0.12s',
            }}
            onMouseEnter={(e) => { if (!isActive(path)) (e.currentTarget as HTMLElement).style.color = 'var(--brand-navy)'; }}
            onMouseLeave={(e) => { if (!isActive(path)) (e.currentTarget as HTMLElement).style.color = 'var(--ink-1)'; }}
        >
            {label}
            {hasChev && <ChevDownIcon />}
        </a>
    );

    return (
        <div style={{ minHeight: '100vh', background: 'var(--bg)', display: 'flex', flexDirection: 'column' }}>
            <header style={{ position: 'fixed', top: 0, left: 0, right: 0, zIndex: 1000 }}>
                {/* TopBar */}
                <div style={{
                    background: 'var(--brand-navy)', color: 'rgba(255,255,255,.85)',
                    fontSize: 12.5, height: TOPBAR_H,
                    display: 'flex', alignItems: 'center', padding: '0 40px', gap: 20,
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <PinIcon /> г. Москва, ул. Промышленная, 27с4
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <TruckIcon /> Доставка по РФ
                    </div>
                    <div style={{ flex: 1 }} />
                    <a onClick={() => navigate('/about')} style={{ opacity: 0.85, cursor: 'pointer', color: 'inherit', textDecoration: 'none' }}>О компании</a>
                    <a onClick={() => navigate('/contacts')} style={{ opacity: 0.85, cursor: 'pointer', color: 'inherit', textDecoration: 'none' }}>Контакты</a>
                    <a style={{ opacity: 0.85, cursor: 'pointer', color: 'inherit', textDecoration: 'none', display: 'flex', alignItems: 'center', gap: 4 }}>
                        Прайс-листы <DownloadIcon />
                    </a>
                    <span style={{ opacity: 0.35 }}>·</span>
                    <a style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, color: '#fff', cursor: 'pointer', textDecoration: 'none' }}>
                        <PhoneIcon /> +7 (495) 120-77-30
                    </a>
                </div>

                {/* Main header */}
                <div style={{
                    background: '#fff', borderBottom: '1px solid var(--line-1)',
                    height: MAIN_H, display: 'flex', alignItems: 'center',
                    padding: '0 40px', gap: 28,
                }}>
                    <div onClick={() => navigate('/')} style={{ display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer', flexShrink: 0 }}>
                        <TriMark size={34} />
                        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1 }}>
                            <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 20, color: 'var(--brand-ink)', letterSpacing: '-0.02em' }}>РФснаб</span>
                            <span style={{ fontSize: 9, color: 'var(--ink-3)', marginTop: 3, letterSpacing: '0.05em', textTransform: 'uppercase', fontWeight: 500 }}>комплексное снабжение</span>
                        </div>
                    </div>

                    {/* Search */}
                    <div style={{ flex: 1, maxWidth: 620, position: 'relative' }}>
                        <input
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            placeholder="Артикул, бренд или название товара"
                            style={{
                                width: '100%', height: 44, padding: '0 120px 0 42px',
                                border: '1px solid var(--line-2)', borderRadius: 8,
                                fontSize: 14, fontFamily: 'var(--font-body)', outline: 'none',
                                background: 'var(--surface)', color: 'var(--ink-1)',
                                transition: 'border-color 0.12s',
                            }}
                            onFocus={(e) => { e.currentTarget.style.borderColor = 'var(--brand-navy)'; }}
                            onBlur={(e) => { e.currentTarget.style.borderColor = 'var(--line-2)'; }}
                        />
                        <span style={{ position: 'absolute', left: 14, top: 13, color: 'var(--ink-3)' }}>
                            <SearchIcon />
                        </span>
                        <button style={{
                            position: 'absolute', right: 4, top: 4, height: 36, padding: '0 18px',
                            background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 6,
                            fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}
                            onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--brand-red)'; }}
                        >
                            Найти
                        </button>
                    </div>

                    {/* User actions */}
                    <div style={{ display: 'flex', gap: 2, alignItems: 'center', marginLeft: 'auto', flexShrink: 0 }}>
                        <HeaderAction icon={<HeartIcon />} label="Избранное" />
                        <HeaderAction icon={<CartIcon />} label="Корзина" count={totalItems} highlight onClick={() => navigate('/cart')} />
                        <div style={{ width: 1, height: 28, background: 'var(--line-1)', margin: '0 4px' }} />
                        {isAuthenticated ? (
                            <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
                                <button style={{
                                    display: 'flex', alignItems: 'center', gap: 6,
                                    background: 'transparent', border: '1px solid var(--line-2)',
                                    borderRadius: 6, padding: '6px 12px', cursor: 'pointer',
                                    fontSize: 13.5, fontWeight: 500, color: 'var(--ink-1)', fontFamily: 'var(--font-body)',
                                }}>
                                    <UserIcon /> {user?.firstname}
                                </button>
                            </Dropdown>
                        ) : (
                            <button
                                onClick={() => navigate('/login')}
                                style={{
                                    display: 'inline-flex', alignItems: 'center', gap: 6,
                                    height: 36, padding: '0 14px',
                                    background: 'transparent', color: 'var(--brand-navy)',
                                    border: '1px solid var(--brand-navy)', borderRadius: 6,
                                    fontSize: 13.5, fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)',
                                    transition: 'background 0.12s',
                                }}
                                onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--navy-tint)'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                            >
                                <UserIcon /> Войти
                            </button>
                        )}
                    </div>
                </div>

                {/* Categories nav */}
                <div style={{
                    background: '#fff', borderBottom: '1px solid var(--line-1)',
                    height: CAT_H, display: 'flex', alignItems: 'center',
                    padding: '0 40px', gap: 0,
                }}>
                    <button
                        onClick={() => navigate('/')}
                        style={{
                            display: 'inline-flex', alignItems: 'center', gap: 8,
                            height: 36, padding: '0 14px', marginRight: 8,
                            background: 'var(--brand-red)', color: '#fff',
                            border: 'none', borderRadius: 6, fontSize: 14, fontWeight: 600,
                            cursor: 'pointer', fontFamily: 'var(--font-body)',
                        }}
                        onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--brand-red-hover)'; }}
                        onMouseLeave={(e) => { e.currentTarget.style.background = 'var(--brand-red)'; }}
                    >
                        <MenuIcon /> Каталог товаров
                    </button>
                    {navLink('/about', 'О компании', true)}
                    {navLink('/contacts', 'Контакты')}
                    <a style={{
                        display: 'inline-flex', alignItems: 'center', gap: 4,
                        padding: '0 14px', height: '100%', fontSize: 14, fontWeight: 500,
                        color: 'var(--ink-1)', cursor: 'pointer', textDecoration: 'none',
                    }}>
                        Акции
                    </a>
                    <div style={{ flex: 1 }} />
                    <span style={{ fontSize: 12.5, color: 'var(--ink-3)' }}>
                        <span style={{ color: 'var(--brand-green)', fontWeight: 600 }}>●</span>{' '}
                        12 480 товаров в наличии · отгрузка от 1 часа
                    </span>
                </div>
            </header>

            <div style={{ height: HEADER_TOTAL }} />

            <main style={{ flex: 1, maxWidth: 1440, margin: '0 auto', width: '100%', padding: '0 40px' }}>
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

            <footer style={{ background: '#1A1A1A', color: '#fff', padding: '40px 40px 28px', marginTop: 40 }}>
                <div style={{ maxWidth: 1360, margin: '0 auto' }}>
                    <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 1fr 1fr 1fr 1fr', gap: 40, marginBottom: 32 }}>
                        <div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
                                <TriMark size={28} />
                                <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1 }}>
                                    <span style={{ fontFamily: 'var(--font-display)', fontWeight: 700, fontSize: 16, color: '#fff', letterSpacing: '-0.02em' }}>РФснаб</span>
                                    <span style={{ fontSize: 8, color: 'rgba(255,255,255,.4)', marginTop: 3, letterSpacing: '0.06em', textTransform: 'uppercase', fontWeight: 500 }}>комплексное снабжение</span>
                                </div>
                            </div>
                            <p style={{ fontSize: 13, color: 'rgba(255,255,255,.6)', lineHeight: 1.6 }}>
                                Комплексное снабжение предприятий и организаций. Работаем с 2008 года, более 18 000 клиентов в 84 регионах России.
                            </p>
                        </div>
                        {[
                            ['Каталог', ['Противопожарное оборудование', 'СИЗ и спецодежда', 'Инструмент', 'Электротехника', 'Все категории →']],
                            ['Компания', ['О нас', 'Реквизиты', 'Сертификаты', 'Поставщикам', 'Вакансии']],
                            ['Покупателям', ['Доставка', 'Оплата и договор', 'Возврат', 'Госзакупки 44-ФЗ', 'Тендеры']],
                            ['Контакты', ['+7 (495) 120-77-30', 'sales@rfsnab.ru', 'Пн–Пт 8:00–19:00', 'Москва · СПб · Казань']],
                        ].map(([heading, items]) => (
                            <div key={heading as string}>
                                <div style={{ fontSize: 13, fontWeight: 600, color: '#fff', marginBottom: 14 }}>{heading}</div>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 13, color: 'rgba(255,255,255,.6)' }}>
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
                        display: 'flex', justifyContent: 'space-between', fontSize: 12, color: 'rgba(255,255,255,.45)',
                    }}>
                        <span>© 2008–{new Date().getFullYear()} ООО «РФснаб» · ИНН 7710123456</span>
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
