import { Link } from 'react-router-dom';
import type { LinkProps } from 'react-router-dom';
import type { CSSProperties } from 'react';

/**
 * ClickableCard — обёртка-ссылка для карточек товаров и похожих блоков.
 *
 * Зачем: нативный <Link> делает всю карточку семантической ссылкой, поэтому
 * middle-click и Ctrl+Click открывают детальную страницу в новой вкладке,
 * а обычный клик переходит в текущей вкладке.
 *
 * Вложенные кнопки: ставь e.stopPropagation() на onClick вложенных button/a —
 * это прерывает всплытие события до Link и предотвращает навигацию карточки.
 *
 * Пример:
 *   <ClickableCard to={`/products/${id}`} className="my-card">
 *     <img ... />
 *     <button onClick={(e) => { e.stopPropagation(); handleCart(); }}>В корзину</button>
 *   </ClickableCard>
 *
 * Пропсы:
 *   to         — путь (как у <Link>)
 *   className  — CSS-класс на корневом элементе
 *   style      — инлайн-стили (дополняют базовые; цвет/декорация не перезаписываются)
 *   children   — содержимое карточки
 *   остальные  — все пропсы <Link> (state, replace, reloadDocument и т.д.)
 */
interface ClickableCardProps extends Omit<LinkProps, 'style'> {
    className?: string;
    style?: CSSProperties;
}

const ClickableCard = ({ children, className, style, ...linkProps }: ClickableCardProps) => (
    <Link
        {...linkProps}
        className={className}
        // style идёт первым — потребитель может задать display/layout, но
        // color и textDecoration защищены от переопределения
        style={{ ...style, color: 'inherit', textDecoration: 'none', display: style?.display ?? 'block' }}
    >
        {children}
    </Link>
);

export default ClickableCard;
