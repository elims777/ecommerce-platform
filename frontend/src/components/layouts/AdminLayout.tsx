import { useState } from 'react';
import { Layout, Menu, Button, theme } from 'antd';
import {
    DashboardOutlined,
    ShoppingOutlined,
    OrderedListOutlined,
    UserOutlined,
    SyncOutlined,
    MenuFoldOutlined,
    MenuUnfoldOutlined,
    ArrowLeftOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import type { MenuProps } from 'antd';

const { Header, Sider, Content } = Layout;

const AdminLayout = () => {
    const [collapsed, setCollapsed] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const { token: themeToken } = theme.useToken();

    // Пункты бокового меню админки
    const menuItems: MenuProps['items'] = [
        {
            key: '/admin',
            icon: <DashboardOutlined />,
            label: 'Дашборд',
        },
        {
            key: '/admin/products',
            icon: <ShoppingOutlined />,
            label: 'Товары',
        },
        {
            key: '/admin/orders',
            icon: <OrderedListOutlined />,
            label: 'Заказы',
        },
        {
            key: '/admin/users',
            icon: <UserOutlined />,
            label: 'Пользователи',
        },
        {
            key: '/admin/integration',
            icon: <SyncOutlined />,
            label: 'Интеграция 1С',
        },
    ];

    // Определяем активный пункт меню по текущему URL
    const selectedKey =
        menuItems.find(
            (item) =>
                item?.key !== '/admin' && location.pathname.startsWith(item?.key as string),
        )?.key as string || '/admin';

    return (
        <Layout style={{ minHeight: '100vh' }}>
            {/* Боковая панель */}
            <Sider
                trigger={null}
                collapsible
                collapsed={collapsed}
                style={{ background: themeToken.colorBgContainer }}
            >
                {/* Логотип в сайдбаре */}
                <div
                    style={{
                        height: 64,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        borderBottom: `1px solid ${themeToken.colorBorderSecondary}`,
                    }}
                >
                    <img
                        src="/logo.png"
                        alt="РФснаб"
                        style={{ height: 32, cursor: 'pointer' }}
                        onClick={() => navigate('/admin')}
                    />
                </div>

                <Menu
                    mode="inline"
                    selectedKeys={[selectedKey]}
                    items={menuItems}
                    onClick={({ key }) => navigate(key)}
                    style={{ borderRight: 'none' }}
                />
            </Sider>

            <Layout>
                {/* Верхняя панель админки */}
                <Header
                    style={{
                        padding: '0 24px',
                        background: themeToken.colorBgContainer,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        boxShadow: '0 1px 4px rgba(0, 0, 0, 0.08)',
                    }}
                >
                    {/* Кнопка сворачивания сайдбара */}
                    <Button
                        type="text"
                        icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                        onClick={() => setCollapsed(!collapsed)}
                    />

                    {/* Кнопка "Вернуться в магазин" */}
                    <Button
                        type="link"
                        icon={<ArrowLeftOutlined />}
                        onClick={() => navigate('/')}
                    >
                        В магазин
                    </Button>
                </Header>

                {/* Содержимое админки */}
                <Content
                    style={{
                        margin: 24,
                        padding: 24,
                        background: themeToken.colorBgContainer,
                        borderRadius: themeToken.borderRadiusLG,
                        minHeight: 280,
                    }}
                >
                    <Outlet />
                </Content>
            </Layout>
        </Layout>
    );
};

export default AdminLayout;