package ru.rfsnab.productservice.mapper;

import ru.rfsnab.productservice.dto.NewsAdminResponse;
import ru.rfsnab.productservice.dto.NewsRequest;
import ru.rfsnab.productservice.dto.NewsResponse;
import ru.rfsnab.productservice.model.News;

/** Маппинг новостей вручную: сущность наружу не отдаём, MapStruct в этом модуле не используем. */
public final class NewsMapper {

    private NewsMapper() {}

    /**
     * Ответ для публичной части.
     *
     * @param videoEmbedUrl URL плеера от VideoEmbedResolver, null — площадка неизвестна
     */
    public static NewsResponse mapToResponse(News news, String videoEmbedUrl) {
        return NewsResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .slug(news.getSlug())
                .coverImageUrl(news.getCoverImageUrl())
                .videoUrl(news.getVideoUrl())
                .videoEmbedUrl(videoEmbedUrl)
                .contentHtml(news.getContentHtml())
                .publishedAt(news.getPublishedAt())
                .build();
    }

    public static NewsAdminResponse mapToAdminResponse(News news) {
        return NewsAdminResponse.builder()
                .id(news.getId())
                .title(news.getTitle())
                .slug(news.getSlug())
                .coverImageUrl(news.getCoverImageUrl())
                .videoUrl(news.getVideoUrl())
                .contentHtml(news.getContentHtml())
                .isPublished(news.getIsPublished())
                .publishedAt(news.getPublishedAt())
                .createdAt(news.getCreatedAt())
                .updatedAt(news.getUpdatedAt())
                .build();
    }

    /**
     * Переносит поля запроса в сущность. Slug, даты и санитайз HTML — забота сервиса.
     */
    public static void applyRequest(News news, NewsRequest request, String sanitizedHtml) {
        news.setTitle(request.getTitle());
        news.setCoverImageUrl(request.getCoverImageUrl());
        news.setVideoUrl(request.getVideoUrl());
        news.setContentHtml(sanitizedHtml);
    }
}