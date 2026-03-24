import { Layout, Menu, Button, Space, Badge, Dropdown } from 'antd';

import {
    ShoppingCartOutlined,
    UserOutlined,
    LoginOutlined,
    LogoutOutlined,
    ProfileOutlined,
    SettingOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { UserRole } from '@/types/auth';
import type { MenuProps } from 'antd';
import { useCartStore } from '@/store/cartStore';

const { Header, Content, Footer } = Layout;

const ClientLayout = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const { isAuthenticated, user, logout } = useAuthStore();
    const totalItems = useCartStore((state) => state.getTotalItems)();

    const handleLogout = async () => {
        await logout();
        navigate('/');
    };

    // Основное меню навигации
    const navItems: MenuProps['items'] = [
        { key: '/', label: 'Каталог' },
        { key: '/about', label: 'О нас' },
        { key: '/contacts', label: 'Контакты' },
    ];

    // Dropdown-меню пользователя (появляется при клике на иконку юзера)
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
        // Если админ — показываем ссылку на админку
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
            <Header
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    background: '#fff',
                    height: 72,
                    padding: '0 24px',
                    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.06)',
                    position: 'sticky',
                    top: 0,
                    zIndex: 100,
                }}
            >
                {/* Логотип */}
                <div
                    style={{ cursor: 'pointer', display: 'flex', alignItems: 'center' }}
                    onClick={() => navigate('/')}
                >
                    <img
                        src="/logo.png"
                        alt="РФснаб"
                        style={{ height: 56 }}
                    />
                </div>

                {/* Навигация по центру */}
                <Menu
                    mode="horizontal"
                    selectedKeys={[location.pathname]}
                    items={navItems}
                    onClick={({ key }) => navigate(key)}
                    style={{ flex: 1, justifyContent: 'center', border: 'none' }}
                />

                {/* Правый блок: корзина + пользователь */}
                <Space size="middle">
                    <Badge count={totalItems} showZero={false}>
                        <Button
                            type="text"
                            icon={<ShoppingCartOutlined style={{ fontSize: 20 }} />}
                            onClick={() => navigate('/cart')}
                        />
                    </Badge>

                    {isAuthenticated ? (
                        <Dropdown menu={{ items: userMenuItems }} trigger={['click']}>
                            <Button type="text" icon={<UserOutlined style={{ fontSize: 20 }} />}>
                                {user?.firstName}
                            </Button>
                        </Dropdown>
                    ) : (
                        <Button
                            type="primary"
                            icon={<LoginOutlined />}
                            onClick={() => navigate('/login')}
                        >
                            Войти
                        </Button>
                    )}
                </Space>
            </Header>

            {/* Основное содержимое страницы */}
            <Content style={{ padding: '24px 48px', maxWidth: 1440, margin: '0 auto', width: '100%' }}>
                <Outlet />
            </Content>

            <Footer style={{ textAlign: 'center', background: '#f5f5f5' }}>
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