import { Link } from 'react-router-dom';
import type { LinkProps } from 'react-router-dom';
import type { CSSProperties, ReactNode } from 'react';

/**
 * RowLink — ссылка для использования внутри <td> кликабельных строк таблицы.
 *
 * Зачем: HTML-стандарт запрещает <a> оборачивать <tr>/<td>. Решение: вкладываем
 * <Link> c display:block в каждую "информационную" ячейку строки. Браузер
 * видит настоящую ссылку → middle-click и Ctrl+Click открывают новую вкладку.
 *
 * Паттерн применения:
 *   1. Убираем onClick + navigate с <tr>.
 *   2. В каждой <td> с "пассивным" контентом (текст, дата, бейджи) оборачиваем
 *      содержимое в <RowLink to={...}>.
 *   3. В <td> с интерактивными элементами (Select, button) НЕ используем RowLink.
 *
 * Пример:
 *   <tr>
 *     <td><RowLink to="/admin/orders/1">ORD-0001</RowLink></td>
 *     <td><RowLink to="/admin/orders/1">{formatDate(o.createdAt)}</RowLink></td>
 *     <td>
 *       <Select ... />   ← интерактивные элементы просто не оборачиваем
 *     </td>
 *   </tr>
 *
 * Пропсы:
 *   to          — путь (как у <Link>)
 *   cellPadding — должен совпадать с padding ячейки таблицы, чтобы кликабельная
 *                 область совпадала с ячейкой целиком. По умолчанию '14px' —
 *                 соответствует .rf-admin-table td { padding: 14px }.
 *                 Передавай явное значение, если таблица использует другой padding.
 *   children    — содержимое ячейки
 *   style       — дополнительные инлайн-стили. ВНИМАНИЕ: в отличие от
 *                 ClickableCard, передаваемый style МОЖЕТ переопределить
 *                 color и textDecoration — это намеренно, чтобы можно было
 *                 задать акцентный цвет конкретной ячейке.
 *   остальные   — все пропсы <Link>
 */
interface RowLinkProps extends Omit<LinkProps, 'style'> {
    children?: ReactNode;
    style?: CSSProperties;
    cellPadding?: string;
}

const RowLink = ({ children, style, cellPadding, ...linkProps }: RowLinkProps) => {
    const padding = cellPadding ?? '14px';
    const baseStyle: CSSProperties = {
        display: 'block',
        color: 'inherit',
        textDecoration: 'none',
        // растягиваем на всю ячейку: margin компенсирует padding td, padding восстанавливает отступ
        margin: `-${padding}`,
        padding,
    };
    return (
        <Link
            {...linkProps}
            style={{ ...baseStyle, ...style }}
        >
            {children}
        </Link>
    );
};

export default RowLink;
