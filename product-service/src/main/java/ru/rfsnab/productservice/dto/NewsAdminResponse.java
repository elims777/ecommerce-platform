package ru.rfsnab.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Новость для админки: включает черновики и служебные даты. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsAdminResponse {

    private Long id;
    private String title;
    private String slug;
    private String coverImageUrl;
    private String videoUrl;
    private String contentHtml;
    private Boolean isPublished;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}