import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuthStore } from '@/store/authStore';
import type { AuthTokens } from '@/types/auth';

const OAuth2SuccessPage = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();

    useEffect(() => {
        const dataParam = searchParams.get('data');
        if (!dataParam) {
            navigate('/login', { replace: true });
            return;
        }

        let tokens: AuthTokens;
        try {
            tokens = JSON.parse(decodeURIComponent(dataParam));
        } catch {
            navigate('/login', { replace: true });
            return;
        }

        if (!tokens.access_token || !tokens.refresh_token || !tokens.user) {
            navigate('/login', { replace: true });
            return;
        }

        localStorage.setItem('accessToken', tokens.access_token);
        localStorage.setItem('refreshToken', tokens.refresh_token);
        useAuthStore.setState({ user: tokens.user, isAuthenticated: true, isLoading: false });

        navigate('/profile', { replace: true });
    }, []);

    return (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
            <Spin size="large" tip="Выполняется вход..." />
        </div>
    );
};

export default OAuth2SuccessPage;
