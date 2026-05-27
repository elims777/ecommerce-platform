import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuthStore } from '@/store/authStore';
import { isAdmin } from '@/types/auth';

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

    if (isLoading) {
        return (
            <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <Spin size="large" />
            </div>
        );
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" state={{ from: location }} replace />;
    }

    if (adminOnly && user && !isAdmin(user)) {
        return <Navigate to="/" replace />;
    }

    return <Outlet />;
};

export default ProtectedRoute;