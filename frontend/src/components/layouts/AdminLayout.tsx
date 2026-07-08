import { Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/store/authStore';
import { NavLink } from '@/components/navigation';

const NAV_ITEMS = [
  { key: '/admin',             label: 'Сводка',        icon: GridIcon },
  { key: '/admin/orders',      label: 'Заказы',         icon: DocIcon },
  { key: '/admin/products',    label: 'Каталог',        icon: MenuIcon },
  { key: '/admin/slider',      label: 'Слайдер',        icon: SliderIcon },
  { key: '/admin/users',       label: 'Клиенты',        icon: PersonIcon },
  { key: '/admin/logistics',   label: 'Логистика',      icon: TruckIcon },
  { key: '/admin/integration', label: 'Интеграция 1С',  icon: SyncIcon },
  { key: '/admin/settings',    label: 'Настройки',      icon: SettingsIcon },
];

const PAGE_TITLES: Record<string, string> = {
  '/admin':             'Сводка',
  '/admin/orders':      'Заказы',
  '/admin/products':    'Каталог',
  '/admin/slider':      'Слайдер',
  '/admin/users':           'Клиенты',
  '/admin/legal-entities':  'Юридическое лицо',
  '/admin/logistics':   'Логистика',
  '/admin/integration': 'Интеграция 1С',
  '/admin/settings':    'Настройки',
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

function TruckIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <path d="M1 3h9v8H1zM10 5h2.5L14 7.5V11h-4V5z" strokeLinejoin="round"/>
      <circle cx="4" cy="12.5" r="1.5"/><circle cx="12" cy="12.5" r="1.5"/>
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

function SliderIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <rect x="1" y="3" width="14" height="10" rx="1.5"/>
      <path d="M5 8h6M7 5l-2 3 2 3"/>
    </svg>
  );
}

function SettingsIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
      <circle cx="8" cy="8" r="2.5"/>
      <path d="M8 1v2M8 13v2M1 8h2M13 8h2M3.05 3.05l1.42 1.42M11.53 11.53l1.42 1.42M3.05 12.95l1.42-1.42M11.53 4.47l1.42-1.42"/>
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

const AdminLayout = () => {
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
        <NavLink
          to="/admin"
          className="rf-admin-logo"
          style={{ display: 'flex', alignItems: 'center', gap: 8 }}
        >
          <img src="/logo-dark.png" alt="РФснаб" style={{ height: 'var(--logo-h-admin)' }} />
          <span style={{ fontFamily: 'var(--font-head)', fontWeight: 700, fontSize: 15, color: 'var(--ink-1)', letterSpacing: '-0.01em' }}>
            РФснаб
          </span>
        </NavLink>

        <nav className="rf-admin-nav">
          <div className="rf-admin-nav-label">Управление</div>
          {NAV_ITEMS.map(({ key, label, icon: Icon }) => (
            <NavLink
              key={key}
              to={key}
              className={`rf-admin-nav-item${activeKey === key ? ' active' : ''}`}
            >
              <Icon />
              <span style={{ flex: 1 }}>{label}</span>
            </NavLink>
          ))}
        </nav>

        <NavLink
          to="/profile"
          className="rf-admin-user"
          title="Личный кабинет"
        >
          <div className="rf-admin-avatar">{initials}</div>
          <div style={{ flex: 1, fontSize: 12 }}>
            <div style={{ fontWeight: 600, color: 'var(--ink-1)' }}>{displayName}</div>
            <div style={{ color: 'var(--ink-3)' }}>Администратор</div>
          </div>
          <ChevronDownIcon />
        </NavLink>
      </aside>

      {/* Main */}
      <div className="rf-admin-main">
        <div className="rf-admin-topbar">
          <h1>{pageTitle}</h1>
          <span className="rf-admin-date-chip">{today}</span>
          <div style={{ flex: 1 }} />
          <NavLink
            to="/"
            className="rf-btn rf-btn-quiet rf-btn-sm"
            style={{ gap: 6 }}
          >
            ← В магазин
          </NavLink>
        </div>

        <div className="rf-admin-content">
          <Outlet />
        </div>
      </div>
    </div>
  );
};

export default AdminLayout;
