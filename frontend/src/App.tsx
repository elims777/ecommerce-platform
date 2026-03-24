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
import AdminProductsPage from '@/features/admin/AdminProductsPage';
import AdminOrdersPage from '@/features/admin/AdminOrdersPage';
import AdminUsersPage from '@/features/admin/AdminUsersPage';
import IntegrationPage from '@/features/admin/IntegrationPage';
import AboutPage from '@/pages/AboutPage';
import ContactsPage from '@/pages/ContactsPage';
import PrivacyPolicyPage from '@/pages/PrivacyPolicyPage';
import PersonalDataPage from '@/pages/PersonalDataPage';

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

// Тема Ant Design — основные цвета, скруглённость, шрифты
const antTheme = {
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 6,
    fontFamily: '"Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
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
        <Route>
          <Route element={<AdminLayout />}>
            <Route path="/admin" element={<DashboardPage />} />
            <Route path="/admin/products" element={<AdminProductsPage />} />
            <Route path="/admin/orders" element={<AdminOrdersPage />} />
            <Route path="/admin/users" element={<AdminUsersPage />} />
            <Route path="/admin/integration" element={<IntegrationPage />} />
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