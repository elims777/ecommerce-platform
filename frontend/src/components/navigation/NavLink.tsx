import { Link } from 'react-router-dom';
import type { LinkProps } from 'react-router-dom';
import type { CSSProperties, ReactNode } from 'react';

/**
 * NavLink — единая навигационная ссылка для инлайн-кейсов: пункты меню,
 * кнопки «Войти/Регистрация», логотипы, breadcrumbs, back-кнопки на URL.
 *
 * Зачем: middle-click и Ctrl+Click открывают новую вкладку только у настоящих
 * <a href>. Связка onClick + navigate() ломает это. NavLink — обёртка над
 * <Link>, поэтому браузер видит ссылку и поведение «открыть в новой вкладке»
 * работает из коробки.
 *
 * Когда использовать другое:
 * - <ClickableCard> — если кликабельна целая карточка (товар, тайл категории).
 * - <RowLink>       — если кликабельна строка таблицы (вкладывается в <td>).
 * - <NavLink>       — всё остальное (текст-ссылка, кнопка-ссылка, иконка-ссылка).
 *
 * variant:
 *   'inline'           — текст-ссылка, наследует цвет/декорацию (default).
 *   'button-primary'   — основная кнопка (brand-red), белый текст.
 *   'button-secondary' — вторичная кнопка (поверхность + рамка).
 *   'icon'             — обёртка под иконку (только сброс декорации).
 *
 * Побочные эффекты в onClick: можно передавать, но НЕ вызывай navigate()
 * внутри — браузер уже перейдёт по href.
 */
type NavLinkVariant = 'inline' | 'button-primary' | 'button-secondary' | 'icon';

interface NavLinkProps extends Omit<LinkProps, 'style'> {
    children?: ReactNode;
    style?: CSSProperties;
    variant?: NavLinkVariant;
}

const baseInline: CSSProperties = {
    color: 'inherit',
    textDecoration: 'none',
    cursor: 'pointer',
};

const baseButton: CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
    height: 'var(--btn-h-base)',
    padding: '0 16px',
    borderRadius: 'var(--r-3)',
    fontSize: 'var(--text-md)',
    fontWeight: 500,
    fontFamily: 'var(--font-body)',
    textDecoration: 'none',
    cursor: 'pointer',
    transition: 'background 0.12s, opacity 0.12s',
    whiteSpace: 'nowrap',
};

const variantStyles: Record<NavLinkVariant, CSSProperties> = {
    inline: baseInline,
    icon: { ...baseInline, display: 'inline-flex', alignItems: 'center' },
    'button-primary': {
        ...baseButton,
        background: 'var(--brand-red)',
        color: '#fff',
        border: 'none',
    },
    'button-secondary': {
        ...baseButton,
        background: 'var(--surface)',
        color: 'var(--ink-1)',
        border: '1px solid var(--line-2)',
    },
};

const NavLink = ({ children, style, variant = 'inline', ...linkProps }: NavLinkProps) => (
    <Link {...linkProps} style={{ ...variantStyles[variant], ...style }}>
        {children}
    </Link>
);

export default NavLink;
