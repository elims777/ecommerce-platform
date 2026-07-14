import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ConfigProvider, App as AntApp, Spin } from 'antd';
import ruRU from 'antd/locale/ru_RU';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from '@/lib/queryClient';
import { useAuthStore } from '@/store/authStore';
import ProtectedRoute from '@/auth/ProtectedRoute';
import ClientLayout from '@/components/layouts/ClientLayout';
import AuthLayout from '@/components/layouts/AuthLayout';
import AdminLayout from '@/components/layouts/AdminLayout';
import ErrorBoundary from '@/components/ErrorBoundary';

const LoginPage = lazy(() => import('@/pages/LoginPage'));
const HomePage = lazy(() => import('@/pages/HomePage'));
const CatalogPage = lazy(() => import('@/features/catalog/CatalogPage'));
const ProductPage = lazy(() => import('@/features/catalog/ProductPage'));
const RegisterPage = lazy(() => import('@/pages/RegisterPage'));
const CartPage = lazy(() => import('@/features/cart/CartPage'));
const CheckoutPage = lazy(() => import('@/features/checkout/CheckoutPage'));
const OrdersPage = lazy(() => import('@/features/orders/OrdersPage'));
const ProfilePage = lazy(() => import('@/features/profile/ProfilePage'));
const DashboardPage = lazy(() => import('@/features/admin/DashboardPage'));
const AdminOrdersPage = lazy(() => import('@/features/admin/AdminOrdersPage'));
const AdminUsersPage = lazy(() => import('@/features/admin/AdminUsersPage'));
const IntegrationPage = lazy(() => import('@/features/admin/IntegrationPage'));
const AboutPage = lazy(() => import('@/pages/AboutPage'));
const ContactsPage = lazy(() => import('@/pages/ContactsPage'));
const PrivacyPolicyPage = lazy(() => import('@/pages/PrivacyPolicyPage'));
const PersonalDataPage = lazy(() => import('@/pages/PersonalDataPage'));
const AdminCatalogPage = lazy(() => import('@/features/admin/AdminCatalogPage'));
const AdminProductEditPage = lazy(() => import('@/features/admin/AdminProductEditPage'));
const AdminOrderDetailPage = lazy(() => import('@/features/admin/AdminOrderDetailPage'));
const AdminUserDetailPage = lazy(() => import('@/features/admin/AdminUserDetailPage'));
const AdminLegalEntityDetailPage = lazy(() => import('@/features/admin/AdminLegalEntityDetailPage'));
const AdminSettingsPage = lazy(() => import('@/features/admin/AdminSettingsPage'));
const LogisticsPage = lazy(() => import('@/features/admin/LogisticsPage'));
const FavouritesPage = lazy(() => import('@/features/favourites/FavouritesPage'));
const PaymentResultPage = lazy(() => import('@/pages/PaymentResultPage'));
const OAuth2SuccessPage = lazy(() => import('@/pages/OAuth2SuccessPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

const antTheme = {
  token: {
    colorPrimary: '#C0272D',
    colorLink: '#1E3A5F',
    colorSuccess: '#1A6B3A',
    borderRadius: 6,
    fontFamily: "'Golos Text', system-ui, sans-serif",
    fontSize: 14,
    colorBgBase: '#ffffff',
    colorBgContainer: '#ffffff',
    colorBgLayout: '#f7f5f3',
    colorText: '#1A1A1A',
    colorTextSecondary: '#6b6560',
    colorTextTertiary: '#9a958f',
    colorBorder: '#e0dbd5',
    colorBorderSecondary: '#ede8e3',
  },
  components: {
    Button: {
      borderRadius: 6,
      fontWeight: 500,
    },
    Menu: {
      itemSelectedColor: '#C0272D',
      itemSelectedBg: '#fdf2f2',
      itemActiveBg: '#fdf2f2',
      itemBg: 'transparent',
    },
    Card: {
      colorBgContainer: '#ffffff',
    },
    Table: {
      colorBgContainer: '#ffffff',
      headerBg: '#f7f5f3',
    },
    Select: {
      colorBgContainer: '#ffffff',
    },
    Input: {
      colorBgContainer: '#ffffff',
    },
  },
};

const AppRoutes = () => {
  const { isLoading, restoreSession } = useAuthStore();

  useEffect(() => {
    restoreSession();
  }, [restoreSession]);

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <ErrorBoundary>
      <Suspense fallback={
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
          <Spin size="large" />
        </div>
      }>
        <Routes>
          <Route path="/oauth2/success" element={<OAuth2SuccessPage />} />

          <Route element={<AuthLayout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>

          <Route element={<ClientLayout />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/catalog" element={<CatalogPage />} />
            <Route path="/products/:id" element={<ProductPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/contacts" element={<ContactsPage />} />
            <Route path="/privacy-policy" element={<PrivacyPolicyPage />} />
            <Route path="/personal-data" element={<PersonalDataPage />} />
            <Route path="/payment/success" element={<PaymentResultPage />} />
            <Route path="/payment/fail" element={<PaymentResultPage />} />

            <Route element={<ProtectedRoute />}>
              <Route path="/cart" element={<CartPage />} />
              <Route path="/checkout" element={<CheckoutPage />} />
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="/profile" element={<ProfilePage />} />
              <Route path="/favourites" element={<FavouritesPage />} />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Route>

          <Route element={<ProtectedRoute adminOnly />}>
            <Route element={<AdminLayout />}>
              <Route path="/admin" element={<DashboardPage />} />
              <Route path="/admin/orders" element={<AdminOrdersPage />} />
              <Route path="/admin/orders/:id" element={<AdminOrderDetailPage />} />
              <Route path="/admin/users" element={<AdminUsersPage />} />
              <Route path="/admin/users/:id" element={<AdminUserDetailPage />} />
              <Route path="/admin/legal-entities/:id" element={<AdminLegalEntityDetailPage />} />
              <Route path="/admin/logistics" element={<LogisticsPage />} />
              <Route path="/admin/integration" element={<IntegrationPage />} />
              <Route path="/admin/products" element={<AdminCatalogPage />} />
              <Route path="/admin/products/:id/edit" element={<AdminProductEditPage />} />
              <Route path="/admin/settings" element={<AdminSettingsPage />} />
            </Route>
          </Route>
        </Routes>
      </Suspense>
    </ErrorBoundary>
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
