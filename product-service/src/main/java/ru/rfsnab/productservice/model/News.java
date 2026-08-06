package ru.rfsnab.productservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "news")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    /** Обложка в Yandex Object Storage. Null — новость без картинки. */
    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

    /** Ссылка на видео стороннего ресурса (VK/YouTube/RuTube). Сам файл в S3 не храним. */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    /** HTML из редактора. Санитайзится при сохранении. */
    @Column(name = "content_html", nullable = false, columnDefinition = "TEXT")
    private String contentHtml;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    /** Проставляется в момент первой публикации, при снятии с публикации не сбрасывается. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}