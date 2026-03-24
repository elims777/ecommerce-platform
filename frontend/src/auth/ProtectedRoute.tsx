import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuthStore } from '@/store/authStore';
import { UserRole } from '@/types/auth';

interface ProtectedRouteProps {
    /** Если true — пускает только ADMIN */
    adminOnly?: boolean;
}

/**
 * Обёртка для защищённых маршрутов.
 * - Пока загружается сессия — показывает спиннер
 * - Не авторизован — редирект на /login (с сохранением откуда пришёл)
 * - adminOnly + роль не ADMIN — редирект на главную
 * - Всё ок — рендерит дочерние маршруты через <Outlet />
 */
const ProtectedRoute = ({ adminOnly = false }: ProtectedRouteProps) => {
    const { isAuthenticated, isLoading, user } = useAuthStore();
    const location = useLocation();

    // Пока восстанавливаем сессию — показываем спиннер по центру экрана
    if (isLoading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <Spin size="large" />
            </div>
        );
    }

    // Не авторизован — на логин, запоминаем куда хотел попасть (state.from)
    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    // Маршрут только для админов, а пользователь не админ
    if (adminOnly && user?.role !== UserRole.ADMIN) {
        return <Navigate to="/" replace />;
    }

    // Всё ок — рендерим вложенные маршруты
    return <Outlet />;
};

export default ProtectedRoute;