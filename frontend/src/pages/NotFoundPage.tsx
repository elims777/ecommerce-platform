import { NavLink } from '@/components/navigation';

const NotFoundPage = () => {
    return (
        <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <div style={{ fontFamily: 'var(--font-head)', fontSize: 20, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>Страница не найдена</div>
            <NavLink
                to="/"
                style={{ display: 'inline-flex', alignItems: 'center', height: 'var(--btn-h-base)', padding: '0 16px', background: 'var(--brand-red)', color: '#fff', border: 'none', borderRadius: 'var(--r-3)', fontSize: 'var(--text-md)', fontWeight: 500, cursor: 'pointer', fontFamily: 'var(--font-body)' }}
            >
                Вернуться на главную
            </NavLink>
        </div>
    );
};

export default NotFoundPage;
