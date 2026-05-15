import { useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, App as AntApp, Spin } from 'antd';
import ruRU from 'antd/locale/ru_RU';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/auth/ProtectedRoute';
import ClientLayout from '@/components/layouts/ClientLayout';
import AdminLayout from '@/components/layouts/AdminLayout';
import LoginPage from '@/pages/LoginPage';
import CatalogPage from '@/features/catalog/CatalogPage';
import ProductPage from '@/features/catalog/ProductPage';
import RegisterPage from '@/pages/RegisterPage';
import CartPage from '@/features/cart/CartPage';
import CheckoutPage from '@/features/checkout/CheckoutPage';
import OrdersPage from '@/features/orders/OrdersPage';
import ProfilePage from '@/features/profile/ProfilePage';
import DashboardPage from '@/features/admin/DashboardPage';
import AdminOrdersPage from '@/features/admin/AdminOrdersPage';
import AdminUsersPage from '@/features/admin/AdminUsersPage';
import IntegrationPage from '@/features/admin/IntegrationPage';
import AboutPage from '@/pages/AboutPage';
import ContactsPage from '@/pages/ContactsPage';
import PrivacyPolicyPage from '@/pages/PrivacyPolicyPage';
import PersonalDataPage from '@/pages/PersonalDataPage';
import AdminCatalogPage from '@/features/admin/AdminCatalogPage';
import AdminProductEditPage from "@/features/admin/AdminProductEditPage.tsx";

// Клиент для TanStack Query — управляет кэшированием серверных данных
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Не перезапрашивать при фокусе окна — для B2B не нужна такая агрессивность
      refetchOnWindowFocus: false,
      // Повторять неудачные запросы 1 раз
      retry: 1,
      // Данные считаются свежими 30 секунд
      staleTime: 30 * 1000,
    },
  },
});

const antTheme = {
  token: {
    colorPrimary: '#C0272D',
    colorLink: '#1E3A5F',
    colorSuccess: '#1A6B3A',
    borderRadius: 6,
    fontFamily: "'Golos Text', system-ui, sans-serif",
    fontSize: 14,
    colorBgBase: 'oklch(0.987 0.004 40)',
  },
  components: {
    Button: {
      borderRadius: 6,
      fontWeight: 500,
    },
    Menu: {
      itemSelectedColor: '#C0272D',
      itemSelectedBg: 'oklch(0.96 0.018 28)',
      itemActiveBg: 'oklch(0.96 0.018 28)',
    },
  },
};

const AppRoutes = () => {
  const { isLoading, restoreSession } = useAuthStore();

  // При первой загрузке приложения — пытаемся восстановить сессию из токена
  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  // Пока сессия восстанавливается — спиннер на весь экран
  if (isLoading) {
    return (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
          <Spin size="large" tip="Загрузка..." />
        </div>
    );
  }

  return (
      <Routes>
        {/*
        Клиентская часть — ClientLayout (header + footer)
        Публичные маршруты: каталог, товар, о нас, контакты
      */}
        <Route element={<ClientLayout />}>
          <Route path="/" element={<CatalogPage />} />
          <Route path="/products/:id" element={<ProductPage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="/contacts" element={<ContactsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/privacy-policy" element={<PrivacyPolicyPage />} />
          <Route path="/personal-data" element={<PersonalDataPage />} />
          <Route path="/register" element={<RegisterPage />} />

          {/* Защищённые клиентские маршруты — требуют авторизации */}
          <Route element={<ProtectedRoute />}>
            <Route path="/cart" element={<CartPage />} />
            <Route path="/checkout" element={<CheckoutPage />} />
            <Route path="/orders" element={<OrdersPage />} />
            <Route path="/profile" element={<ProfilePage />} />
          </Route>
        </Route>

        {/*
        Админка — AdminLayout (sidebar + header)
        Только для пользователей с ролью ADMIN
      */}
        {/*<Route element={<ProtectedRoute adminOnly />}>*/}
        <Route element={<ProtectedRoute adminOnly />}>
          <Route element={<AdminLayout />}>
            <Route path="/admin" element={<DashboardPage />} />
            <Route path="/admin/orders" element={<AdminOrdersPage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/integration" element={<IntegrationPage />} />
            <Route path="/admin/products" element={<AdminCatalogPage />} />
            <Route path="/admin/products/:id/edit" element={<AdminProductEditPage />} />
          </Route>
        </Route>
      </Routes>
  );
};

const App = () => {
  return (
      <QueryClientProvider client={queryClient}>
        <ConfigProvider locale={ruRU} theme={antTheme}>
          <AntApp>
            <BrowserRouter>
              <AppRoutes />
            </BrowserRouter>
          </AntApp>
        </ConfigProvider>
      </QueryClientProvider>
  );
};

export default App;