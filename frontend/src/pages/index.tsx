import { Typography } from 'antd';

const { Title } = Typography;

// Клиентские страницы
export const CatalogPage = () => <Title level={2}>Каталог товаров</Title>;
export const ProductPage = () => <Title level={2}>Карточка товара</Title>;
export const CartPage = () => <Title level={2}>Корзина</Title>;
export const CheckoutPage = () => <Title level={2}>Оформление заказа</Title>;
export const OrdersPage = () => <Title level={2}>Мои заказы</Title>;
export const ProfilePage = () => <Title level={2}>Профиль</Title>;
export const AboutPage = () => <Title level={2}>О нас</Title>;
export const ContactsPage = () => <Title level={2}>Контакты</Title>;

// Админские страницы
export const DashboardPage = () => <Title level={2}>Дашборд</Title>;
export const AdminProductsPage = () => <Title level={2}>Управление товарами</Title>;
export const AdminOrdersPage = () => <Title level={2}>Управление заказами</Title>;
export const AdminUsersPage = () => <Title level={2}>Пользователи</Title>;
export const IntegrationPage = () => <Title level={2}>Интеграция 1С</Title>;