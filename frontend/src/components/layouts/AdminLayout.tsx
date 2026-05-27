import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';

const NAV_ITEMS = [
  { key: '/admin',            label: 'Сводка',        icon: GridIcon },
  { key: '/admin/orders',     label: 'Заявки',         icon: DocIcon },
  { key: '/admin/products',   label: 'Каталог',        icon: MenuIcon },
  { key: '/admin/users',      label: 'Клиенты',        icon: PersonIcon },
  { key: '/admin/integration',label: 'Интеграция 1С',  icon: SyncIcon },
];

const PAGE_TITLES: Record<string, string> = {
  '/admin':             'Сводка',
  '/admin/orders':      'Заявки и заказы',
  '/admin/products':    'Каталог',
  '/admin/users':       'Клиенты',
  '/admin/integration': 'Интеграция 1С',
};

function GridIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <rect x="1" y="1" width="6" height="6" rx="1"/><rect x="9" y="1" width="6" height="6" rx="1"/>
      <rect x="1" y="9" width="6" height="6" rx="1"/><rect x="9" y="9" width="6" height="6" rx="1"/>
    </svg>
  );
}

function DocIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M3 2h7l3 3v9a1 1 0 01-1 1H3a1 1 0 01-1-1V3a1 1 0 011-1z"/>
      <path d="M10 2v3h3M5 7h6M5 10h6M5 13h4"/>
    </svg>
  );
}

function MenuIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M2 4h12M2 8h12M2 12h12"/>
    </svg>
  );
}

function PersonIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="8" cy="5" r="3"/><path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6"/>
    </svg>
  );
}

function SyncIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M2 8a6 6 0 0110.5-4M14 8a6 6 0 01-10.5 4"/>
      <path d="M12 3l1 2-2 .5M4 13l-1-2 2-.5"/>
    </svg>
  );
}

function ChevronDownIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M3 5l4 4 4-4"/>
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="6" cy="6" r="4"/><path d="M10 10l2.5 2.5"/>
    </svg>
  );
}

const AdminLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const user = useAuthStore((s) => s.user);

  const activeKey = NAV_ITEMS.slice()
    .reverse()
    .find((item) => location.pathname.startsWith(item.key))?.key ?? '/admin';

  const pageTitle = Object.entries(PAGE_TITLES)
    .reverse()
    .find(([path]) => location.pathname.startsWith(path))?.[1] ?? 'Кабинет';

  const today = new Date().toLocaleDateString('ru-RU', {
    day: 'numeric', month: 'long', year: 'numeric', weekday: 'short',
  });

  const initials = user
    ? `${(user.firstname?.[0] ?? '')}${(user.lastname?.[0] ?? '')}`.toUpperCase()
    : 'АД';

  const displayName = user
    ? `${user.firstname ?? ''} ${user.lastname ?? ''}`.trim() || user.email
    : 'Администратор';

  return (
    <div className="rf-admin-layout">
      {/* Sidebar */}
      <aside className="rf-admin-sidebar">
        <div className="rf-admin-logo">
          <img
            src="/logo-dark.png"
            alt="РФснаб"
            style={{ height: 28, cursor: 'pointer' }}
            onClick={() => navigate('/admin')}
          />
        </div>

        <nav className="rf-admin-nav">
          <div className="rf-admin-nav-label">Управление</div>
          {NAV_ITEMS.map(({ key, label, icon: Icon }) => (
            <div
              key={key}
              className={`rf-admin-nav-item${activeKey === key ? ' active' : ''}`}
              onClick={() => navigate(key)}
            >
              <Icon />
              <span style={{ flex: 1 }}>{label}</span>
            </div>
          ))}
        </nav>

        <div className="rf-admin-user">
          <div className="rf-admin-avatar">{initials}</div>
          <div style={{ flex: 1, fontSize: 12 }}>
            <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{displayName}</div>
            <div style={{ color: 'var(--ink-3)' }}>Администратор</div>
          </div>
          <ChevronDownIcon />
        </div>
      </aside>

      {/* Main */}
      <div className="rf-admin-main">
        <div className="rf-admin-topbar">
          <h1>{pageTitle}</h1>
          <span className="rf-admin-date-chip">{today}</span>
          <div style={{ flex: 1 }} />
          <div className="rf-admin-search">
            <SearchIcon />
            <input placeholder="Поиск по заявкам, артикулам, клиентам…" />
          </div>
          <button
            className="rf-btn rf-btn-quiet rf-btn-sm"
            onClick={() => navigate('/')}
            style={{ gap: 6 }}
          >
            ← В магазин
          </button>
          <button className="rf-btn rf-btn-primary rf-btn-sm">
            + Новая заявка
          </button>
        </div>

        <div className="rf-admin-content">
          <Outlet />
        </div>
      </div>
    </div>
  );
};

export default AdminLayout;
