import { useSearchParams, useNavigate } from 'react-router-dom';

const OAuth2ErrorPage = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const reason = searchParams.get('reason');

    const message =
        reason === 'phone_required'
            ? 'Для регистрации через Яндекс нужен доступ к номеру телефона. Попробуйте снова и не снимайте галочку доступа к телефону на экране Яндекса.'
            : 'Не удалось выполнить вход через Яндекс. Попробуйте ещё раз.';

    return (
        <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', padding: 24, textAlign: 'center' }}>
            <div style={{ maxWidth: 440 }}>
                <h1 style={{ fontFamily: 'var(--font-head)', fontSize: 'var(--text-3xl)', fontWeight: 600, color: 'var(--ink-1)', marginBottom: 12 }}>
                    Регистрация не завершена
                </h1>
                <p style={{ fontSize: 'var(--text-md)', color: 'var(--ink-3)', lineHeight: 1.6, marginBottom: 24 }}>
                    {message}
                </p>
                <button
                    onClick={() => navigate('/register', { replace: true })}
                    style={{ height: 'var(--btn-h-lg)', padding: '0 24px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
                >
                    Вернуться к регистрации
                </button>
            </div>
        </div>
    );
};

export default OAuth2ErrorPage;
