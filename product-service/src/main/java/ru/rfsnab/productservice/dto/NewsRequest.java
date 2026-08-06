package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Создание и редактирование новости из админки. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsRequest {

    @NotBlank(message = "Заголовок новости обязателен")
    @Size(min = 3, max = 255, message = "Заголовок должен быть от 3 до 255 символов")
    private String title;

    @Size(max = 1000, message = "Ссылка на обложку не должна превышать 1000 символов")
    private String coverImageUrl;

    @Size(max = 1000, message = "Ссылка на видео не должна превышать 1000 символов")
    private String videoUrl;

    @NotBlank(message = "Текст новости обязателен")
    private String contentHtml;

    private Boolean isPublished;
}