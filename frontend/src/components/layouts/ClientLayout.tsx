import { useRef, useEffect } from 'react';
import { Layout, Menu, Button, Badge, Dropdown } from 'antd';
import {
    ShoppingCartOutlined,
    UserOutlined,
    LoginOutlined,
    LogoutOutlined,
    ProfileOutlined,
    SettingOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuthStore } from '@/store/authStore';
import { useCartStore } from '@/store/cartStore';
import { isAdmin } from '@/types/auth';
import type { MenuProps } from 'antd';

const { Content } = Layout;

const PAGE_ORDER = [
    '/about',
    '/contacts',
    '/',
    '/cart',
    '/orders',
    '/profile',
    '/login',
    '/register',
    '/privacy-policy',
    '/personal-data',
];

const getPageIndex = (pathname: string): number => {
    if (pathname.startsWith('/products/')) return 2.5;
    if (pathname.startsWith('/checkout')) return 4.5;
    const idx = PAGE_ORDER.indexOf(pathname);
    return idx >= 0 ? idx : 5;
};

const HEADER_HEIGHT = 64;

const ClientLayout = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { isAuthenticated, user, logout } = useAuthStore();
    const totalItems = useCartStore((state) => state.totalItems);
    const prevIndexRef = useRef(getPageIndex(location.pathname));
    const fetchCart = useCartStore((state) => state.fetchCart);

    useEffect(() => {
        if (isAuthenticated) {
            fetchCart();
        }
    }, [isAuthenticated, fetchCart]);

    const handleLogout = async () => {
        await logout();
        navigate('/');
    };

    const currentIndex = getPageIndex(location.pathname);
    prevIndexRef.current = currentIndex;

    const leftNavItems: MenuProps['items'] = [
        { key: '/', label: 'Каталог' },
        { key: '/about', label: 'О нас' },
        { key: '/contacts', label: 'Контакты' },
    ];

    const userMenuItems: MenuProps['items'] = [
        {
            key: 'orders',
            icon: <ProfileOutlined />,
            label: 'Мои заказы',
            onClick: () => navigate('/orders'),
        },
        {
            key: 'profile',
            icon: <SettingOutlined />,
            label: 'Профиль',
            onClick: () => navigate('/profile'),
        },
        ...(user && isAdmin(user)
            ? [
                { type: 'divider' as const },
                {
                    key: 'admin',
                    icon: <SettingOutlined />,
                    label: 'Админ-панель',
                    onClick: () => navigate('/admin'),
                },
            ]
            : []),
        { type: 'divider' as const },
        {
            key: 'logout',
            icon: <LogoutOutlined />,
            label: 'Выйти',
            onClick: handleLogout,
        },
    ];

    return (
        <Layout style={{ minHeight: '100vh', background: 'var(--bg)' }}>
            <header
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    zIndex: 1000,
                    height: HEADER_HEIGHT,
                    background: 'var(--surface)',
                    borderBottom: '1px solid var(--line-1)',
                    boxShadow: 'var(--shadow-1)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0 40px',
                }}
            >
                <div
                    onClick={() => navigate('/')}
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 10,
                        cursor: 'pointer',
                        flexShrink: 0,
                    }}
                >
                    <img
                        src="/logo.png"
                        alt="РФснаб"
                        style={{ height: 36, width: 'auto', objectFit: 'contain' }}
                    />
                    <span
                        style={{
                            fontFamily: 'var(--font-display)',
                            fontWeight: 700,
                            fontSize: 18,
                            color: 'var(--brand-ink)',
                            letterSpacing: '-0.01em',
                            lineHeight: 1,
                        }}
                    >
                        РФснаб
                    </span>
                </div>

                <div style={{ flex: 1, display: 'flex', justifyContent: 'center' }}>
                    <Menu
                        mode="horizontal"
                        selectedKeys={[location.pathname]}
                        items={leftNavItems}
                        onClick={({ key }) => navigate(key)}
                        style={{
                            border: 'none',
                            background: 'transparent',
                            fontFamily: 'var(--font-body)',
                            fontWeight: 500,
                            fontSize: 15,
                        }}
                    />
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                    <Badge count={totalItems} size="small">
                        <Button
                            type="text"
                            icon={<ShoppingCartOutlined style={{ fontSize: 20 }} />}
                            onClick={() => navigate('/cart')}
                            style={{ fontWeight: 500 }}
                        />
                    </Badge>

                    {isAuthenticated ? (
                        <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
                            <Button
                                type="text"
                                icon={<UserOutlined style={{ fontSize: 18 }} />}
                                style={{ fontWeight: 500 }}
                            >
                                {user?.firstname}
                            </Button>
                        </Dropdown>
                    ) : (
                        <Button
                            type="primary"
                            icon={<LoginOutlined />}
                            onClick={() => navigate('/login')}
                            style={{ borderRadius: 6, fontWeight: 500 }}
                        >
                            Войти
                        </Button>
                    )}
                </div>
            </header>

            <div style={{ height: HEADER_HEIGHT }} />

            <Content style={{ maxWidth: 1440, margin: '0 auto', width: '100%', padding: '0 48px', overflow: 'hidden' }}>
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
            </Content>

            <footer
                style={{
                    background: 'var(--brand-navy)',
                    color: '#fff',
                    padding: '32px 48px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginTop: 40,
                }}
            >
                <span
                    style={{
                        fontFamily: 'var(--font-body)',
                        fontWeight: 400,
                        fontSize: 13,
                        color: 'rgba(255,255,255,.7)',
                    }}
                >
                    ООО «МСВ» — Комплексное снабжение © {new Date().getFullYear()}
                </span>
                <div style={{ display: 'flex', gap: 24 }}>
                    {[
                        { href: '/privacy-policy', label: 'Политика конфиденциальности' },
                        { href: '/personal-data', label: 'Обработка персональных данных' },
                    ].map(({ href, label }) => (
                        <a
                            key={href}
                            href={href}
                            style={{
                                fontSize: 13,
                                color: 'rgba(255,255,255,.6)',
                                textDecoration: 'none',
                                fontFamily: 'var(--font-body)',
                                transition: 'color 0.15s ease-out',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.color = 'rgba(255,255,255,.9)')}
                            onMouseLeave={(e) => (e.currentTarget.style.color = 'rgba(255,255,255,.6)')}
                        >
                            {label}
                        </a>
                    ))}
                </div>
            </footer>
        </Layout>
    );
};

export default ClientLayout;
