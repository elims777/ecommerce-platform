package ru.rfsnab.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVideoRequest {
    @NotBlank(message = "URL видео обязателен")
    @Size(max = 500, message = "URL не должен превышать 500 символов")
    private String videoUrl;

    @Size(max = 50, message = "Тип видео не должен превышать 50 символов")
    private String videoType;

    @Size(max = 50, message = "Провайдер не должен превышать 50 символов")
    private String provider;

    @Size(max = 500, message = "URL превью не должен превышать 500 символов")
    private String thumbnailUrl;

    private Integer durationSeconds;

    @Size(max = 255, message = "Название не должно превышать 255 символов")
    private String title;
}
