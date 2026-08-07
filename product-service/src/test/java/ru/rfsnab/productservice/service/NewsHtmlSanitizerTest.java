package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NewsHtmlSanitizer — очистка HTML новости")
class NewsHtmlSanitizerTest {

    private final NewsHtmlSanitizer sanitizer = new NewsHtmlSanitizer();

    @Test
    @DisplayName("<script> вырезается вместе с содержимым")
    void removesScriptTagWithContent() {
        String result = sanitizer.sanitize("<p>До</p><script>alert('xss')</script><p>После</p>");

        assertThat(result).doesNotContain("script", "alert");
        assertThat(result).contains("До", "После");
    }

    @Test
    @DisplayName("незакрытый <script src=...> тоже вырезается")
    void removesUnclosedScriptTag() {
        String result = sanitizer.sanitize("<p>Текст</p><script src=\"http://evil.com/x.js\">");

        assertThat(result).doesNotContain("script", "evil.com");
        assertThat(result).contains("Текст");
    }

    @ParameterizedTest
    @ValueSource(strings = {"style", "iframe", "object", "embed", "form", "svg"})
    @DisplayName("опасные блоки удаляются целиком")
    void removesDangerousBlocks(String tag) {
        String result = sanitizer.sanitize("<p>ок</p><" + tag + ">начинка</" + tag + ">");

        assertThat(result).doesNotContain(tag, "начинка");
        assertThat(result).contains("ок");
    }

    @Test
    @DisplayName("onerror и прочие on*-обработчики срезаются с атрибутами")
    void removesEventHandlers() {
        String result = sanitizer.sanitize("<img src=\"/a.png\" onerror=\"alert(1)\">");

        assertThat(result).doesNotContain("onerror", "alert");
        assertThat(result).contains("src=\"/a.png\"");
    }

    @Test
    @DisplayName("onclick срезается и у ссылки")
    void removesOnClickFromLink() {
        String result = sanitizer.sanitize("<a href=\"/catalog\" onclick=\"steal()\">Каталог</a>");

        assertThat(result).doesNotContain("onclick", "steal");
        assertThat(result).contains("href=\"/catalog\"", "Каталог");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "JavaScript:alert(1)", "vbscript:msgbox", "data:text/html;base64,PHNjcmlwdD4="})
    @DisplayName("ссылки с исполняемой схемой теряют href")
    void dropsUnsafeHrefSchemes(String url) {
        String result = sanitizer.sanitize("<a href=\"" + url + "\">Клик</a>");

        assertThat(result).doesNotContain("javascript", "vbscript", "data:");
        // Текст ссылки сохраняется, сама ссылка становится нерабочей
        assertThat(result).contains("Клик");
    }

    @Test
    @DisplayName("разрешённое форматирование сохраняется")
    void keepsAllowedFormatting() {
        String html = "<h2>Заголовок</h2><p><strong>Жирный</strong> и <em>курсив</em></p>"
                + "<ul><li>Пункт</li></ul><blockquote>Цитата</blockquote>";

        String result = sanitizer.sanitize(html);

        assertThat(result).contains("<h2>", "<strong>", "<em>", "<ul>", "<li>", "<blockquote>");
        assertThat(result).contains("Заголовок", "Жирный", "курсив", "Пункт", "Цитата");
    }

    @Test
    @DisplayName("обычная ссылка сохраняется и получает rel=noopener")
    void keepsSafeLinkAndAddsNoopener() {
        String result = sanitizer.sanitize("<a href=\"https://rfsnab.ru/catalog\">Каталог</a>");

        assertThat(result).contains("href=\"https://rfsnab.ru/catalog\"");
        assertThat(result).contains("rel=\"noopener noreferrer\"");
        assertThat(result).contains("target=\"_blank\"");
    }

    @Test
    @DisplayName("картинка из S3 сохраняется")
    void keepsImage() {
        String url = "https://storage.yandexcloud.net/ecommerce-products/news/abc.jpg";

        String result = sanitizer.sanitize("<img src=\"" + url + "\">");

        assertThat(result).contains("src=\"" + url + "\"");
    }

    @Test
    @DisplayName("неизвестный тег разворачивается, текст внутри остаётся")
    void unwrapsUnknownTagKeepingText() {
        String result = sanitizer.sanitize("<marquee>Бегущая строка</marquee>");

        assertThat(result).doesNotContain("marquee");
        assertThat(result).contains("Бегущая строка");
    }

    @Test
    @DisplayName("посторонние атрибуты у разрешённых тегов отбрасываются")
    void dropsForeignAttributes() {
        String result = sanitizer.sanitize("<p class=\"evil\" style=\"position:fixed\">Текст</p>");

        assertThat(result).doesNotContain("class", "style", "evil");
        assertThat(result).contains("<p>", "Текст");
    }

    @Test
    @DisplayName("null и пустая строка дают пустой результат")
    void handlesNullAndBlank() {
        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize("")).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEmpty();
    }
}
