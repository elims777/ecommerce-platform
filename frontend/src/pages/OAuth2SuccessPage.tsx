import { useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuthStore } from '@/store/authStore';
import { exchangeOAuth2Code } from '@/api/auth';
import { queryClient } from '@/lib/queryClient';
import { useCartStore } from '@/store/cartStore';

const OAuth2SuccessPage = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    // код одноразовый — StrictMode не должен обменять его дважды
    const exchanged = useRef(false);

    useEffect(() => {
        const code = searchParams.get('code');
        if (!code) {
            void navigate('/login', { replace: true });
            return;
        }
        if (exchanged.current) return;
        exchanged.current = true;

        exchangeOAuth2Code(code)
            .then((tokens) => {
                if (!tokens.access_token || !tokens.refresh_token || !tokens.user) {
                    void navigate('/login', { replace: true });
                    return;
                }
                localStorage.setItem('accessToken', tokens.access_token);
                localStorage.setItem('refreshToken', tokens.refresh_token);
                localStorage.removeItem('clientType');
                localStorage.removeItem('userId');
                queryClient.clear();
                useCartStore.getState().resetCart();
                useAuthStore.setState({ user: tokens.user, isAuthenticated: true, isLoading: false });
                void navigate('/profile', { replace: true });
            })
            .catch(() => {
                void navigate('/login', { replace: true });
            });
    }, [searchParams, navigate]);

    return (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
            <Spin size="large" description="Выполняется вход..." />
        </div>
    );
};

export default OAuth2SuccessPage;
