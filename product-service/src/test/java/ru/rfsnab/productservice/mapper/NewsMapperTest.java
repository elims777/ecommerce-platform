package ru.rfsnab.productservice.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.rfsnab.productservice.dto.NewsAdminResponse;
import ru.rfsnab.productservice.dto.NewsRequest;
import ru.rfsnab.productservice.dto.NewsResponse;
import ru.rfsnab.productservice.model.News;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NewsMapper — маппинг новостей")
class NewsMapperTest {

    private static News news() {
        return News.builder()
                .id(1L)
                .title("Поступление спецодежды")
                .slug("postuplenie-specodezhdy")
                .coverImageUrl("https://storage.yandexcloud.net/ecommerce-products/news/cover.jpg")
                .videoUrl("https://youtu.be/dQw4w9WgXcQ")
                .contentHtml("<p>Текст новости</p>")
                .isPublished(true)
                .publishedAt(LocalDateTime.of(2026, 8, 6, 12, 0))
                .createdAt(LocalDateTime.of(2026, 8, 5, 10, 0))
                .updatedAt(LocalDateTime.of(2026, 8, 6, 11, 0))
                .build();
    }

    @Test
    @DisplayName("публичный ответ содержит embed-URL и не содержит служебных дат")
    void mapsToPublicResponse() {
        String embed = "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ";

        NewsResponse result = NewsMapper.mapToResponse(news(), embed);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Поступление спецодежды");
        assertThat(result.getSlug()).isEqualTo("postuplenie-specodezhdy");
        assertThat(result.getCoverImageUrl()).contains("cover.jpg");
        assertThat(result.getVideoUrl()).isEqualTo("https://youtu.be/dQw4w9WgXcQ");
        assertThat(result.getVideoEmbedUrl()).isEqualTo(embed);
        assertThat(result.getContentHtml()).isEqualTo("<p>Текст новости</p>");
        assertThat(result.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 12, 0));
    }

    @Test
    @DisplayName("embed=null допустим — площадка неизвестна")
    void mapsToPublicResponseWithoutEmbed() {
        NewsResponse result = NewsMapper.mapToResponse(news(), null);

        assertThat(result.getVideoEmbedUrl()).isNull();
        assertThat(result.getVideoUrl()).isNotNull();
    }

    @Test
    @DisplayName("админский ответ содержит статус и обе служебные даты")
    void mapsToAdminResponse() {
        NewsAdminResponse result = NewsMapper.mapToAdminResponse(news());

        assertThat(result.getIsPublished()).isTrue();
        assertThat(result.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
        assertThat(result.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 6, 11, 0));
        assertThat(result.getContentHtml()).isEqualTo("<p>Текст новости</p>");
    }

    @Test
    @DisplayName("applyRequest переносит поля и подставляет очищенный HTML")
    void appliesRequestWithSanitizedHtml() {
        News target = News.builder().slug("ne-menyaetsya").build();
        NewsRequest request = NewsRequest.builder()
                .title("Новый заголовок")
                .coverImageUrl("https://storage.yandexcloud.net/x/new.jpg")
                .videoUrl("https://vk.com/video-1_2")
                .contentHtml("<p>Грязный<script>alert(1)</script></p>")
                .build();

        NewsMapper.applyRequest(target, request, "<p>Чистый</p>");

        assertThat(target.getTitle()).isEqualTo("Новый заголовок");
        assertThat(target.getCoverImageUrl()).isEqualTo("https://storage.yandexcloud.net/x/new.jpg");
        assertThat(target.getVideoUrl()).isEqualTo("https://vk.com/video-1_2");
        assertThat(target.getContentHtml()).isEqualTo("<p>Чистый</p>");
        // Slug — забота сервиса, маппер его не трогает
        assertThat(target.getSlug()).isEqualTo("ne-menyaetsya");
    }
}
