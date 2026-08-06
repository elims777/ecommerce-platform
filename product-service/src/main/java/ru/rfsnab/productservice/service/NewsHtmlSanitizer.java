package ru.rfsnab.productservice.service;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Вырезает из HTML новости всё, что не входит в белый список тегов и атрибутов.
 * <p>
 * Первый из двух слоёв защиты: здесь чистим перед записью в БД, на фронте — DOMPurify
 * перед выводом. Одного слоя мало: в БД данные могут попасть в обход контроллера.
 */
@Service
public class NewsHtmlSanitizer {

    /** Теги, которые создаёт редактор. Остальные удаляются, их текстовое содержимое сохраняется. */
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "p", "br", "strong", "b", "em", "i", "u", "s",
            "h2", "h3", "h4", "ul", "ol", "li", "blockquote",
            "a", "img", "code", "pre"
    );

    /** Теги, содержимое которых опасно само по себе — удаляем целиком вместе с внутренностями. */
    private static final Pattern DANGEROUS_BLOCKS = Pattern.compile(
            "(?is)<\\s*(script|style|iframe|object|embed|form|svg|math)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>"
    );

    /** Незакрытые опасные теги (например <script src=...> без пары). */
    private static final Pattern DANGEROUS_SELF = Pattern.compile(
            "(?is)<\\s*/?\\s*(script|style|iframe|object|embed|form|svg|math)\\b[^>]*>"
    );

    private static final Pattern TAG = Pattern.compile("(?is)<\\s*(/?)\\s*([a-z0-9]+)([^>]*)>");

    /** on*-обработчики: onclick, onerror и прочие. */
    private static final Pattern EVENT_ATTR = Pattern.compile("(?is)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    private static final Pattern HREF = Pattern.compile("(?is)\\shref\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern SRC = Pattern.compile("(?is)\\ssrc\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    /** Схемы, исполняющие код при клике или загрузке. */
    private static final Pattern UNSAFE_URL = Pattern.compile("(?is)^\\s*(javascript|vbscript|data|file)\\s*:");

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String result = DANGEROUS_BLOCKS.matcher(html).replaceAll("");
        result = DANGEROUS_SELF.matcher(result).replaceAll("");
        result = EVENT_ATTR.matcher(result).replaceAll("");

        return filterTags(result);
    }

    /** Оставляет только разрешённые теги; у остальных убирает саму разметку, сохраняя текст. */
    private String filterTags(String html) {
        Matcher matcher = TAG.matcher(html);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String closing = matcher.group(1);
            String tag = matcher.group(2).toLowerCase();
            String attrs = matcher.group(3);

            String replacement = ALLOWED_TAGS.contains(tag)
                    ? "<" + closing + tag + cleanAttributes(tag, attrs) + ">"
                    : "";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** У ссылок и картинок оставляем только безопасный URL, остальные атрибуты отбрасываем. */
    private String cleanAttributes(String tag, String attrs) {
        if (attrs == null || attrs.isBlank()) {
            return "";
        }
        if ("a".equals(tag)) {
            String href = extractUrl(HREF.matcher(attrs));
            // noopener: без него открытая вкладка получает доступ к window.opener
            return href == null ? "" : " href=\"" + href + "\" target=\"_blank\" rel=\"noopener noreferrer\"";
        }
        if ("img".equals(tag)) {
            String src = extractUrl(SRC.matcher(attrs));
            return src == null ? "" : " src=\"" + src + "\"";
        }
        return "";
    }

    private String extractUrl(Matcher matcher) {
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group(2) != null ? matcher.group(2)
                : matcher.group(3) != null ? matcher.group(3)
                : matcher.group(4);

        if (url == null || url.isBlank() || UNSAFE_URL.matcher(url).find()) {
            return null;
        }
        return url.replace("\"", "&quot;").trim();
    }
}
