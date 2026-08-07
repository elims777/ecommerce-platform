import DOMPurify from 'dompurify';
import { useMemo } from 'react';

interface Props {
    html: string;
}

/**
 * Вывод текста новости. HTML чистится DOMPurify перед вставкой — это второй слой защиты
 * (первый на бэкенде, при сохранении). Полагаться только на бэкенд нельзя: данные могли
 * попасть в БД в обход контроллера.
 */
const NewsHtml = ({ html }: Props) => {
    const clean = useMemo(() => DOMPurify.sanitize(html, {
        ALLOWED_TAGS: [
            'p', 'br', 'strong', 'b', 'em', 'i', 'u', 's',
            'h2', 'h3', 'h4', 'ul', 'ol', 'li', 'blockquote',
            'a', 'img', 'code', 'pre',
        ],
        ALLOWED_ATTR: ['href', 'src', 'alt', 'target', 'rel'],
    }), [html]);

    return <div className="rf-news-html" dangerouslySetInnerHTML={{ __html: clean }} />;
};

export default NewsHtml;
