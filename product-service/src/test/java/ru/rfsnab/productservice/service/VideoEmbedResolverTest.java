package ru.rfsnab.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VideoEmbedResolver — ссылка на видео в URL плеера")
class VideoEmbedResolverTest {

    private final VideoEmbedResolver resolver = new VideoEmbedResolver();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ",
            "https://www.youtube.com/watch?list=PL123&v=dQw4w9WgXcQ"
    })
    @DisplayName("YouTube во всех формах ссылки")
    void resolvesYouTube(String url) {
        assertThat(resolver.resolveEmbedUrl(url))
                .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ");
    }

    @Test
    @DisplayName("VK: ссылка вида vk.com/video-123_456")
    void resolvesVkVideo() {
        assertThat(resolver.resolveEmbedUrl("https://vk.com/video-22822305_456241864"))
                .isEqualTo("https://vk.com/video_ext.php?oid=-22822305&id=456241864");
    }

    @Test
    @DisplayName("VK: домен vkvideo.ru")
    void resolvesVkVideoDomain() {
        assertThat(resolver.resolveEmbedUrl("https://vkvideo.ru/video-22822305_456241864"))
                .isEqualTo("https://vk.com/video_ext.php?oid=-22822305&id=456241864");
    }

    @Test
    @DisplayName("VK: клип на vkvideo.ru играется тем же плеером")
    void resolvesVkClip() {
        assertThat(resolver.resolveEmbedUrl("https://vkvideo.ru/clip-231565734_456241450"))
                .isEqualTo("https://vk.com/video_ext.php?oid=-231565734&id=456241450");
    }

    @Test
    @DisplayName("VK: уже готовая embed-ссылка")
    void resolvesVkEmbedLink() {
        assertThat(resolver.resolveEmbedUrl("https://vk.com/video_ext.php?oid=-123&id=456"))
                .isEqualTo("https://vk.com/video_ext.php?oid=-123&id=456");
    }

    @Test
    @DisplayName("RuTube")
    void resolvesRutube() {
        String id = "c2e2d5f0f1a34b6c9d8e7f6a5b4c3d2e";

        assertThat(resolver.resolveEmbedUrl("https://rutube.ru/video/" + id + "/"))
                .isEqualTo("https://rutube.ru/play/embed/" + id);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/video/123",
            "https://vimeo.com/123456",
            "не ссылка вовсе",
            "javascript:alert(1)"
    })
    @DisplayName("неизвестная площадка → null, встраивать нельзя")
    void returnsNullForUnknownSource(String url) {
        assertThat(resolver.resolveEmbedUrl(url)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("пустое значение → null")
    void returnsNullForBlank(String url) {
        assertThat(resolver.resolveEmbedUrl(url)).isNull();
    }
}
