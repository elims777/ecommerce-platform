package ru.rfsnab.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Новость для публичной части сайта. Черновики сюда не попадают. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsResponse {

    private Long id;
    private String title;
    private String slug;
    private String coverImageUrl;

    /** Исходная ссылка — на случай, если площадка неизвестна и эмбед невозможен. */
    private String videoUrl;

    /** URL плеера для iframe. Null — встраивать нечего, показываем обычную ссылку. */
    private String videoEmbedUrl;

    private String contentHtml;
    private LocalDateTime publishedAt;
}