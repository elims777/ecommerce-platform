import { useRef } from 'react';
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
import { UserRole } from '@/types/auth';
import type { MenuProps } from 'antd';

const { Content, Footer } = Layout;

/** Порядок страниц — определяет направление анимации */
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

const HEADER_HEIGHT = 80;
const LOGO_SIZE = 120;

const ClientLayout = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { isAuthenticated, user, logout } = useAuthStore();
    const totalItems = useCartStore((state) => state.getTotalItems)();
    const prevIndexRef = useRef(getPageIndex(location.pathname));

    const handleLogout = async () => {
        await logout();
        navigate('/');
    };

    // Определяем направление анимации
    const currentIndex = getPageIndex(location.pathname);
    const direction = currentIndex > prevIndexRef.current ? 1 : -1;
    prevIndexRef.current = currentIndex;

    // Левая навигация
    const leftNavItems: MenuProps['items'] = [
        { key: '/', label: 'Каталог' },
        { key: '/about', label: 'О нас' },
        { key: '/contacts', label: 'Контакты' },
    ];

    // Dropdown-меню пользователя
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
        ...(user?.role === UserRole.ADMIN
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
        <Layout style={{ minHeight: '100vh' }}>
            {/* Хэдер */}
            <header
                style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    zIndex: 1000,
                    height: HEADER_HEIGHT,
                    background: '#fff',
                    boxShadow: '0 2px 12px rgba(0, 0, 0, 0.08)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '0 32px',
                }}
            >
                {/* Левая навигация */}
                <div style={{ flex: 1, display: 'flex', justifyContent: 'flex-end', paddingRight: 60 }}>
                    <Menu
                        mode="horizontal"
                        selectedKeys={[location.pathname]}
                        items={leftNavItems}
                        onClick={({ key }) => navigate(key)}
                        style={{
                            border: 'none',
                            background: 'transparent',
                            fontWeight: 500,
                            fontSize: 15,
                        }}
                    />
                </div>

                {/* Логотип-медальон по центру */}
                <div
                    className="glass-sphere"
                    onClick={() => navigate('/')}
                    style={{
                        position: 'absolute',
                        left: '50%',
                        top: '50%',
                        transform: 'translate(-50%, -50%)',
                        zIndex: 1001,
                        width: LOGO_SIZE,
                        height: LOGO_SIZE,
                        marginTop: LOGO_SIZE * 0.25,
                        cursor: 'pointer',
                    }}
                >
                    <img
                        src="/logo.png"
                        alt="РФснаб"
                        style={{
                            width: LOGO_SIZE * 0.55,
                            height: LOGO_SIZE * 0.55,
                            objectFit: 'contain',
                            position: 'relative',
                            zIndex: 2,
                            filter: 'drop-shadow(0 1px 2px rgba(0,0,0,0.1))',
                        }}
                    />
                </div>

                {/* Правая навигация */}
                <div style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'flex-start', paddingLeft: 60, gap: 8 }}>
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
                                {user?.firstName}
                            </Button>
                        </Dropdown>
                    ) : (
                        <Button
                            type="primary"
                            icon={<LoginOutlined />}
                            onClick={() => navigate('/login')}
                            style={{ borderRadius: 20, fontWeight: 500 }}
                        >
                            Войти
                        </Button>
                    )}
                </div>
            </header>

            {/* Spacer — чтобы контент не залезал под fixed-хэдер + место для выступающего логотипа */}
            <div style={{ height: HEADER_HEIGHT + LOGO_SIZE * 0.25 + 10 }} />

            {/* Основное содержимое с анимацией перехода */}
            <Content style={{ maxWidth: 1440, margin: '0 auto', width: '100%', padding: '0 48px', overflow: 'hidden' }}>
                <AnimatePresence mode="wait" initial={false}>
                    <motion.div
                        key={location.pathname}
                        initial={{ x: direction * 300, opacity: 0 }}
                        animate={{ x: 0, opacity: 1 }}
                        exit={{ x: direction * -300, opacity: 0 }}
                        transition={{ duration: 0.3, ease: 'easeInOut' }}
                    >
                        <Outlet />
                    </motion.div>
                </AnimatePresence>
            </Content>

            <Footer style={{ textAlign: 'center', background: '#f5f5f5', marginTop: 40 }}>
                <div>
                    <a href="/privacy-policy" style={{ marginRight: 16 }}>Политика конфиденциальности</a>
                    <a href="/personal-data">Обработка персональных данных</a>
                </div>
                <div style={{ marginTop: 8 }}>ООО «МСВ» — Комплексное снабжение © {new Date().getFullYear()}</div>
            </Footer>
        </Layout>
    );
};

export default ClientLayout;